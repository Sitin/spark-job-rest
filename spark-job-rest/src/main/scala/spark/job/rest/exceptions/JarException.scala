package spark.job.rest.exceptions

import java.io.IOException

import spark.job.rest.api.entities.Jars

class JarException(val jars: Jars, message: String, cause: Throwable = null) extends IOException(message, cause)

class MissingJarException(val jar: String, scenario: String) extends JarException(Jars.fromString(jar), s"Jar '$jar' does not exists at $scenario.")
