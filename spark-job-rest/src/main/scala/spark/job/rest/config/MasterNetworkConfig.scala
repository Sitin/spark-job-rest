package spark.job.rest.config

import com.typesafe.config.{Config, ConfigFactory}
import spark.job.rest.utils.ActorUtils.findAvailablePort

/**
 * Config for context network
 */
trait MasterNetworkConfig extends ConfigDependent {
  import MasterNetworkConfig._

  /**
   * Provides available manager akka port which is calculated only once among all instances of the trait.
   */
  lazy val akkaMasterPort: Int = (_akkaMasterPort match {
    case None =>
      _akkaMasterPort = Some(findAvailablePort(config.getInt("spark.job.rest.master.actor.systems.first.port")))
      _akkaMasterPort
    case _ => _akkaMasterPort
  }).get

  /**
   * Hostname for master
   */
  lazy val masterHost = config.getString("spark.job.rest.master.akka.remote.netty.tcp.hostname")

  lazy val masterAkkaSystemConfig = ConfigFactory.parseString(
    s"""
      |akka.remote.netty.tcp {
      |  port = $akkaMasterPort
      |}
    """.stripMargin)
    .withFallback(config.getConfig("spark.job.rest.master"))
    .withOnlyPath("akka")
}

object MasterNetworkConfig extends ConfigDependentCompanion[MasterNetworkConfig] {
  private var _akkaMasterPort: Option[Int] = None

  override def configDependentInstance(config: Config): MasterNetworkConfig =
    new Configured(config) with MasterNetworkConfig
}
