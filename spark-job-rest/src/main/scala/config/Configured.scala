package config

import com.typesafe.config.Config

/**
 * Class for creating instances of configurable traits.
 */
class Configured(val config: Config) extends ConfigDependent
