package spark.job.rest.utils

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}

/**
 * HDFS utility functions.
 */
object HdfsUtils {
  /**
   * Copies file from HDFS to local file system.
   * @param hdfsPath HDFS path
   * @param outputFolder local path
   * @throws java.io.IOException if copy operation failed
   */
  def copyJarFromHdfs(hdfsPath: String, outputFolder: String): Unit = {
    val conf = new Configuration()
    conf.set("fs.defaultFS", hdfsPath)
    val hdfsFileSystem = FileSystem.get(conf)
    hdfsFileSystem.copyToLocalFile(new Path(hdfsPath), new Path(outputFolder))
  }

  /**
   * Checks whether file exists.
   * @param hdfsPath HDFS path
   * @return
   * @throws java.io.IOException on HDFS failure
   */
  def hdfsFileExists(hdfsPath: String): Boolean = {
    val conf = new Configuration()
    conf.set("fs.defaultFS", hdfsPath)
    val hdfsFileSystem = FileSystem.get(conf)
    hdfsFileSystem.exists(new Path(hdfsPath))
  }

}
