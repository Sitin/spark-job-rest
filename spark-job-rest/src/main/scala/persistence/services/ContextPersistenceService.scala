package persistence.services

import api.entities.ContextDetails
import api.entities.ContextState._
import api.types._
import com.typesafe.config.Config
import config.durations.Durations
import org.slf4j.LoggerFactory
import persistence.schema.ColumnTypeImplicits._
import persistence.schema._
import persistence.slickWrapper.Driver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}

/**
 * Collection of methods for persisting context entities
 */
trait ContextPersistenceService extends Durations {
  private val log = LoggerFactory.getLogger(getClass)

  private lazy val dbTimeout = durations.db.timeout

  /**
   * Inserts new context to database
   * @param context context entity to persist
   * @param db database connection
   * @return future of affected columns
   */
  def insertContext(context: ContextDetails, db: Database): Future[Int] = {
    log.info(s"Inserting context ${context.id}.")
    db.run(contexts += context)
  }

  /**
   * Synchronously updates state for context with specified id.
   * Does not replace [[Error]] or [[Terminated]] states.
   *
   * @param contextId context's ID
   * @param newState context state to set
   * @param db database connection
   */
  def updateContextState(contextId: ID, newState: ContextState, db: Database, newDetails: String = ""): Unit = {
    log.info(s"Updating context $contextId state to $newState with details: $newDetails")
    val affectedContext = for { c <- contexts if c.id === contextId && c.state =!= Failed && c.state =!= Terminated } yield c
    val contextStateUpdate = affectedContext map (x => (x.state, x.details)) update (newState, newDetails)
    Await.ready(db.run(contextStateUpdate), dbTimeout.duration)
  }

  /**
   * Synchronously updates Spark UI port for context with specified id.
   * @param contextId context's ID
   * @param port Spark UI port to set
   * @param db database connection
   */
  def setContextSparkUiPort(contextId: ID, port: String, db: Database): Unit = {
    log.info(s"Updating context $contextId Spark UI port to $port.")
    val affectedContext = for { c <- contexts if c.id === contextId } yield c
    val updateQuery = affectedContext map (_.sparkUiPort) update Some(port)
    Await.ready(db.run(updateQuery), dbTimeout.duration)
  }

  /**
   * Synchronously persists context creation.
   * @param contextId context's ID
   * @param finalConfig config finally applied to context
   * @param db database connection
   */
  def persistContextCreation(contextId: ID, finalConfig: Config, db: Database): Unit = {
    log.info(s"Persisting context $contextId creation.")
    val affectedContext = for { c <- contexts if c.id === contextId } yield c
    val columnsToUpdate = affectedContext map (c => (c.state, c.details, c.finalConfig))
    val updateQuery = columnsToUpdate update (Running, "Context created", Some(finalConfig))
    Await.ready(db.run(updateQuery), dbTimeout.duration)
  }

  def contextById(contextId: ID, db: Database): Future[Option[ContextDetails]] = {
    db.run(contexts.filter(c => c.id === contextId).result).map {
      case Seq(context) => Some(context)
      case _ => None
    }
  }

  def allContexts(db: Database): Future[Array[ContextDetails]] = {
    db.run(contexts.result).map(_.toArray)
  }

  def allInactiveContexts(db: Database): Future[Array[ContextDetails]] = {
    db.run(contexts.filter(c => c.state =!= Running).result).map(_.toArray)
  }
}
