package spark.job.rest.server.domain.actors

import akka.actor._
import com.typesafe.config.{Config, ConfigValueFactory}
import spark.job.rest.api.types.ID
import spark.job.rest.config.ContextNetworkConfig
import spark.job.rest.config.durations.AskTimeout
import spark.job.rest.persistence.slickWrapper.Driver.api.Database
import spark.job.rest.server.domain.actors.ContextSupervisorActor._
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
    val supervisor: ActorRef
  }
  case class Awaken(supervisor: ActorRef) extends Data
  case class Initialized(supervisor: ActorRef,
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
  case class ContextStartFailed(e: Throwable)
  case object ShutDown
}

/**
 * Context application actor responsible for application initialisation and
 * creating [[ContextActor]] which actually starts context.
 * @param contextName context name
 * @param contextId context ID
 * @param masterHost master application host
 * @param masterPort master application port
 * @param config local application config
 */
class ContextApplicationActor(contextName: String,
                              contextId: ID,
                              masterHost: String,
                              masterPort: Int,
                              val config: Config)
  extends FSM[states.State, data.Data]
  with DatabaseUtils with ContextNetworkConfig with AskTimeout {
  import ContextApplicationActor._
  
  /**
   * Supervisor selection points to [[ContextSupervisorActor]]
   */
  val supervisorSelection =
    context.actorSelection(ActorUtils.getActorAddress(
      "ManagerSystem",
      masterHost,
      masterPort,
      s"Supervisor/ContextManager/ContextSupervisor-$contextName"
    ))

  /**
   * Resolve supervisor, watch it setup FSM initial state
   */
  override def preStart(): Unit = {
    log.info(s"Trying to watch the manager actor $supervisorSelection at for $masterHost:$masterPort")

    // Resolve supervisor actor reference
    supervisorSelection.resolveOne() map {
      case supervisor =>
        log.info(s"Now watching the ContextSupervisor($supervisor) from context application actor.")
        context.watch(supervisor)
        
        // Setup initial FSM state and start
        startWith(states.Awaken, data.Awaken(supervisor))
        initialize()
        
        // Sent registration request to supervisor
        supervisor ! RegisterContextApplication(self)
    } onFailure {
      case e: Throwable =>
        log.error("Error watching supervisor.", e)
        context.system.shutdown()
    }
  }

  /**
   * In [[states.Awaken]] context application accepts initialisation message, initialises database connection,
   * creates [[ContextActor]] and then switches to [[states.Initialized]].
   */
  when(states.Awaken) {
    case Event(Initialize(name, id, remoteConnectionProvider, contextConfig, jarsForSpark), data.Awaken(supervisor)) =>
      log.info(s"Context application $contextName:$contextId received initialisation data.")

      val mergedConfig = contextConfig
        .withFallback(config)
        .withValue("spark.job.rest.context.jars", ConfigValueFactory.fromAnyRef(jarsForSpark.asJava))
        .withValue("spark.ui.port", ConfigValueFactory.fromAnyRef(sparkUiPort))
      val db = initDbConnection(remoteConnectionProvider)
      val jobContext = context.actorOf(Props(classOf[ContextActor], contextName, contextId, mergedConfig))

      supervisor ! ContextApplicationInitialized(sparkUiPort)
      goto(states.Initialized) using data.Initialized(supervisor, jobContext, mergedConfig, db)
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
    case Event(ContextStarted, current @ data.Initialized(supervisor, _, contextConfig, _)) =>
      log.info(s"Context $contextName:$contextId started.")
      supervisor ! JobContextStarted(contextConfig)
      goto(states.Running)
    case Event(errorMessage @ ContextStartFailed(e), data.Initialized(supervisor, _, _, _)) =>
      supervisor ! errorMessage
      stop(FSM.Failure(e)) replying errorMessage
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
    case Event(Terminated(actor), having: data.Data) if actor.equals(having.supervisor) =>
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


