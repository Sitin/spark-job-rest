package spark.job.rest

import java.io.File

import com.typesafe.config.ConfigFactory

import scala.util.Try

/**
 * System wide config and config utils
 */
package object config {
  /**
   * Default application configuration is just an `application.conf`.
   */
  lazy val defaultApplicationConfig = ConfigFactory.load()

  /**
   * Optionally loads config form file or resource specified by resource parameter.
   * @param resource path
   * @return
   */
  def applicationConfigWithOverrides(resource: String) = {
    val confFile = new File(resource)
    if (confFile.isFile)
      ConfigFactory.parseFile(confFile).withFallback(defaultApplicationConfig)
    else
      Try { ConfigFactory.load(resource).withFallback(defaultApplicationConfig) } getOrElse defaultApplicationConfig
  }

  /**
   * Master application configuration.
   */
  def masterApplicationConfig(resource: String) = applicationConfigWithOverrides(resource)
//    .withoutPath("spark.job.rest.context.akka")

  /**
   * Context application configuration.
   */
  def contextApplicationConfig(resource: String) = applicationConfigWithOverrides(resource)
//    .withoutPath("spark.job.rest.master")
//    .withoutPath("spark.job.rest.appConf")
//    .withoutPath("spark.job.rest.database")
}
