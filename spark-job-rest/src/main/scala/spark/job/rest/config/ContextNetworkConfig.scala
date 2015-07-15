package spark.job.rest.config

import com.typesafe.config.{Config, ConfigFactory}
import spark.job.rest.utils.ActorUtils.findAvailablePort

/**
 * Config for context network
 */
trait ContextNetworkConfig extends ConfigDependent {
  import ContextNetworkConfig._

  /**
   * Provides available context akka port which is calculated only once among all instances of the trait.
   */
  lazy val akkaContextPort: Int = (_akkaContextPort match {
    case None =>
      _akkaContextPort = Some(findAvailablePort(config.getInt("spark.job.rest.context.actor.systems.first.port")))
      _akkaContextPort
    case _ => _akkaContextPort
  }).get

  /**
   * Provides available Spark UI port which is calculated only once among all instances of the trait.
   */
  lazy val sparkUiPort: Int = (_sparkUiPort match {
    case None =>
      _sparkUiPort = Some(findAvailablePort(config.getInt("spark.job.rest.context.spark.ui.first.port")))
      _sparkUiPort
    case _ => _sparkUiPort
  }).get

  /**
   * Hostname for context
   */
  lazy val contextHost = config.getString("spark.job.rest.context.akka.remote.netty.tcp.hostname")

  lazy val akkaSystemConfig = ConfigFactory.parseString(
    s"""
      |akka.remote.netty.tcp {
      |  port = $akkaContextPort
      |}
    """.stripMargin)
    .withFallback(config.getConfig("spark.job.rest.context"))
}

object ContextNetworkConfig extends ConfigDependentCompanion[ContextNetworkConfig] {
  private var _akkaContextPort: Option[Int] = None
  private var _sparkUiPort: Option[Int] = None

  override def configDependentInstance(config: Config): ContextNetworkConfig =
    new Configured(config) with ContextNetworkConfig
}
