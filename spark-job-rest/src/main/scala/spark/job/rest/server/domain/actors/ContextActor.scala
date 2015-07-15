package spark.job.rest.server.domain.actors

import akka.actor.{Actor, ActorRef, Props, Stash, Terminated}
import com.google.gson.{GsonBuilder, Gson}
import com.typesafe.config.{Config, ConfigValueFactory}
import org.apache.commons.lang.exception.ExceptionUtils
import org.slf4j.LoggerFactory
import spark.job.rest.api._
import spark.job.rest.api.entities.ContextState
import spark.job.rest.api.types.ID
import spark.job.rest.config.durations.AskTimeout
import spark.job.rest.context.JobContextFactory
import spark.job.rest.persistence.services.{ContextPersistenceService, JobPersistenceService}
import spark.job.rest.persistence.slickWrapper.Driver.api.Database
import spark.job.rest.server.domain.actors.ContextActor._
import spark.job.rest.server.domain.actors.JobActor._
import spark.job.rest.server.domain.actors.messages.IsAwake
import spark.job.rest.utils.{ActorUtils, DatabaseUtils}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * Context actor messages
 */
object ContextActor {
  case class Initialize(contextName: String, contextId: ID, connectionProviderActor: ActorRef, config: Config, jarsForSpark: List[String])
  case object Initialized
  case class FailedInit(message: String)
  case object ShutDown
}

/**
 * Context actor responsible for creation and managing Spark Context
 * @param localConfig config of the context application
 */
