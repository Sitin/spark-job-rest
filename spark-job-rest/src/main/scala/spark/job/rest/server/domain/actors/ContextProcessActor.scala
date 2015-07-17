package spark.job.rest.server.domain.actors

import akka.actor.Actor
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import spark.job.rest.api.types.ID
import spark.job.rest.config.durations.Durations

import scala.concurrent.ExecutionContext.Implicits.global
import scala.sys.process.{Process, ProcessBuilder, ProcessLogger}

object ContextProcessActor {
  case object Terminate
}

/**
 * Actor responsible for execution of context process.
 * During pre-start it schedules execution of the process configured by passed process builder.
 * @param contextName context name corresponding to a process
 * @param contextId context ID
 * @param jars jars for spark-submit
 * @param config application config which specifies durations
 */
class ContextProcessActor(contextName: String, contextId: ID, jars: String, val config: Config) extends Actor with Durations {
  import ContextProcessActor._

  val log = LoggerFactory.getLogger(s"$getClass::$contextName")

  class Slf4jProcessLogger extends ProcessLogger {
    def out(line: => String): Unit = log.info(line)
    def err(line: => String): Unit = log.error(line)
    def buffer[T](f: => T): T = f
  }

  val processBuilder = createProcessBuilder(contextName, contextId, jars, config)

  var process: Process = _

  /**
   * Schedules
   */
  override def preStart(): Unit = {
    process = processBuilder.run(new Slf4jProcessLogger)
    log.info(s"Context $contextName:$contextId process started: $processBuilder")

    context.system.scheduler.scheduleOnce(durations.context.waitBeforeWatch) {
      // Blocks execution until process exits
      val statusCode = process.exitValue()

      // Process
      if (statusCode < 0) {
        log.error(s"Context $contextName exit with error code $statusCode.")
        context.parent ! ContextSupervisorActor.ProcessFailed(statusCode)

      } else {
        log.info(s"Context process exit with status $statusCode")
        context.parent ! ContextSupervisorActor.ProcessStopped(statusCode)
      }

      context.system.stop(self)
    }
  }

  override def postStop(): Unit = {
    log.info(s"Process actor for $contextName:$contextId stopped.")
  }

  def receive: Receive = {
    case Terminate =>
      log.info(s"Received Terminate message")
      process.destroy()
      context.system.stop(self)
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

    Process(scriptPath, Seq(jars, contextName, contextId.toString, sparkMaster, driverMemory, processDirName, masterHost, masterPort))
  }
}
