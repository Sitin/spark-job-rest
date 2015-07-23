package spark.job.rest.test

import java.util.concurrent.TimeUnit

import akka.util.Timeout

/**
 * Durations for tests.
 */
object durations {
  val dbTimeout = Timeout(1, TimeUnit.SECONDS)
  val contextTimeout = Timeout(60, TimeUnit.SECONDS)
  val minorContextTimeout = Timeout(10, TimeUnit.SECONDS)
  val stopTimeout = Timeout(5, TimeUnit.SECONDS)

  object timeLimits {
    import org.scalatest.time.SpanSugar._

    val dbTest = 5.seconds
    val contextTest = 60.seconds
  }
}
