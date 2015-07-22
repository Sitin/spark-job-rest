package spark.job.rest.server

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import spark.job.rest.api.types.idFromString
import spark.job.rest.config.{ContextNetworkConfig, contextApplicationConfig}
import spark.job.rest.logging.LoggingOutputStream
import spark.job.rest.server.domain.actors.ContextApplicationActor
import spark.job.rest.utils.ActorUtils

/**
 * Spark context container entry point.
 */
object MainContext extends ActorUtils with ContextNetworkConfig {

  LoggingOutputStream.redirectConsoleOutput()
  val log = LoggerFactory.getLogger(getClass)

  var config: Config = _

  def main(args: Array[String]) {
    val extraConfig = args(0)
    val contextName = args(1)
    val contextId = idFromString(args(2))
    val masterHost = args(3)
    val masterPort = args(4).toInt
    val gatewayPath = args(5)

    config = contextApplicationConfig(extraConfig)

    log.info(s"Started new process for contextName = $contextName with port = $masterPort")

    // Use default config as a base
    val system = ActorSystem(contextSystemPrefix + contextName, akkaSystemConfig)

    system.actorOf(Props(new ContextApplicationActor(contextName, contextId, masterHost, masterPort, gatewayPath, config)), contextActorPrefix + contextName)

    log.info(s"Initialized system $contextSystemPrefix$contextName and actor $contextActorPrefix$contextName")
  }
}
