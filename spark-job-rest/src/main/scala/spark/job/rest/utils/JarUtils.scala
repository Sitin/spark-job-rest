package spark.job.rest.utils

import java.io.{File, FileNotFoundException}

import spark.job.rest.api.entities.Jars
import spark.job.rest.config.JarsConfig
import spark.job.rest.exceptions.MissingJarException

/**
 * Mixable helpers for JAR manipulation which depends on application config.
 */
trait JarUtils extends JarsConfig {
  def getJarsPathForSpark(jars: Jars): List[String] =
    jars.list map { x: String =>
      JarUtils.getJarPathForSpark(x, jarFolder)
    }

  def getJarsPathForSpark(jars: String): List[String] =
    getJarsPathForSpark(Jars.fromString(jars))

  def getJarsPathForClasspath(jars: Jars, contextName: String): String =
    jars.list map { x =>
      JarUtils.getPathForClasspath(x, jarFolder, contextName)
    } mkString JarsConfig.classPathJarSeparator

  def getJarsPathForClasspath(jars: String, contextName: String): String =
    getJarsPathForClasspath(Jars.fromString(jars), contextName)
}

/**
 * JAR's manipulation functions.
 */
object JarUtils {
  /**
   * Checks that JAR is a valid ZIP archive
   * @param bytes file as byte array
   * @return
   */
  def validateJar(bytes: Array[Byte]): Boolean = {
    // For now just check the first few bytes are the ZIP signature: 0x04034b50 little endian
    if(bytes.length < 4 || bytes(0) != 0x50 || bytes(1) != 0x4b || bytes(2) != 0x03 || bytes(3) != 0x04){
      false
    } else {
      true
    }
  }

  /**
   * Calculates absolute file path and checks for file existence, copies HDFS files locally.
   * @param path path to file
   * @param jarFolder jar folder
   * @param contextName context name
   * @return
   * @throws FileNotFoundException if file not found
   */
  def getPathForClasspath(path: String, jarFolder: String, contextName: String): String = {
    val diskPath =
      if(path.startsWith("/")) {
        path
      } else if (path.startsWith("hdfs")) {
        val tempFolder = jarFolder + File.pathSeparator + "tmp" + File.pathSeparator + contextName
        FileUtils.createFolder(tempFolder, overwrite = true)
        HdfsUtils.copyJarFromHdfs(path, tempFolder)
        tempFolder + File.pathSeparator + getJarName(path)
      } else {
        jarFolder + File.separator + getJarName(path)
      }

    val diskFile = new File(diskPath)

    if (!diskFile.exists()) {
      throw new MissingJarException(path, "spark-submit")
    }

    diskFile.getAbsolutePath
  }

  def getJarName(path: String): String = {
    if(path.contains('\\')) {
      path.substring(path.lastIndexOf('\\'))
    } else {
      path
    }
  }

  /**
   * Returns absolute path and checks it's existence, preserves HDFS paths.
   * @param path file path
   * @param jarFolder path to jar folder
   * @return
   * @throws FileNotFoundException if file not found
   */
  def getJarPathForSpark(path: String, jarFolder: String): String = {
    if (path.startsWith("hdfs")) {
      // Throw exception if file is not exists
      if (!HdfsUtils.hdfsFileExists(path)) {
        throw new MissingJarException(path, "Spark config")
      }
      path
    } else {
      val diskPath = if(path.startsWith("/"))
        path
      else
        jarFolder + File.separator + getJarName(path)

      val diskFile = new File(diskPath)
      if (!diskFile.exists()) {
        throw new MissingJarException(path, "Spark config")
      }

      diskFile.getAbsolutePath
    }
  }

}
