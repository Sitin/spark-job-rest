package spark.job.rest.persistence.services

import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import spark.job.rest.api.entities.ContextDetails
import spark.job.rest.api.entities.ContextState._
import spark.job.rest.api.types._
import spark.job.rest.config.durations.Durations
import spark.job.rest.persistence.schema.ColumnTypeImplicits._
import spark.job.rest.persistence.schema.contexts
import spark.job.rest.persistence.slickWrapper.Driver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}

/**
 * Collection of methods for persisting context entities
 */
trait ContextPersistenceService extends Durations {
  private val logger = LoggerFactory.getLogger(getClass)

  private lazy val dbTimeout = durations.db.timeout

  /**
   * Inserts new context to database
   * @param context context entity to persist
   * @param db database connection
   * @return inserted context
   */
  def insertContext(context: ContextDetails, db: Database): ContextDetails = {
    logger.info(s"Inserting context ${context.id}.")
    Await.result(db.run(contexts += context), dbTimeout.duration)
    contextById(context.id, db).get
  }

  /**
   * Synchronously updates state for context with specified id if that context is exists.
   * Does not replace [[Error]] or [[Terminated]] states.
   * This function is dedicated to handling failure cases and is not intendent for general usege.
   * @param contextId context's ID
   * @param newState context state to set
   * @param db database connection
   * @param newDetails new context details
   */
  def updateContextStateIfExists(contextId: ID, newState: ContextState, db: Database, newDetails: String = ""): Unit = {
    logContextStateUpdate(contextId, newState, newDetails)
    val affectedContext = for { c <- contexts if c.id === contextId && c.state =!= Failed && c.state =!= Terminated } yield c
    val contextStateUpdate = affectedContext map (x => (x.state, x.details)) update (newState, newDetails)
    Await.ready(db.run(contextStateUpdate), dbTimeout.duration)
  }

  /**
   * Synchronously updates state for context with specified id.
   * Does not replace [[Error]] or [[Terminated]] states.
   *
   * @param contextId context's ID
   * @param newState context state to set
   * @param db database connection
   * @param newDetails new context details
   * @return updated context
   */
  def updateContextState(contextId: ID, newState: ContextState, db: Database, newDetails: String = ""): ContextDetails = {
    logContextStateUpdate(contextId, newState, newDetails)
    val affectedContext = for { c <- contexts if c.id === contextId && c.state =!= Failed && c.state =!= Terminated } yield c
    val contextStateUpdate = affectedContext map (x => (x.state, x.details)) update (newState, newDetails)
    Await.result(db.run(contextStateUpdate), dbTimeout.duration)
    contextById(contextId, db).get
  }

  /**
   * Synchronously set context to [[Initialized]] state and updates Spark UI port.
   * @param contextId context's ID
   * @param sparkUiPort Spark UI port to set
   * @param db database connection
   * @return updated context
   */
  def persistContextInitialisation(contextId: ID, sparkUiPort: Int, db: Database): ContextDetails = {
    logger.info(s"Updating context $contextId Spark UI port to $sparkUiPort.")
    val affectedContext = for { c <- contexts if c.id === contextId } yield c
    val values = (Initialized, Some(sparkUiPort), "Remote context application initialized and about to start job context.")
    val updateQuery = affectedContext map (c => (c.state, c.sparkUiPort, c.details )) update values
    Await.result(db.run(updateQuery), dbTimeout.duration)
    contextById(contextId, db).get
  }

  /**
   * Synchronously persists context creation.
   * @param contextId context's ID
   * @param finalConfig config finally applied to context
   * @param db database connection
   * @return updated context
   */
  def persistContextCreation(contextId: ID, finalConfig: Config, db: Database): ContextDetails = {
    logger.info(s"Persisting context $contextId creation.")
    val affectedContext = for { c <- contexts if c.id === contextId } yield c
    val columnsToUpdate = affectedContext map (c => (c.state, c.details, c.finalConfig))
    val updateQuery = columnsToUpdate update (Running, "Context created", Some(finalConfig))
    Await.result(db.run(updateQuery), dbTimeout.duration)
    contextById(contextId, db).get
  }

  /**
   * Synchronously returns context by ID.
   * @param contextId context ID to lookup
   * @param db database connection
   * @return context on [[None]]
   */
  def contextById(contextId: ID, db: Database): Option[ContextDetails] = {
    Await.result(db.run(contexts.filter(c => c.id === contextId).result).map {
      case Seq(context) => Some(context)
      case _ => None
    }, dbTimeout.duration)
  }

  /**
   * Asynchronously returns all contexts
   * @param db database connection
   * @return future of context details array
   */
  def allContexts(db: Database): Future[Array[ContextDetails]] = {
    db.run(contexts.result).map(_.toArray)
  }

  /**
   * Asynchronously returns all historical contexts
   * @param db database connection
   * @return future of context details array
   */
  def allInactiveContexts(db: Database): Future[Array[ContextDetails]] = {
    db.run(contexts.filter(c => c.state =!= Running).result).map(_.toArray)
  }

  /**
   * Logs context state update to proper appender (error for [[Failed]] and info for others).
   * @param contextId context ID
   * @param newState new state
   * @param newDetails new state details
   */
  def logContextStateUpdate(contextId: ID, newState: ContextState, newDetails: String): Unit =
    if (newState != Failed)
      logger.info(s"Updating context $contextId state to $newState with details: $newDetails")
    else
      logger.error(s"Updating context $contextId state to $newState with details: $newDetails")
}
