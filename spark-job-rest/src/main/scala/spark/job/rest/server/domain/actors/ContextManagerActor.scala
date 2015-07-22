package spark.job.rest.server.domain.actors

import akka.actor.SupervisorStrategy._
import akka.actor._
import akka.pattern.ask
import com.typesafe.config.Config
import spark.job.rest.api.entities.Jars
import spark.job.rest.api.responses.{Context, Contexts}
import spark.job.rest.config.MasterNetworkConfig
import spark.job.rest.config.durations.AskTimeout
import spark.job.rest.persistence.services.ContextPersistenceService
import spark.job.rest.persistence.slickWrapper.Driver.api._
import spark.job.rest.server.domain.actors.ContextManagerActor._
import spark.job.rest.utils.{ActorUtils, DatabaseUtils}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


/**
 * Messages handled by [[ContextCreationSupervisor]]
 */
object ContextCreationSupervisor {
  case class ContextStarted(info: Context)
  case class ContextCreationError(reason: Throwable)
  case class ContextFailure(reason: Throwable)
}

/**
 * Lightweight proxy for handling context creation request by creating [[ContextProviderActor]] instance from provided
 * props and then handling messages from it.
 * @param contextName context name
 * @param jars jars for context
 * @param submittedConfig config submitted by client
 * @param configDefaults configuration defaults obtained from application config
 * @param db database connection
 * @param connectionProvider connection provider
 * @param questioner questioner (by design REST controller) interested in context creation
 */
class ContextCreationSupervisor(contextName: String,
                                jars: Jars,
                                submittedConfig: Config,
                                configDefaults: Config,
                                db: Database,
                                connectionProvider: ActorRef,
                                questioner: ActorRef) extends Actor with ActorLogging {
  import ContextCreationSupervisor._
  import ContextManagerActor._
  import ContextProviderActor._

  val contextProvider = Try {
    val provider = context.actorOf(Props(new ContextProviderActor(
      contextName,
      jars,
      submittedConfig,
      configDefaults,
      db,
      connectionProvider)), name = s"ContextProvider-$contextName")
    context.watch(provider)
    provider
  } match {
    case Success(result) => result
    case Failure(e: Throwable) =>
      questioner ! ContextCreationError(e)
      context.stop(self)
      null
  }
  
  override def preStart(): Unit = {
    contextProvider ! Go
  }

  def receive: Receive = {
    case ContextStarted(contextInfo: Context) =>
      questioner ! contextInfo
      context.parent ! RegisterContext(contextName, contextProvider)

    case msg @ ContextCreationError(reason: Throwable) =>
      log.error(s"Context $contextName start failed", reason)
      questioner ! msg
      context.stop(self)

    case Terminated(provider: ActorRef) if contextProvider.equals(provider) =>
      context.stop(self)

    case error =>
      log.error(s"Error during context $contextName creation: $error")
      questioner ! error
      context.stop(self)
  }
}

/**
 * Context management messages
 */
object ContextManagerActor {
  case class CreateContext(contextName: String, jars: String, config: Config)
  case class DeleteContext(contextName: String)
  case class GetContext(contextName: String)
  case class GetContextInfo(contextName: String)
  case object GetAllContexts
  case object NoSuchContext
  case object ContextAlreadyExists
  case object JarsPropertiesIsNotSet
  case class RegisterContext(contextName: String, contextProvider: ActorRef)
}

/**
 * Actor that creates, monitors and destroys contexts and corresponding processes.
 * @param config configuration defaults
 * @param jarActor actor that responsible for jars which may be included to context classpath
 */
class ContextManagerActor(val config: Config, jarActor: ActorRef, connectionProviderActor: ActorRef)
  extends Actor with ActorLogging with ContextPersistenceService with DatabaseUtils with ActorUtils with MasterNetworkConfig with AskTimeout {

  val contextMap = new mutable.HashMap[String, ActorRef]() with mutable.SynchronizedMap[String, ActorRef]

  /**
   * Database connection received from connection provider [[DatabaseServerActor]]
   */
  var db: Database = _

  /**
   * Synchronously obtain database connection
   */
  override def preStart() = {
    db = dbConnection(connectionProviderActor)
  }

  override def receive: Receive = {
    case request @ CreateContext(contextName, jars, submittedConfig) =>
      log.info(s"Received context creation request: $request")
      val webSender = sender()

      if (contextMap contains contextName) {
        webSender ! ContextAlreadyExists
      } else if (jars.isEmpty) {
        webSender ! JarsPropertiesIsNotSet
      } else try {
        // Create context creation supervisor to track
        context.actorOf(
          Props(new ContextCreationSupervisor(
            contextName,
            Jars.fromString(jars),
            submittedConfig,
            config,
            db,
            connectionProviderActor,
            webSender)),
          name = s"ContextCreationSupervisor")
      } catch {
        case e: Throwable =>
          log.error("Unrecoverable context creation error", e)
          webSender ! e
      }

    case RegisterContext(contextName, contextProvider) =>
      log.info(s"Register context '$contextName' provider.")
      contextMap += contextName -> contextProvider

    case DeleteContext(contextName) =>
      log.info(s"Received DeleteContext message : context=$contextName")
      if (contextMap contains contextName) {
        for (
          providerRef <- contextMap remove contextName
        ) {
          context.stop(providerRef)
          sender ! Success
        }
      } else {
        sender ! NoSuchContext
      }

    case GetContext(contextName) =>
      log.info(s"Received GetContext message : context=$contextName")
      if (contextMap contains contextName) {
        sender ! contextMap(contextName)
      } else {
        sender ! NoSuchContext
      }

    case GetContextInfo(contextName) =>
      log.info(s"Received GetContextInfo message : context=$contextName")
      if (contextMap contains contextName) {
        contextMap(contextName) forward ContextProviderActor.GetContextInfo
      } else {
        sender ! NoSuchContext
      }

    case GetAllContexts =>
      log.info(s"Received GetAllContexts message.")
      val webSender = sender()
      val arrayOfContextFutures: List[Future[Context]] = contextMap.values.toList map {
        case provider => (provider ? ContextProviderActor.GetContextInfo).mapTo[Context]
      }
      // Convert sequence of futures to future of sequence
      Future.sequence(arrayOfContextFutures) map {
        case contexts => webSender ! Contexts(contexts.toArray)
      }

    case Terminated(diedActor) =>
      val terminatedContext: Option[(String, ActorRef)] =
        contextMap.asParSeq find { case (contextName, actorRef) => actorRef.equals(diedActor) }

      terminatedContext match {
        case Some((contextName, provider)) =>
          log.warning(s"Context provider $provider for context $contextName actor died.")
          self ! DeleteContext(contextName)
        case None =>
          log.warning(s"Found unregistered dead actor $diedActor")
      }
  }

  /**
   * Restrict restarting of child actors.
   */
  override val supervisorStrategy = OneForOneStrategy() {
    case _: ActorInitializationException => Stop
    case _: ActorKilledException         => Stop
    case _: DeathPactException           => Stop
    case _: Throwable                    => Stop
  }
}

