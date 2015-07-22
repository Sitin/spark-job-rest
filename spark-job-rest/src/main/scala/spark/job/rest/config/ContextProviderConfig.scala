package spark.job.rest.config

/**
 * Configuration for [[spark.job.rest.server.domain.actors.ContextProviderActor]].
 */
trait ContextProviderConfig extends ConfigDependent{
  lazy val contextProcessActorClass =
    Class.forName(config.getString("spark.job.rest.context-creation.context-process-actor"))
}
