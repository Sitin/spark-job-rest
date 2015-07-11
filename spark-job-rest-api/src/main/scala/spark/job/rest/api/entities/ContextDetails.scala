package spark.job.rest.api.entities

import com.typesafe.config.Config
import spark.job.rest.api.entities.ContextState._
import spark.job.rest.api.types.{ID, nextIdentifier}

/**
 * Context entity
 * @param id context id
 * @param name context name
 * @param submittedConfig config submitted by client
 * @param finalConfig final config passed to context
 * @param jars list of JARs associated with the config
 * @param state context state
 * @param details detailed information about context state
 */
case class ContextDetails(name: String,
                          submittedConfig: Config,
                          finalConfig: Option[Config],
                          jars: Jars,
                          state: ContextState = Requested,
                          details: String = "",
                          sparkUiPort: Option[String] = None,
                          id: ID = nextIdentifier)
