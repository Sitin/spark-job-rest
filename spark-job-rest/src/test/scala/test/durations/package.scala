package test

import java.util.concurrent.TimeUnit

import akka.util.Timeout

package object durations {
  val dbTimeout = Timeout(1, TimeUnit.SECONDS)
  val contextTimeout = Timeout(10, TimeUnit.SECONDS)

  object timeLimits {
    import org.scalatest.time.SpanSugar._

    val dbTest = 5.seconds
    val contextTest = 20.seconds
  }
}
