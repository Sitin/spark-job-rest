package spark.job.rest.server.domain.actors

import akka.actor.{Actor, ActorRef, Props, Terminated}
import akka.pattern.ask
import com.typesafe.config.Config
import org.apache.commons.lang.exception.ExceptionUtils
import org.slf4j.LoggerFactory
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
import scala.util.Success

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
}

/**
 * Actor that creates, monitors and destroys contexts and corresponding processes.
 * @param config configuration defaults
 * @param jarActor actor that responsible for jars which may be included to context classpath
 */
class ContextManagerActor(val config: Config, jarActor: ActorRef, connectionProviderActor: ActorRef)
  extends Actor with ContextPersistenceService with DatabaseUtils with ActorUtils with MasterNetworkConfig with AskTimeout {

  val log = LoggerFactory.getLogger(getClass)

  val contextMap = new mutable.HashMap[String, ActorRef]() with mutable.SynchronizedMap[String, ActorRef]

  /**
   * Database connection received from connection provider [[DatabaseServerActor]]
   */
  var db: Database = _

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
      } else (for {
        // Create supervisor and obtain context creation promise
        ContextSupervisorActor.ContextCreationPromise(contextCreated) <- {
          val contextSupervisor = context.actorOf(Props(new ContextSupervisorActor(
            contextName,
            Jars.fromString(jars),
            submittedConfig,
            config,
            db,
            connectionProviderActor)
          ), name = s"ContextSupervisor-$contextName")

          contextMap += contextName -> contextSupervisor

          log.info(s"Creating supervisor $contextSupervisor created and registered.")

          contextSupervisor ? ContextSupervisorActor.GetContextCreationPromise
        }
        // Resolve context creation to actual context reference
        supervisor: ActorRef <- {
          log.info(s"Received context creation future for $contextName.")
          contextCreated.future
        }
        // Get context info
        contextInfo <- {
          log.info(s"Context supervisor $supervisor reported context creation for $contextName.")
          context.watch(supervisor)
          supervisor ? ContextSupervisorActor.GetContextInfo
        }
      } yield {
          log.info(s"Received info for created context: $contextInfo")
          webSender ! contextInfo
      }) onFailure {
        case e: Throwable =>
          log.error(s"Failed! ${ExceptionUtils.getStackTrace(e)}")
          self ! DeleteContext(contextName)
          webSender ! e
      }

    case DeleteContext(contextName) =>
      log.info(s"Received DeleteContext message : context=$contextName")
      if (contextMap contains contextName) {
        for (
          supervisorRef <- contextMap remove contextName
        ) {
          context.stop(supervisorRef)
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
        contextMap(contextName) forward ContextSupervisorActor.GetContextInfo
      } else {
        sender ! NoSuchContext
      }

    case GetAllContexts =>
      log.info(s"Received GetAllContexts message.")
      val webSender = sender()
      val arrayOfContextFutures: List[Future[Context]] = contextMap.values.toList map {
        case supervisor => (supervisor ? ContextSupervisorActor.GetContextInfo).mapTo[Context]
      }
      // Convert sequence of futures to future of sequence
      Future.sequence(arrayOfContextFutures) map {
        case contexts => webSender ! Contexts(contexts.toArray)
      }

    case Terminated(diedActor) =>
      val terminatedContext: Option[(String, ActorRef)] =
        contextMap.asParSeq find { case (contextName, actorRef) => actorRef.equals(diedActor) }

      terminatedContext match {
        case Some((contextName, supervisor)) =>
          log.warn(s"Context supervisor $supervisor for context $contextName actor died.")
          self ! DeleteContext(contextName)
        case None =>
          log.warn(s"Found unregistered dead actor $diedActor")
      }
  }
}

