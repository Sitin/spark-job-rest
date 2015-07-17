package spark.job.rest.server.domain.actors

import akka.actor.ActorSystem
import akka.pattern.{ask, gracefulStop}
import akka.testkit.TestActorRef
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.spark.SparkContext
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.concurrent.Futures
import org.scalatest.junit.JUnitRunner
import spark.job.rest.api.types.nextIdentifier
import spark.job.rest.api.{ContextLike, SparkJobBase}
import spark.job.rest.context.JobContextFactory
import spark.job.rest.server.domain.actors.ContextApplicationActor.Initialize
import spark.job.rest.test.durations.{contextTimeout, dbTimeout, timeLimits}
import spark.job.rest.test.fixtures

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
    contextActorRef = TestActorRef(new ContextActor(contextName, contextId, config))
  }

  after {
    contextActor.jobContext.stop()
    gracefulStop(connectionProvider, dbTimeout.duration)
  }

  "ContextActor" should {
    "create Spark context when requested" in {
      val future = contextActorRef ? initMessage()
      val Success(ContextApplicationActor.StartContext) = future.value.get
      contextActor.jobContext.isInstanceOf[SparkContext] mustEqual true
    }

    "have default factory for Spark context" in {
      val configWithoutFactory = config.withoutPath(JobContextFactory.classNameConfigEntry)
      val future = contextActorRef ? initMessage(configWithoutFactory)
      val Success(ContextApplicationActor.StartContext) = future.value.get
      contextActor.jobContext.isInstanceOf[SparkContext] mustEqual true
    }

    "create context from specified factory" in {
      val future = contextActorRef ? initMessage(fakeContextFactoryConfig)
      val Success(ContextApplicationActor.StartContext) = future.value.get
      contextActor.jobContext.isInstanceOf[FakeContext] mustEqual true
    }

  }

  val fakeContextFactoryConfig = ConfigFactory.parseString(
    """
      |{
      |  spark.job.rest.context.job-context-factory = "spark.job.rest.server.domain.actors.FakeJobContextFactory",
      |}
    """.stripMargin).withFallback(config)
}


@RunWith(classOf[JUnitRunner])
class GsonSpec extends WordSpec with MustMatchers {

  "Gson" should {
    "serialize doubles and floats properly" in {
      import com.google.gson.Gson
      import com.google.gson.GsonBuilder
      val gson = new GsonBuilder().serializeSpecialFloatingPointValues().create()
      val result = Map("foo" -> Double.NaN, "bar" -> Double.PositiveInfinity)
      val s = gson.toJson(result)
      s mustEqual "{\"key1\":\"foo\",\"value1\":NaN,\"key2\":\"bar\",\"value2\":Infinity}"
    }
  }
}