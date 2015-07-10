package config.durations

import java.util.concurrent.TimeUnit

import akka.util.Timeout
import config.ConfigDependent

import scala.concurrent.duration.FiniteDuration

/**
 * Default durations including timeouts, intervals and retries.
 * All properties should be functions or lazy values.
 */
trait Durations extends ConfigDependent {
  private val prefix = s"spark.job.rest.durations"

  object durations {
    object ask {
      def timeout = Timeout(config.getLong(s"$prefix.ask.timeout"), TimeUnit.MILLISECONDS)
    }

    object init {
      def timeout = Timeout(config.getLong(s"$prefix.init.timeout"), TimeUnit.MILLISECONDS)
      def tries = config.getInt(s"$prefix.init.tries")
    }

    object context {
      def sleep = FiniteDuration(config.getLong(s"$prefix.context.sleep"), TimeUnit.MILLISECONDS)
      def timeout = Timeout(config.getLong(s"$prefix.context.timeout"), TimeUnit.MILLISECONDS)
      def interval = FiniteDuration(config.getLong(s"$prefix.context.interval"), TimeUnit.MILLISECONDS)
      def tries = config.getInt(s"$prefix.context.tries")
    }

    object db {
      def timeout = Timeout(config.getLong(s"$prefix.db.timeout"), TimeUnit.MILLISECONDS)
      def initializationTimeout = Timeout(config.getLong(s"$prefix.db.initialization-timeout"), TimeUnit.MILLISECONDS)

      object connection {
        def timeout = Timeout(config.getLong(s"$prefix.db.connection.timeout"), TimeUnit.MILLISECONDS)
        def tries = config.getInt(s"$prefix.db.connection.tries")
      }
    }
  }
}
