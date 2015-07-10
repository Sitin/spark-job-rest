package config

import com.typesafe.config.Config

/**
 * This is a base trait for mix-ins which provide information derived from instance config.
 */
trait ConfigDependent {
  /**
   * This value should be defined in constructor.
   */
  def config: Config
}
