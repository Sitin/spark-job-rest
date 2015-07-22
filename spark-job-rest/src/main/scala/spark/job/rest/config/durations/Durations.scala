package spark.job.rest.config.durations

import java.util.concurrent.TimeUnit

import akka.util.Timeout
import spark.job.rest.config.ConfigDependent

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
      // Following parameters defines context actor initialisation and registration
      def registerTimeout = FiniteDuration(config.getLong(s"$prefix.context.register-timeout"), TimeUnit.MILLISECONDS)
      def wakeupTimeout = FiniteDuration(config.getLong(s"$prefix.context.wakeup-timeout"), TimeUnit.MILLISECONDS)
      def watchGatewayTimeout = FiniteDuration(config.getLong(s"$prefix.context.watch-gateway-timeout"), TimeUnit.MILLISECONDS)
      def initialisationTimeout = FiniteDuration(config.getLong(s"$prefix.context.initialisation-timeout"), TimeUnit.MILLISECONDS)
      def startTimeout = FiniteDuration(config.getLong(s"$prefix.context.start-timeout"), TimeUnit.MILLISECONDS)
      // Context actor process start/stop durations
      def waitBeforeWatch = FiniteDuration(config.getLong(s"$prefix.context.wait-before-watch"), TimeUnit.MILLISECONDS)
      def waitForTermination = FiniteDuration(config.getLong(s"$prefix.context.wait-for-termination"), TimeUnit.MILLISECONDS)
    }

    object supervisor {
      def tries = config.getInt(s"$prefix.supervisor.tries")
      def timeRange = FiniteDuration(config.getLong(s"$prefix.supervisor.time-range"), TimeUnit.MILLISECONDS)
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
