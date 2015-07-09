package utils

import java.io.{File, FileNotFoundException}

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
      throw new FileNotFoundException(s"Jar $path not found.")
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
        throw new FileNotFoundException(s"Jar $path not found.")
      }
      path
    } else {
      val diskPath = if(path.startsWith("/"))
        path
      else
        jarFolder + File.separator + getJarName(path)

      val diskFile = new File(diskPath)
      if (!diskFile.exists()) {
        throw new FileNotFoundException(s"Jar $path not found.")
      }

      diskFile.getAbsolutePath
    }
  }

}
