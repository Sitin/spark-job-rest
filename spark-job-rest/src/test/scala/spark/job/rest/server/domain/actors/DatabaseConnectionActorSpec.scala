package spark.job.rest.server.domain.actors

import akka.actor.ActorSystem
import akka.pattern.{ask, gracefulStop}
import akka.testkit.TestActorRef
import com.typesafe.config.ConfigFactory
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.concurrent.TimeLimitedTests
import org.scalatest.junit.JUnitRunner
import spark.job.rest.persistence.schema
import spark.job.rest.persistence.schema._
import spark.job.rest.persistence.slickWrapper.Driver.api._
import spark.job.rest.server.domain.actors.messages._
import spark.job.rest.test.durations.{dbTimeout, timeLimits}
import spark.job.rest.test.fixtures

import scala.concurrent.Await

/**
 * Test suit for [[ContextActor]]
 */
@RunWith(classOf[JUnitRunner])
class DatabaseConnectionActorSpec extends WordSpec with MustMatchers with BeforeAndAfter with TimeLimitedTests {
  val timeLimit = timeLimits.dbTest

  val config = ConfigFactory.load()

  implicit val timeout = dbTimeout
  implicit val system = ActorSystem("localSystem")

  var databaseServerActorRef: TestActorRef[DatabaseServerActor] = _
  var databaseConnectionActorRef: TestActorRef[DatabaseConnectionActor] = _

  before {
    databaseServerActorRef = TestActorRef(new DatabaseServerActor(config))
    databaseConnectionActorRef = TestActorRef(new DatabaseConnectionActor(databaseServerActorRef, config))
  }

  after {
    Await.result(gracefulStop(databaseServerActorRef, timeout.duration), timeout.duration)
    Await.result(gracefulStop(databaseConnectionActorRef, timeout.duration), timeout.duration)
  }

  "DatabaseConnectionActor" should {
    "provide database info" in {
      val DatabaseInfo(_, status, _, connectionString) = Await.result(databaseConnectionActorRef ? GetDatabaseInfo, timeout.duration)
      status must not equal null
      connectionString must not equal null
    }

    "provide database connection" in {
      val DatabaseConnection(db) = Await.result(databaseConnectionActorRef ? GetDatabaseConnection, timeout.duration)
      val context = fixtures.contextEntity
      Await.result(db.run(contexts += context), timeout.duration)
      Await.result(db.run(schema.contexts.result), timeout.duration).size must not equal 0
    }
  }
}
