package utils

import com.typesafe.config.Config
import org.apache.spark.SparkConf
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

object ContextUtils {
  private val log = LoggerFactory.getLogger(getClass)

  /**
   * Creates Spark config from application config.
   *  - Copies everything that starts from `spark.` but not from `spark.job.rest.`.
   *  - Set jars from `spark.job.rest.context.jars`.
   *  - Set application name to context name.
   * @param config application config
   * @param contextName context name
   * @return
   */
  def configToSparkConf(config: Config, contextName: String): SparkConf = {
    val jars = config.getStringList("spark.job.rest.context.jars").asScala.toSeq
    log.info(s"Jars in config for $contextName: $jars")

    val sparkConf = new SparkConf()

    for(x <- config.entrySet().asScala if x.getKey.startsWith("spark.") && ! x.getKey.startsWith("spark.job.rest.")) {
      sparkConf.set(x.getKey, x.getValue.unwrapped().toString)
    }

    sparkConf
      .setAppName(contextName)
      .setJars(jars)
  }
}
