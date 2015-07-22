package spark.job.rest.server.domain.actors

import akka.actor.{Actor, ActorLogging}
import com.typesafe.config.Config
import spark.job.rest.api.types.ID
import spark.job.rest.config.durations.Durations

import scala.concurrent.ExecutionContext.Implicits.global
import scala.sys.process.{Process, ProcessBuilder, ProcessLogger}

/**
 * Actor responsible for execution of context process.
 * During pre-start it schedules execution of the process configured by passed process builder.
 * @param contextName context name corresponding to a process
 * @param contextId context ID
 * @param jars jars for spark-submit
 * @param config application config which specifies durations
 */
class ContextProcessActor(contextName: String, contextId: ID, gatewayPath: String, jars: String, val config: Config) extends Actor with ActorLogging with Durations {

  class Slf4jProcessLogger extends ProcessLogger {
    def out(line: => String): Unit = log.info(line)
    def err(line: => String): Unit = log.error(line)
    def buffer[T](f: => T): T = f
  }

  var process: Process = _

  /**
   * Starts process and schedules process watch. After process ends it notifies parent process with
   * [[ContextProviderActor.ProcessStopped]] or [[ContextProviderActor.ProcessFailed]] regarding to process exit status.
   */
  override def preStart(): Unit = {
    val processBuilder = createProcessBuilder(contextName, contextId, jars, config)
    process = processBuilder.run(new Slf4jProcessLogger)

    log.info(s"Context $contextName:$contextId process started: $processBuilder")

    context.system.scheduler.scheduleOnce(durations.context.waitBeforeWatch) {
      // Blocks execution until process exits
      val statusCode = process.exitValue()

      // Process
      if (statusCode < 0) {
        log.error(s"Context $contextName exit with error code $statusCode.")
        context.parent ! ContextProviderActor.ProcessFailed(statusCode)

      } else {
        log.info(s"Context process exit with status $statusCode")
        context.parent ! ContextProviderActor.ProcessStopped(statusCode)
      }

      context.system.stop(self)
    }
  }

  override def postStop(): Unit = {
    Option(process) foreach (_.destroy())
    log.info(s"Process actor for $contextName:$contextId stopped.")
  }

  def receive: Receive = {
    case msg =>
      log.warning(s"Context process actor is not designed to process messages but $msg received.")
  }

  /**
   * Creates process builder for context process.
   * @param contextName context name
   * @param contextId contxt ID
   * @param jars jars as a string
   * @param config merged context config
   * @return
   */
  def createProcessBuilder(contextName: String, contextId: ID, jars: String, config: Config): ProcessBuilder = {
    import spark.job.rest.config.{Configured, ContextProcessConfig, MasterNetworkConfig}

    val processConfig = new Configured(config) with ContextProcessConfig with MasterNetworkConfig

    val scriptPath = processConfig.contextsStartScript.getCanonicalPath
    val processDirName = processConfig.contextProcessDir(contextName).getCanonicalPath
    val masterHost = processConfig.masterHost
    val masterPort = processConfig.akkaMasterPort.toString
    val driverMemory = processConfig.driverMemory
    val sparkMaster = processConfig.sparkMaster

    Process(scriptPath, Seq(jars, contextName, contextId.toString, sparkMaster, driverMemory, processDirName, masterHost, masterPort, gatewayPath))
  }
}
