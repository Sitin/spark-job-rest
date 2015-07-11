package spark.job.rest.config.durations

/**
 * Injects default timeout.
 *
 * This should comes last in extends/with chain to be applied as an implicit
 */
trait AskTimeout extends Durations {
  implicit def timeout = durations.ask.timeout
}
