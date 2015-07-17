package spark.job.rest.server

import akka.actor.{ActorSystem, Props}
import org.slf4j.LoggerFactory
import spark.job.rest.api.types.idFromString
import spark.job.rest.config.{ContextNetworkConfig, contextApplicationConfig}
import spark.job.rest.logging.LoggingOutputStream
import spark.job.rest.server.domain.actors.ContextApplicationActor
import spark.job.rest.utils.ActorUtils

/**
 * Spark context container entry point.
 */
object MainContext extends ActorUtils {

  LoggingOutputStream.redirectConsoleOutput()
  val log = LoggerFactory.getLogger(getClass)

  val config = contextApplicationConfig
  val akkaSystemConfig = ContextNetworkConfig.configDependentInstance(config).akkaSystemConfig

  def main(args: Array[String]) {
    val contextName = args(0)
    val contextId = idFromString(args(1))
    val masterHost = args(2)
    val masterPort = args(3).toInt

    log.info(s"Started new process for contextName = $contextName with port = $masterPort")

    // Use default config as a base
    val system = ActorSystem(contextSystemPrefix + contextName, akkaSystemConfig)

    system.actorOf(Props(new ContextApplicationActor(contextName, contextId, masterHost, masterPort, config)), contextActorPrefix + contextName)

    log.info(s"Initialized system $contextSystemPrefix$contextName and actor $contextActorPrefix$contextName")
  }
}
