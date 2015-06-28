package server.domain.actors

import akka.actor.ActorSystem
import akka.pattern.{ask, gracefulStop}
import akka.testkit.TestActorRef
import com.typesafe.config.{Config, ConfigFactory}
import context.{FakeContext, JobContextFactory}
import org.apache.spark.SparkContext
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.concurrent.TimeLimitedTests
import org.scalatest.junit.JUnitRunner
import persistence.schema.nextId
import server.domain.actors.ContextActor.Initialize
import test.durations.{contextTimeout, dbTimeout, timeLimits}
import test.fixtures

import scala.util.Success

/**
 * Test suit for [[ContextActor]]
 */
@RunWith(classOf[JUnitRunner])
class ContextActorSpec extends WordSpec with MustMatchers with BeforeAndAfter with TimeLimitedTests {
  val timeLimit = timeLimits.contextTest

  val config = fixtures.applicationConfig

  implicit val timeout = contextTimeout
  implicit val system = ActorSystem("localSystem")

  var contextActorRef: TestActorRef[ContextActor] = _
  def contextActor = contextActorRef.underlyingActor

  var connectionProvider: TestActorRef[DatabaseServerActor] = _

  val contextName = "demoContext"
  val contextId = nextId

  def initMessage(contextConfig: Config = config) =
    Initialize(contextName, nextId, connectionProvider, contextConfig, List())

  before {
    connectionProvider = TestActorRef(new DatabaseServerActor(config))
    contextActorRef = TestActorRef(new ContextActor(config))
  }

  after {
    contextActor.jobContext.stop()
    gracefulStop(connectionProvider, dbTimeout.duration)
  }

  "ContextActor" should {
    "create Spark context when initialized" in {
      val future = contextActorRef ? initMessage()
      val Success(result: ContextActor.Initialized) = future.value.get
      result must not equal null
      contextActor.jobContext.isInstanceOf[SparkContext] mustEqual true
    }

    "have default factory for Spark context" in {
      val configWithoutFactory = config.withoutPath(JobContextFactory.classNameConfigEntry)
      val future = contextActorRef ? initMessage(configWithoutFactory)
      val Success(result: ContextActor.Initialized) = future.value.get
      result must not equal null
      contextActor.jobContext.isInstanceOf[SparkContext] mustEqual true
    }

    "create context from specified factory" in {
      val future = contextActorRef ? initMessage(fakeContextFactoryConfig)
      val Success(result: ContextActor.Initialized) = future.value.get
      result must not equal null
      contextActor.jobContext.isInstanceOf[FakeContext] mustEqual true
    }
  }

  val fakeContextFactoryConfig = ConfigFactory.parseString(
    """
      |{
      |  context.job-context-factory = "context.FakeJobContextFactory",
      |}
    """.stripMargin).withFallback(config)
}