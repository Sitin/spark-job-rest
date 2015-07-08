package persistence

import com.typesafe.config.Config
import config.durations
import org.h2.tools.Server
import org.slf4j.LoggerFactory
import persistence.slickWrapper.Driver.api._
import server.domain.actors.messages.DatabaseInfo
import utils.ActorUtils.findAvailablePort
import utils.FileUtils.deleteFolder


object DatabaseServer {
  val portConfigEntry = "spark.job.rest.database.port"
  val hostConfigEntry = "spark.job.rest.database.host"
  val nameConfigEntry = "spark.job.rest.database.name"
  val baseDirConfigEntry = "spark.job.rest.database.baseDir"
}

/**
 * This class responsible for database server life cycle.
 * @param config application config
 */
class DatabaseServer(config: Config) {
  import DatabaseServer._

  implicit val timeout = durations.db.initializationTimeout

  private val log = LoggerFactory.getLogger(getClass)

  /**
   * Server host
   */
  val host = config.getString(hostConfigEntry)

  /**
   * Database name
   */
  val dbName = config.getString(nameConfigEntry)

  /**
   * Calculates full path to base directory
   */
  val baseDir = {
    val path = config.getString(baseDirConfigEntry)
    if (path startsWith "/")
      path
    else
      System.getProperty("user.dir") + s"/$path"
  }

  /**
   * Instantiates database server from [[org.h2.server.TcpServer]]
   */
  val server = Server.createTcpServer(
    "-tcpPort", findAvailablePort(config.getInt(portConfigEntry)).toString,
    "-baseDir", baseDir
  )

  /**
   * Database server port
   */
  val port = server.getPort

  /**
   * Additional connection parameters
   */
  val connectionParameters = "AUTO_RECONNECT=TRUE"

  /**
   * JDBC connection string. All clients should use this to connect to database.
   */
  val connectionString = s"jdbc:h2:tcp://$host:$port/./$dbName;$connectionParameters"

  /**
   * Database connection
   */
  lazy val db = Database.forURL(url = connectionString)

  /**
   * Starts server
   */
  def start() = {
    log.info(s"Starting database server at $baseDir")
    server.start()
    log.info(s"Database serer started at $host:$port. Base directory: '$baseDir', connection: '$connectionString'")
    this
  }

  /**
   * Stops server
   */
  def stop() = {
    server.stop()
    log.warn("Database serer stopped.")
    this
  }

  /**
   * Returns server status
   * @return
   */
  def status = server.getStatus

  /**
   * Returns database info: database name, server status and JDBC connection string
   * @return
   */
  def databaseInfo = DatabaseInfo(dbName, server.getStatus, server.getURL, connectionString)

  /**
   * Recreates database storage
   * @return
   */
  def reset() = synchronized {
    stop()
    deleteBaseDir()
    this
  }

  /**
   * Deletes base directory
   */
  private def deleteBaseDir(): Unit = {
    deleteFolder(baseDir)
    log.warn("Database server base directory deleted.")
  }
}
