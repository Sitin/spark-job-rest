package spark.job.rest.server

import akka.actor.{ActorSystem, Props}
import org.slf4j.LoggerFactory
import spark.job.rest.config.{ContextNetworkConfig, contextApplicationConfig}
import spark.job.rest.logging.LoggingOutputStream
import spark.job.rest.server.domain.actors.ContextActor
import spark.job.rest.utils.ActorUtils

/**
 * Spark context container entry point.
 */
object MainContext {

  LoggingOutputStream.redirectConsoleOutput()
  val log = LoggerFactory.getLogger(getClass)

  val config = contextApplicationConfig

  val akkaSystemConfig = ContextNetworkConfig.configDependentInstance(config).akkaSystemConfig

  val actorUtils = ActorUtils.configDependentInstance(config)

  def main(args: Array[String]) {
    val contextName = System.getenv("CONTEXT_NAME")
    val port = System.getenv("CONTEXT_PORT").toInt

    log.info(s"Started new process for contextName = $contextName with port = $port")

    // Use default config as a base
    val system = ActorSystem(actorUtils.contextSystemPrefix + contextName, akkaSystemConfig)

    system.actorOf(Props(new ContextActor("127.0.0.1", 4042, config)), actorUtils.contextActorPrefix + contextName)

    log.info(s"Initialized system ${actorUtils.contextSystemPrefix}$contextName and actor ${actorUtils.contextActorPrefix}$contextName")
  }
}
