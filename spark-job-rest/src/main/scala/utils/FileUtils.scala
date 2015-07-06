package utils

import java.io.{File, FileOutputStream}

import org.apache.commons

/**
 * Common utilities function for file manipulation
 */
object FileUtils {

  def writeToFile(fileName: String, folderName: String, bytes: Array[Byte]): Unit = {
    val fos = new FileOutputStream(folderName + File.separator + fileName)
    fos.write(bytes)
    fos.close()
  }

  def createFolder(folder: String, overwrite: Boolean) = {
    val file = new File(folder)
    if(!file.exists()){
      file.mkdirs()
    } else if (overwrite){
      commons.io.FileUtils.deleteDirectory(file)
      file.mkdirs()
    }
  }

  def deleteFolder(folder: String): Unit = {
    val file = new File(folder)
    if(file.exists()){
      commons.io.FileUtils.deleteDirectory(file)
    }
  }
}
