package spark.job.rest

import com.typesafe.config.ConfigFactory

/**
 * System wide config and config utils
 */
package object config {
  /**
   * Default application configuration.
   * Loads deployment configuration `deploy.conf` on top of application defaults `application.conf`
   */
  lazy val defaultApplicationConfig = ConfigFactory.load("deploy").withFallback(ConfigFactory.load())


  /**
   * Master application configuration.
   */
  lazy val masterApplicationConfig = defaultApplicationConfig
//    .withoutPath("spark.job.rest.context.akka")

  /**
   * Context application configuration.
   */
  lazy val contextApplicationConfig = defaultApplicationConfig
//    .withoutPath("spark.job.rest.master")
//    .withoutPath("spark.job.rest.appConf")
//    .withoutPath("spark.job.rest.database")
}
