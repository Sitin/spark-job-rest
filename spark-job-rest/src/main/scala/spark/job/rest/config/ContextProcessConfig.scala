package spark.job.rest.config

import java.io.File

/**
 * Configuration for process creation
 */
trait ContextProcessConfig extends ConfigDependent {
  lazy val contextsBaseDir = new File(config.getString("spark.job.rest.context-creation.contexts-base-dir"))

  lazy val contextsStartScript = new File(config.getString("spark.job.rest.context-creation.context-start-script"))

  lazy val driverMemory = config.getString("driver.xmxMemory")

  lazy val sparkMaster = config.getString("spark.master")

  /**
   * Calculates context process working directory according to context name
   * @param contextName context name
   * @return
   */
  def contextProcessDir(contextName: String) = new File(s"${contextsBaseDir.getCanonicalPath}/$contextName")
}
