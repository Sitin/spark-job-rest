package utils

import java.io.{File, FileOutputStream}

import org.apache.commons

/**
 * Common utilities function for file manipulation
 */
object FileUtils {
  /**
   * Writes byte array to file in a folder and closes output stream. 
   * @param fileName file to write to
   * @param folder path to folder
   * @param bytes bytes to write
   */
  def writeToFile(fileName: String, folder: String, bytes: Array[Byte]): Unit = {
    val fos = new FileOutputStream(folder + File.separator + fileName)
    fos.write(bytes)
    fos.close()
  }

  /**
   * Creates folder recursively optionally overwriting existed one.
   * @param folder folder to create
   * @param overwrite whether overwrite existing folder or not.
   * @return
   */
  def createFolder(folder: File, overwrite: Boolean) = {
    if(!folder.exists()) {
      folder.mkdirs()
    } else if (overwrite) {
      commons.io.FileUtils.deleteDirectory(folder)
      folder.mkdirs()
    }
  }

  /**
   * Creates folder recursively optionally overwriting existed one.
   * @param folder folder path to create
   * @param overwrite whether overwrite existing folder or not.
   * @return
   */
  def createFolder(folder: String, overwrite: Boolean) = {
    val file = new File(folder)
    createFolder(file, overwrite)
  }

  /**
   * Deletes folder if exists
   * @param folder folder path
   */
  def deleteFolder(folder: String): Unit = {
    val file = new File(folder)
    if (file.exists()) {
      commons.io.FileUtils.deleteDirectory(file)
    }
  }
}
