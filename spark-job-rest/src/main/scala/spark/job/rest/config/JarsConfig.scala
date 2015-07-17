package spark.job.rest.config

import com.typesafe.config.Config

/**
 * Configuration for jars processing
 */
trait JarsConfig extends ConfigDependent {
  import JarsConfig._

  lazy val jarFolder = config.getString(jarFolderPropertyPath)
}

object JarsConfig extends ConfigDependentCompanion[JarsConfig] {
  val classPathJarSeparator = ":"
  val jarFolderPropertyPath = "spark.job.rest.appConf.jars.path"

  override def configDependentInstance(config: Config): JarsConfig =
    new Configured(config) with JarsConfig
}
