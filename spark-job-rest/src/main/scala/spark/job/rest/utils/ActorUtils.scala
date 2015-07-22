package spark.job.rest.utils

import java.net.ServerSocket

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import spark.job.rest.config.durations.Durations
import spark.job.rest.config.{ConfigDependentCompanion, Configured}
import spark.job.rest.server.domain.actors.messages.{Initialized, IsInitialized}

import scala.annotation.tailrec
import scala.concurrent.{Await, TimeoutException}
import scala.util.{Failure, Success, Try}


trait ActorUtils extends Durations {

  private val logger = LoggerFactory.getLogger(getClass)

  private lazy val askTimeout = durations.init.timeout
  private lazy val reTries = durations.init.tries

  lazy val contextActorPrefix = config.getString("spark.job.rest.context.actor.prefix")
  lazy val contextSystemPrefix = config.getString("spark.job.rest.context.actor.system.prefix")

  /**
   * Blocks until actor will be initialized
   * @param actor actor reference
   * @param timeout timeout for each ask attempt
   * @param tries how many attempt should
   */
  @tailrec
  final def awaitActorInitialization(actor: ActorRef, timeout: Timeout = askTimeout, tries: Int = reTries): Unit = tries match {
    case 0 =>
      throw new RuntimeException(s"Refused to wait for actor $actor initialization.")
    case _ =>
      implicit val askTimeout = timeout
      val future = actor ? IsInitialized
      // Await for future
      Try { Await.result(future, timeout.duration) } match {
        // When actor is initialized it responds with Initialized message
        case Success(Initialized) =>
          logger.info(s"Actor $actor initialized.")
        // Messages other than Initialized are not allowed
        case Success(msg) =>
          throw new IllegalArgumentException(s"Message $msg is not a valid response during initialisation. Subject: $actor")
        // Or we will fail due to timeout
        case Failure(e: TimeoutException) =>
          logger.info(s"Actor $actor is not responding. Retrying.")
          awaitActorInitialization(actor, timeout, tries - 1)
        // Rethrow all other exceptions
        case Failure(e: Throwable) =>
          logger.error("Error during actor initialization", e)
          throw e
      }
  }
}


object ActorUtils extends ConfigDependentCompanion[ActorUtils] {
  
  override def configDependentInstance(config: Config): ActorUtils =
    new Configured(config) with ActorUtils

  /**
   * Helper method which calculates actor address
   * @param systemName name of the actor system
   * @param host actor system host
   * @param port actor system port
   * @param actorPath actor path without address
   * @return
   */
  def getActorAddress(systemName: String, host: String, port: Int, actorPath: String): String = {
    s"akka.tcp://$systemName@$host:$port$actorPath"
  }

  /**
   * Finds available port looking up from given port number
   * @param port port to start search from
   * @return available port
   */
  @tailrec
  def findAvailablePort(port: Int): Integer = {
    Try {
      new ServerSocket(port).close()
    } match {
      case Success(_) => port
      case _ => findAvailablePort(port + 1)
    }
  }
}
