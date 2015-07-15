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
import scala.util.{Success, Try}


trait ActorUtils extends Durations {
  import ActorUtils._

  private val log = LoggerFactory.getLogger(getClass)

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
      try { Await.ready(future, timeout.duration) }
      // Ignore timeout
      catch { case _: TimeoutException => }
      // Return if actor initialized or retry
      future.value.getOrElse(None) match {
        case Success(Initialized) =>
        case _ =>
          log.info(s"Actor $actor is not responding. Retrying.")
          awaitActorInitialization(actor, timeout, tries - 1)
      }
  }

  @deprecated
  def getContextActorAddress(contextName: String, host: String, port: Int): String ={
    getActorAddress(contextSystemPrefix + contextName, host, port, contextActorPrefix + contextName)
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
   * @param actorName actor name
   * @return
   */
  def getActorAddress(systemName: String, host: String, port: Int, actorName: String): String = {
    "akka.tcp://"  + systemName + "@" + host + ":" + port + "/user/" + actorName
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
