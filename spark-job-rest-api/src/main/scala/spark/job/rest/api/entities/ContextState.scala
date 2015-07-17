package spark.job.rest.api.entities

/**
 * States of Spark jobs.
 */
object ContextState extends Enumeration {
  type ContextState = Value
  val Requested = Value("Requested")
  val Queued = Value("Queued")
  val Started = Value("Started")
  val Initialized = Value("Initialized")
  val Running = Value("Running")
  val Terminated = Value("Terminated")
  val Failed = Value("Failed")
}