class ContextActor(masterHost: String, masterPort: Int, localConfig: Config) extends Actor
  with Stash with ContextPersistenceService with JobPersistenceService with DatabaseUtils with AskTimeout {
  import context.become

  val log = LoggerFactory.getLogger(getClass)

  /**
   * Spark job context which may be either directly [[org.apache.spark.SparkContext]]
   * or anything else (normally SQL context) based on top of it.
   */
  var jobContext: ContextLike = _

  /**
   * Config which will be used as a defaults for jobs running on this context.
   * Starting value is a local application config.
   */
  var config: Config = localConfig

  /**
   * Spark application name
   */
  var contextName: String = _

  /**
   * Persistent identifier of the context
   */
  var contextId: ID = _

  /**
   * Provider for database connection: [[DatabaseConnectionActor]]
   */
  var connectionProvider: ActorRef = _

  /**
   * Database connection
   */
  var db: Database = _

  /**
   * JSON serializer for job results
   */
  val gsonTransformer = new GsonBuilder().serializeSpecialFloatingPointValues().create()

  /**
   * Actor initialisation
   */
  override def preStart(): Unit = {
    startWatchingManagerActor()
  }

  /**
   * Context cleanup
   */
  override def postStop(): Unit = {
    updateContextState(contextId, ContextState.Terminated, db, "Context actor stopped")
  }

  /**
   * Initial actor mode when it responds to [[IsAwake]] message and can be initialized
   * @return
   */
  override def receive: Receive = {
    case IsAwake =>
      sender ! IsAwake

    case Initialize(name, id, remoteConnectionProvider, contextConfig, jarsForSpark) =>
      // Stash all messages to process them later
      stash()

      config = contextConfig.withFallback(config)
      contextName = name
      contextId = id

      log.info("Initializing context " + contextName)

      // Do all unsafe stuff
      try {
        // Request connection parameters and establish connection to database
        initDbConnection(remoteConnectionProvider)

        // Initialize context
        initContext(contextConfig, jarsForSpark)
        sender ! Initialized

        // Switch to initialised mode and process all messages
        become(initialized)
        unstashAll()
      } catch {
        case e: Throwable =>
          log.error("Exception while initializing", e)
          sender ! FailedInit(ExceptionUtils.getStackTrace(e))
          gracefullyShutdown()
      }
  }

  /**
   * Main actor mode when it can run jobs
   * @return
   */
  def initialized: Receive = {
    case ShutDown =>
      updateContextState(contextId, ContextState.Terminated, db, "Received shutdown request")
      log.info(s"Context received ShutDown message : contextName=$contextName")
      log.info(s"Shutting down SparkContext $contextName")

      gracefullyShutdown()

    case RunJob(runningClass, _, jobConfig, jobId) =>
      log.info(s"Received RunJob message : runningClass=$runningClass contextName=$contextName uuid=$jobId ")
      val contextManager = sender()

      val jobExecutionFuture = Future {
        Try {
          // Create job instance
          val sparkJob = try {
            val classLoader = Thread.currentThread.getContextClassLoader
            val runnableClass = classLoader.loadClass(runningClass)
            runnableClass.newInstance.asInstanceOf[SparkJobBase]
          } catch {
            // Job instantiation error is not fatal for the context
            case e: Throwable => throw new Exception("Job instantiation error", e)
          }

          jobContext.validateJob(sparkJob) match {
            case SparkJobValid => log.info(s"Job $jobId passed context validation.")
            case SparkJobInvalid(message) => throw new IllegalArgumentException(s"Invalid job $jobId: $message")
          }

          val jobConfigValidation = sparkJob.validate(jobContext.asInstanceOf[sparkJob.C], jobConfig.withFallback(config))
          jobConfigValidation match {
            case SparkJobInvalid(message) => throw new IllegalArgumentException(message)
            case SparkJobValid => log.info("Job config validation passed.")
          }

          val finalJobConfig = jobConfig.withFallback(config)
          persistJobStart(jobId, contextName, contextId, finalJobConfig, db)

          sparkJob.runJob(jobContext.asInstanceOf[sparkJob.C], finalJobConfig)
        }
      }
      try {
        jobExecutionFuture map {
          case Success(result) =>
            log.info(s"Finished running job : runningClass=$runningClass contextName=$contextName uuid=$jobId ")
            persistJobResult(jobId, gsonTransformer.toJson(result), db)
          case Failure(e: Throwable) =>
            log.error(s"Error running job : runningClass=$runningClass contextName=$contextName uuid=$jobId ", e)
            persistJobFailure(jobId, "Job error: " + ExceptionUtils.getStackTrace(e), db)
          case x: Any =>
            log.error("Received ANY from running job !!! " + x)
            persistJobFailure(jobId, "Received ANY from running job !!! " + x, db)
        } onFailure {
          case e: Throwable =>
            log.error(s"Error running job : runningClass=$runningClass contextName=$contextName uuid=$jobId ", e)
            persistJobFailure(jobId, "Job execution error: " + ExceptionUtils.getStackTrace(e), db)
        }
      } catch {
        case e: Throwable =>
          val errorReport = ExceptionUtils.getStackTrace(e)
          log.error(s"Error during processing job $jobId result at context $contextName : $contextId: $errorReport")
          contextManager ! ContextManagerActor.UnrecoverableContextError(e, contextName, contextId)
      }

    case Terminated(actor) =>
      if (actor.path.toString.contains("Supervisor/ContextManager")) {
        updateContextState(contextId, ContextState.Terminated, db, "Stopped due to ManagerSystem termination")
        log.info(s"Received Terminated message from: ${actor.path.toString}")
        log.warn("Shutting down the system because the ManagerSystem terminated.")
        gracefullyShutdown()
      }

    case _: Initialize =>
      log.info(s"Received redundant Initialize message")

    case x @ _ =>
      log.info(s"Received UNKNOWN message: $x")
  }

  /**
   * Initializes connection to database
   * @param remoteConnectionProvider reference to connection provider actor
   */
  def initDbConnection(remoteConnectionProvider: ActorRef): Unit = {
    connectionProvider = context.actorOf(Props(new DatabaseConnectionActor(remoteConnectionProvider, localConfig)))
    db = dbConnection(connectionProvider)
    log.info(s"Obtained connection to database: $db")
  }

  /**
   * Prepares config and initializes context
   * @param submittedConfig submitted context
   * @param jarsForSpark jars to be included
   * @throws Throwable anything that may happen during context creation
   */
  def initContext(submittedConfig: Config, jarsForSpark: List[String]): Unit = {
    config = submittedConfig.withValue("spark.job.rest.context.jars", ConfigValueFactory.fromAnyRef(jarsForSpark.asJava))
    jobContext = JobContextFactory.makeContext(config, contextName)
    persistContextCreation(contextId, config, db)
    log.info("Successfully initialized context " + contextName)
  }

  def gracefullyShutdown() {
    Option(jobContext).foreach(_.stop())
    context.system.shutdown()
  }

  /**
   * Connect to manager actor and watch it's state.
   */
  def startWatchingManagerActor() = {
    log.info(s"Trying to watch the manager actor at : $masterHost:$masterPort")
    val managerActor = context.actorSelection(ActorUtils.getActorAddress("ManagerSystem", masterHost, masterPort, "Supervisor/ContextManager"))
    managerActor.resolveOne().onComplete {
      case Success(actorRef) =>
        log.info(s"Now watching the ContextManager from this actor.")
        context.watch(actorRef)
      case x @ _ => log.info(s"Received message of type $x")
    }
  }
}


