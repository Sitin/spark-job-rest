package spark.job.rest.server.domain.actors

import akka.actor._
import com.typesafe.config.Config
import spark.job.rest.api.entities.{ContextDetails, ContextState, Jars}
import spark.job.rest.api.responses.Context
import spark.job.rest.api.types._
import spark.job.rest.config.durations.Durations
import spark.job.rest.persistence.services.ContextPersistenceService
import spark.job.rest.persistence.slickWrapper.Driver.api._
import spark.job.rest.server.domain.actors.ContextApplicationActor.{ContextStartFailed, StartContext}
import spark.job.rest.utils.JarUtils

import scala.concurrent.Promise
import scala.util.Success

/**
 * Base data trait for context supervisor.
 * In each state context supervisor has a process actor.
 */
sealed trait ContextSupervisorData

/**
 * Trait for data for states after successful start
 */
sealed trait SuccessfulStart {
  val processActor: ActorRef
  val contextDetails: ContextDetails
}

/**
 * Trait for data which contains context application referenceю
 */
sealed trait WhenApplicationStarted {
  val contextApp: ActorRef
}

/**
 * Data which corresponds to failed process start
 * @param e throwable error
 */
case class ContextFailed(e: Throwable) extends ContextSupervisorData

/**
 * Data corresponded to initial [[Starting]] state when process is just started.
 * @param contextDetails context details
 * @param processActor actor responsible for the process
 */
case class StartedProcess(contextDetails: ContextDetails, processActor: ActorRef) extends ContextSupervisorData with SuccessfulStart

/**
 * Data that corresponds to [[Initialising]] and [[StartingContext]] states when remote application
 * negotiates with master for configuration.
 * @param contextDetails context details
 * @param processActor actor responsible for the process
 * @param contextApp remote context application actor 
 */
case class RegisteredContextApplication(contextDetails: ContextDetails, processActor: ActorRef, contextApp: ActorRef) 
  extends ContextSupervisorData with SuccessfulStart with WhenApplicationStarted

/**
 * Data which correspond to running remote context in [[Running]].
 * @param contextDetails context details
 * @param processActor actor responsible for the process
 * @param contextApp remote context application actor 
 * @param jobs set of currently running jobs
 */
case class RunningContext(contextDetails: ContextDetails, processActor: ActorRef, contextApp: ActorRef, jobs: Set[ID]) 
  extends ContextSupervisorData with SuccessfulStart with WhenApplicationStarted

sealed trait ContextSupervisorState
case object Starting extends ContextSupervisorState
case object Initialising extends ContextSupervisorState
case object StartingContext extends ContextSupervisorState
case object Running extends ContextSupervisorState
case object Stopping extends ContextSupervisorState
case object Failed extends ContextSupervisorState

object ContextSupervisorActor {
  // Registering application to master
  case class RegisterContextApplication(contextApp: ActorRef)
  // Initialising application
  case class ContextApplicationInitialized(sparkUiPor: Int)
  case class ContextApplicationInitFailed(message: String)
  // Starting context
  case class JobContextStarted(finalConfig: Config)
  case class JobContextStartFailed(message: String)
  // Process termination
  case class ProcessStopped(statusCode: Int)
  case class ProcessFailed(statusCode: Int)
  // Context creation promise
  case object GetContextCreationPromise
  case class ContextCreationPromise(promise: Promise[ActorRef])
  // Context info
  case object GetContextInfo
  // Context termination
  case object StopContext
  // Errors
  case class RestartFailure(reason: Throwable)
}

/**
 * Context contextApp responsible for single context creation.
 * It starts context processActor via [[ContextProcessActor]] and initializes remote [[ContextApplicationActor]].
 */
