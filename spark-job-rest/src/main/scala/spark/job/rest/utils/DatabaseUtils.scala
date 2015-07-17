package spark.job.rest.utils

import akka.actor.ActorRef
import akka.pattern.ask
import org.slf4j.LoggerFactory
import spark.job.rest.config.durations.Durations
import spark.job.rest.persistence.slickWrapper.Driver.api.Database
import spark.job.rest.server.domain.actors.messages._

import scala.annotation.tailrec
import scala.concurrent.{Await, TimeoutException}
import scala.util.{Failure, Success, Try}

trait DatabaseUtils extends Durations {
  private implicit def timeout = durations.db.connection.timeout
  private def maxReties = durations.db.connection.tries
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Synchronously requests connection from connection provider actor which may be either.
   * [[spark.job.rest.server.domain.actors.DatabaseServerActor]] or [[spark.job.rest.server.domain.actors.DatabaseConnectionActor]]
   * @param connectionProviderActor connection provider actor
   * @param retries optional retries to be performed
   * @throws TimeoutException if tried everything
   * @return connection
   */
  @tailrec
  final def dbConnection(connectionProviderActor: ActorRef, retries: Int = maxReties): Database = {
    Try {
      Await.result((connectionProviderActor ? GetDatabaseConnection).mapTo[DatabaseConnection], timeout.duration)
    } match {
      case Success(DatabaseConnection(db)) => db
      case Failure(e: TimeoutException) =>
        if (retries == 0)
          throw new TimeoutException("Failed to connect to database.")
        else
          dbConnection(connectionProviderActor, retries)
      case Failure(e: Throwable) =>
        logger.error("Error during obtaining database connection from provider", e)
        throw e
    }
  }
}
