package spark.job.rest.server.domain.actors

import akka.actor.{Actor, ActorLogging, Props}
import com.typesafe.config.Config
import spark.job.rest.api.types._
import spark.job.rest.config.MasterNetworkConfig
import spark.job.rest.config.durations.Durations

/**
 * Creates [[ContextApplicationActor]] locally.
 */
class LocalContextDispatcherActor(contextName: String,
                                  contextId: ID,
                                  gatewayPath: String,
                                  jars: String,
                                  val config: Config)
  extends Actor with ActorLogging with MasterNetworkConfig with Durations {

  /**
   * Create context application actor
   */
  val contextApplication = context.actorOf(Props(
    new ContextApplicationActor(
      contextName,
      contextId,
      masterHost,
      akkaMasterPort,
      gatewayPath,
      config,
      terminateOnStop = false  // Do not shut down Akka system when context application stopped!
    )
  ), s"LocalContextApplication-$contextName")

  override def postStop(): Unit = {
    log.info("Local context dispatcher stopped.")
  }

  def receive: Receive = {
    case msg =>
      log.warning(s"Local context dispatcher actor is not designed to process messages but $msg received.")
  }
}