class ContextSupervisorActor(contextName: String,
                             jars: Jars,
                             submittedConfig: Config,
                             defaultConfig: Config,
                             db: Database,
                             connectionProvider: ActorRef)
  extends FSM[ContextSupervisorState, ContextSupervisorData] with JarUtils with ContextPersistenceService with Durations {
  // Internal imports
  import ContextApplicationActor.Initialize
  import ContextSupervisorActor._

  // Assert that context supervisor has right path ending.
  assert(
    self.path.toStringWithoutAddress.endsWith(s"-$contextName"),
    s"Address for context supervisor should end with context name but '${self.path.toStringWithoutAddress}' is given."
  )

  /**
   * Config for config-dependent traits
   */
  val config = submittedConfig.withFallback(defaultConfig)

  /**
   * This promise tracks job context creation.
   */
  val contextCreated = Promise[ActorRef]()

  /**
   * During startup we create context process actor and persist initial context state.
   */
  override def preStart(): Unit = {
    try {
      // Persist initial context state
      val contextDetails = insertContext(ContextDetails(contextName, submittedConfig, None, jars), db)

      // Start processActor actor
      val processActor = context.actorOf(Props(new ContextProcessActor(contextName, contextDetails.id, getJarsPathForClasspath(jars, contextName), config)))
      context.watch(processActor)

      // Configure FSM and initialize it
      startWith(Starting, StartedProcess(contextDetails, processActor), Some(durations.context.wakeupTimeout))

      log.info(s"Context supervisor started: $self")
    } catch {
      case e: Throwable =>
        log.error(s"Context $contextName supervisor failed to start.", e)
        contextCreated.failure(e)
        startWith(Failed, ContextFailed(e))
    }

    initialize()
  }

  /**
   * We do not support restart of the context actor.
   * @param reason restart reason
   */
  override def postRestart(reason: Throwable): Unit = {
    log.error("Context Supervisor restart is not supported", reason)
    stop(FSM.Failure(reason)) replying RestartFailure(reason)
  }

  /**
   * In [[Starting]] state [[ContextSupervisorActor]] wait while for remote processActor will start and registers back it's
   * [[ContextApplicationActor]]. After that [[ContextSupervisorActor]] sends initialisation message and transits to
   * [[Initialising]] state saving reference to context application actor in [[RegisteredContextApplication]].
   * All cases of processActor terminations are considered as failures.
   */
  when(Starting) {
    case Event(RegisterContextApplication(contextApp), StartedProcess(contextDetails, processActor)) =>
      val contextId = contextDetails.id
      log.info(s"Context application for $contextName:$contextId is registered from $contextApp.")

      // Persist context start and create data for the next state
      val newContextDetails = updateContextState(contextId, ContextState.Started, db, "Remote context application started.")
      val newData = RegisteredContextApplication(newContextDetails, processActor, contextApp)

      // Send init info to context application
      contextApp ! Initialize(contextName, contextId, connectionProvider, config, getJarsPathForSpark(contextDetails.jars))

      goto(Initialising) using newData forMax durations.context.initialisationTimeout
  }

  /**
   * At [[Initialising]] state [[ContextSupervisorActor]] waits until remote context application actor finishes it's
   * initialisation and will be about to start context. Once [[ContextApplicationInitialized]] received we
   * switching to [[StartingContext]] state with updated context details in [[RegisteredContextApplication]].
   * All cases of processActor terminations are considered as failures.
   */
  when(Initialising) {
    case Event(ContextApplicationInitialized(sparkUiPort), data: RegisteredContextApplication) =>
      val contextId = data.contextDetails.id
      log.info(s"Context application $contextName:$contextId is initialized and is about to start the context.")

      // Persist context initialisation and obtain data for new state
      val newData = data.copy(contextDetails = persistContextInitialisation(contextId, sparkUiPort, db))

      // Send start command
      data.contextApp ! StartContext

      // Switch to starting state
      goto(StartingContext) using newData forMax durations.context.startTimeout
  }

  /**
   * At [[StartingContext]] state we just waiting for context to start and switching to [[Running]] after that.
   * All cases of processActor terminations are considered as failures.
   */
  when(StartingContext) {
    case Event(JobContextStarted(finalConfig), RegisteredContextApplication(contextDetails, processActor, contextApp)) =>
      val contextId = contextDetails.id
      log.info(s"Context $contextName:$contextId successfully started.")
      contextCreated.complete(Success(self))
      goto(Running) using RunningContext(persistContextCreation(contextId, finalConfig, db), processActor, contextApp, Set())
    case Event(ContextStartFailed(e), RegisteredContextApplication(contextDetails, processActor, contextApp)) =>
      val contextId = contextDetails.id
      log.error(s"Context start for $contextName:$contextId failed", e)
      updateContextState(contextId, ContextState.Failed, db, s"Context start failed due to: ${e.getMessage}")
      stop(FSM.Failure(e))
  }

  /**
   * During [[Running]] state we accepting job requests to the context storing running job in a [[RunningContext]].
   */
  when(Running) {
    case Event(runJob: JobActor.RunJob, data: RunningContext) =>
      stay() using data.copy(jobs = data.jobs + runJob.id)
    case Event(GetContextInfo, data: RunningContext) =>
      sender() ! Context(contextName, data.contextDetails.id, data.contextDetails.state, data.contextDetails.sparkUiPort)
      stay() using data
  }

  /**
   * At the [[Stopping]] state we await for context process to stop and shutting down the actor.
   * After [[ProcessStopped]] or [[ProcessFailed]] received actor will stop replying only with
   * [[ContextCreationPromise]].
   */
  when(Stopping) {
    case Event(ProcessStopped(statusCode), _) =>
      val message = s"Actor processActor exits with exit code $statusCode."
      log.error(message)
      if (!contextCreated.isCompleted) contextCreated.failure(new RuntimeException(message))
      stop() replying ContextCreationPromise(contextCreated)

    case Event(ProcessFailed(statusCode), _) =>
      val message = s"Actor processActor failed during shutdown with exit code $statusCode."
      log.error(message)
      if (!contextCreated.isCompleted) contextCreated.failure(new RuntimeException(message))
      stop(FSM.Failure(ProcessFailed(statusCode))) replying ContextCreationPromise(contextCreated)
  }

  /**
   * This state is for unrecoverable context errors.
   */
  when(Failed) {
    case Event(_, ContextFailed(e)) =>
      if (!contextCreated.isCompleted) contextCreated.failure(e)
      sender() ! ContextCreationPromise(contextCreated)
      stop(FSM.Failure(e)) replying ContextCreationPromise(contextCreated)
  }

  whenUnhandled {
    /**
     * Watch unexpected process stop
     */
    case Event(reason @ Terminated(actor), data: SuccessfulStart) if actor.equals(data.processActor) =>
      contextCreated.failure(new RuntimeException(s"Unexpected termination of context process actor."))
      stop(FSM.Failure(reason))

    /**
     * Watch unexpected context application stop
     */
    case Event(reason @ Terminated(actor), data: WhenApplicationStarted) if actor.equals(data.contextApp) =>
      contextCreated.failure(new RuntimeException(s"Unexpected termination of remote context application actor."))
      stop(FSM.Failure(reason))

    /**
     * In all cases we able to report context creation promise.
     */
    case Event(GetContextCreationPromise, _) =>
      sender() ! ContextCreationPromise(contextCreated)
      stay()

    /**
     * When stop message received we transit to final [[Stopping]] where we will finally await for process termination.
     */
    case Event(StopContext, data: SuccessfulStart with WhenApplicationStarted) =>
      updateContextState(data.contextDetails.id, ContextState.Terminated, db, "Stopped by user request.")
      data.contextApp ! ContextApplicationActor.ShutDown
      goto(Stopping) using data forMax durations.context.waitForTermination
  }
}
