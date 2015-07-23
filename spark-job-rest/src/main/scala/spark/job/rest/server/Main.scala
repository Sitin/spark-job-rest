package spark.job.rest.server

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import com.typesafe.config.Config
import spark.job.rest.config.durations.AskTimeout
import spark.job.rest.config.{MasterNetworkConfig, masterApplicationConfig}
import spark.job.rest.logging.LoggingOutputStream
import spark.job.rest.server.domain.actors._
import spark.job.rest.utils.ActorUtils

import scala.concurrent.Await

/**
 * Spark-Job-REST entry point.
 */
object Main extends ActorUtils with MasterNetworkConfig with AskTimeout {
  LoggingOutputStream.redirectConsoleOutput()

  /**
   * Application config depends on program parameters
   */
  var config: Config = _

  def main(args: Array[String]) {
    // Use default config as a base
    config = masterApplicationConfig(args(0))

    // Create actor system
    val system = ActorSystem("ManagerSystem", masterAkkaSystemConfig)
    // Create supervisor for system actors
    val supervisor = system.actorOf(Props(classOf[Supervisor], config), "Supervisor")

    // Database server actor will instantiate database and ensures that schema is created
    val databaseServerActor = createActor(Props(new DatabaseServerActor(config)), "DatabaseServerActor", system, supervisor)
    // We should wait for this actor to be initialized before proceed
    awaitActorInitialization(databaseServerActor, durations.init.timeout, durations.init.tries)

    val jarActor = createActor(Props(new JarActor(config)), "JarActor", system, supervisor)
    val contextManagerActor = createActor(Props(new ContextManagerActor(config, databaseServerActor)), "ContextManager", system, supervisor)
    val jobManagerActor = createActor(Props(new JobActor(config, contextManagerActor, databaseServerActor)), "JobManager", system, supervisor)

    // HTTP server will start immediately after controller instantiation
    new Controller(config, contextManagerActor, jobManagerActor, jarActor, databaseServerActor, system)
  }

  def createActor(props: Props, name: String, customSystem: ActorSystem, supervisor: ActorRef): ActorRef = {
    val actorRefFuture = ask(supervisor, (props, name))
    Await.result(actorRefFuture, timeout.duration).asInstanceOf[ActorRef]
  }
}
