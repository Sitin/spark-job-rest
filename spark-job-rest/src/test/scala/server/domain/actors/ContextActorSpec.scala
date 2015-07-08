package server.domain.actors

import akka.actor.ActorSystem
import akka.pattern.{ask, gracefulStop}
import akka.testkit.TestActorRef
import api.types.nextIdentifier
import api.{ContextLike, SparkJobBase}
import com.typesafe.config.{Config, ConfigFactory}
import context.JobContextFactory
import org.apache.spark.SparkContext
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.concurrent.Futures
import org.scalatest.junit.JUnitRunner
import server.domain.actors.ContextActor.Initialize
import test.durations.{contextTimeout, dbTimeout, timeLimits}
import test.fixtures

import scala.util.Success

trait FakeContext

class FakeJobContextFactory extends JobContextFactory {
  type C = ContextLike
  def makeContext(config: Config, contextName: String): ContextLike = new ContextLike with FakeContext {
    val contextClass = classOf[FakeContext].getName
    override def stop(): Unit = {}
    override def isValidJob(job: SparkJobBase): Boolean = true
    override def sparkContext: SparkContext = null
  }
}

/**
 * Test suit for [[ContextActor]]
 */
@RunWith(classOf[JUnitRunner])
class ContextActorSpec extends WordSpec with MustMatchers with BeforeAndAfter with Futures {
  val timeLimit = timeLimits.contextTest

  val config = fixtures.applicationConfig

  implicit val timeout = contextTimeout
  implicit val system = ActorSystem("localSystem")

  var contextActorRef: TestActorRef[ContextActor] = _
  def contextActor = contextActorRef.underlyingActor

  var connectionProvider: TestActorRef[DatabaseServerActor] = _

  val contextName = "demoContext"
  val contextId = nextIdentifier

  def initMessage(contextConfig: Config = config) =
    Initialize(contextName, contextId, connectionProvider, contextConfig, List())

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
      val Success(ContextActor.Initialized) = future.value.get
      contextActor.jobContext.isInstanceOf[SparkContext] mustEqual true
    }

    "have default factory for Spark context" in {
      val configWithoutFactory = config.withoutPath(JobContextFactory.classNameConfigEntry)
      val future = contextActorRef ? initMessage(configWithoutFactory)
      val Success(ContextActor.Initialized) = future.value.get
      contextActor.jobContext.isInstanceOf[SparkContext] mustEqual true
    }

    "create context from specified factory" in {
      val future = contextActorRef ? initMessage(fakeContextFactoryConfig)
      val Success(ContextActor.Initialized) = future.value.get
      contextActor.jobContext.isInstanceOf[FakeContext] mustEqual true
    }
  }

  val fakeContextFactoryConfig = ConfigFactory.parseString(
    """
      |{
      |  spark.job.rest.context.job-context-factory = "server.domain.actors.FakeJobContextFactory",
      |}
    """.stripMargin).withFallback(config)
}
