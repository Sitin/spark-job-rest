package spark.job.rest.server.domain.actors

import akka.actor._
import com.typesafe.config.{Config, ConfigValueFactory}
import spark.job.rest.api.types.ID
import spark.job.rest.config.ContextNetworkConfig
import spark.job.rest.config.durations.AskTimeout
import spark.job.rest.context.JobContextStartException
import spark.job.rest.persistence.slickWrapper.Driver.api.Database
import spark.job.rest.server.domain.actors.ContextProviderActor._
import spark.job.rest.server.domain.actors.JobActor._
import spark.job.rest.utils.{ActorUtils, DatabaseUtils}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * States for [[ContextApplicationActor]]
 */
object states {
  sealed trait State
  case object Awaken extends State
  case object Initialized extends State
  case object Starting extends State
  case object Running extends State
}

/**
 * States data for [[ContextApplicationActor]]
 */
object data {
  sealed trait Data {
    val provider: ActorRef
  }
  case class Awaken(provider: ActorRef) extends Data
  case class Initialized(provider: ActorRef,
                         jobContext: ActorRef,
                         contextConfig: Config,
                         db: Database) extends Data
}

/**
 * Context application messages
 */
object ContextApplicationActor {
  case class Initialize(contextName: String,
                        contextId: ID,
                        connectionProviderActor: ActorRef,
                        contextConfig: Config,
                        jarsForSpark: List[String])
  case object StartContext
  case object ContextStarted
  case object ShutDown
}

/**
 * Context application actor responsible for application initialisation and
 * creating [[ContextActor]] which actually starts context.
 * @param contextName context name
 * @param contextId context ID
 * @param masterHost master application host
 * @param masterPort master application port
 * @param gatewayPath path to context gateway for registering
 * @param config local application config
 */
class ContextApplicationActor(contextName: String,
                              contextId: ID,
                              masterHost: String,
                              masterPort: Int,
                              gatewayPath: String,
                              val config: Config)
  extends FSM[states.State, data.Data]
  with DatabaseUtils with ContextNetworkConfig with AskTimeout {
  import ContextApplicationActor._
  
  /**
   * Supervisor selection points to [[ContextProviderActor]]
   */
  val providerSelection =
    context.actorSelection(ActorUtils.getActorAddress(
      "ManagerSystem",
      masterHost,
      masterPort,
      gatewayPath
    ))

  /**
   * Resolve provider, watch it setup FSM initial state
   */
  override def preStart(): Unit = {
    log.info(s"Trying to watch the manager actor $providerSelection at for $masterHost:$masterPort")

    // Resolve provider actor reference
    providerSelection.resolveOne() map {
      case provider =>
        log.info(s"Now watching the ContextSupervisor($provider) from context application actor.")
        context.watch(provider)
        
        // Setup initial FSM state and start
        startWith(states.Awaken, data.Awaken(provider))
        initialize()
        
        // Sent registration request to provider
        provider ! RegisterContextApplication(self)
    } onFailure {
      case e: Throwable =>
        log.error("Error watching provider.", e)
        context.system.shutdown()
    }
  }

  /**
   * In [[states.Awaken]] context application accepts initialisation message, initialises database connection,
   * creates [[ContextActor]] and then switches to [[states.Initialized]].
   */
  when(states.Awaken) {
    case Event(Initialize(name, id, remoteConnectionProvider, contextConfig, jarsForSpark), data.Awaken(provider)) =>
      log.info(s"Context application $contextName:$contextId received initialisation data.")

      val mergedConfig = contextConfig
        .withFallback(config)
        .withValue("spark.job.rest.context.jars", ConfigValueFactory.fromAnyRef(jarsForSpark.asJava))
        .withValue("spark.ui.port", ConfigValueFactory.fromAnyRef(sparkUiPort))
      val db = initDbConnection(remoteConnectionProvider)
      val jobContext = context.actorOf(Props(classOf[ContextActor], contextName, contextId, mergedConfig))

      provider ! ContextApplicationInitialized(sparkUiPort)
      goto(states.Initialized) using data.Initialized(provider, jobContext, mergedConfig, db)
  }

  /**
   * When context application is in [[states.Initialized]] it only waits to [[StartContext]] request and passes it
   * forward to [[ContextActor]]. Then switches to [[states.Starting]] state.
   */
  when(states.Initialized) {
    case Event(StartContext, current @ data.Initialized(_, jobContext, _, _)) =>
      log.info(s"Context application $contextName:$contextId received context start request from ${sender()}.")
      jobContext ! StartContext
      goto(states.Starting) using current
  }

  /**
   * In [[states.Starting]] state context application waits [[ContextActor]] to start the context and moves to
   * [[states.Running]] once [[ContextStarted]] received.
   */
  when(states.Starting) {
    case Event(ContextStarted, current @ data.Initialized(provider, _, contextConfig, _)) =>
      log.info(s"Context $contextName:$contextId started.")
      provider ! JobContextStarted(contextConfig)
      goto(states.Running)
    case Event(error: JobContextStartException, data.Initialized(provider, _, _, _)) =>
      provider ! error
      stop(FSM.Failure(error)) replying error
  }

  /**
   * This is a main state for context application when it responds to [[RunJob]] requests.
   */
  when(states.Running) {
    case Event(ShutDown, having @ data.Initialized(_, jobContext, _, _)) =>
      log.info(s"Context application $contextName:$contextId received ShutDown message.")
      context.stop(jobContext)
      stop()
    case Event(job: RunJob, having @ data.Initialized(_, jobContext, _, _)) =>
      jobContext forward job
      stay()
  }

  whenUnhandled {
    case Event(Terminated(actor), having: data.Data) if actor.equals(having.provider) =>
      stop(FSM.Shutdown)
    case Event(Terminated(actor), data.Initialized(_, jobContext, _, _)) if actor.equals(jobContext) =>
      stop(FSM.Failure(s"Unexpected shutdown of context $contextName:$contextId."))
  }

  onTermination {
    case _ => context.system.shutdown()
  }

  /**
   * Initializes connection to database
   * @param remoteConnectionProvider reference to connection provider actor
   */
  def initDbConnection(remoteConnectionProvider: ActorRef): Database = {
    val connectionProvider = context.actorOf(Props(new DatabaseConnectionActor(remoteConnectionProvider, config)))
    val db = dbConnection(connectionProvider)
    log.info(s"Obtained connection to database: $db")
    db
  }
}


