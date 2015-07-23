package spark.job.rest.server.domain.actors

import akka.actor.ActorSystem
import akka.pattern.{ask, gracefulStop}
import akka.testkit.TestActorRef
import com.typesafe.config.{Config, ConfigFactory}
import org.junit.runner.RunWith
import org.scalatest.concurrent.TimeLimitedTests
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, MustMatchers, WordSpec}
import spark.job.rest.api.entities.ContextState
import spark.job.rest.api.responses.{Context, Contexts}
import spark.job.rest.config.{JarsConfig, MasterNetworkConfig}
import spark.job.rest.server.domain.actors.ContextManagerActor.{CreateContext, DeleteContext, GetAllContexts, GetContextInfo}
import spark.job.rest.test.durations._
import spark.job.rest.test.fixtures
import spark.job.rest.utils.ContextUtils

import scala.concurrent.Await
import scala.sys.process._
import scala.util.Success

/**
 * Test suite for [[ContextManagerActor]]
 */
@RunWith(classOf[JUnitRunner])
class ContextManagerActorSpec extends WordSpec with MustMatchers with BeforeAndAfter with BeforeAndAfterAll with TimeLimitedTests
with JarsConfig with MasterNetworkConfig {
  val timeLimit = timeLimits.contextTest

  val config = fixtures.applicationConfig

  implicit val timeout = contextTimeout
  implicit val system = ActorSystem("ManagerSystem", masterAkkaSystemConfig)

  var contextManagerActorRef: TestActorRef[ContextManagerActor] = _
  def contextManagerActor = contextManagerActorRef.underlyingActor

  var databaseServerActorRef: TestActorRef[DatabaseServerActor] = _
  def databaseServerActor = databaseServerActorRef.underlyingActor

  val defaultContextName = "test-context"

  override def beforeAll() {
    s"mkdir -p $jarFolder".!!
    s"touch $jarFolder/existing.jar".!!
    databaseServerActorRef = TestActorRef(new DatabaseServerActor(config), name = "DatabaseServer")
  }

  before {
    contextManagerActorRef = TestActorRef(new ContextManagerActor(config, databaseServerActorRef), name = "ContextManager")
  }

  after {
    Await.result(gracefulStop(contextManagerActorRef, stopTimeout.duration), stopTimeout.duration)
    contextManagerActorRef.stop()
  }

  override def afterAll() {
    s"rm -rf $jarFolder".!!
    Await.result(gracefulStop(databaseServerActorRef, stopTimeout.duration), stopTimeout.duration)
    databaseServerActorRef.stop()
  }

  "ContextManagerActor" should {
    "create context by request" in {
      val creationResponse = createContext()
      creationResponse.contextName mustEqual defaultContextName
      creationResponse.state mustEqual ContextState.Running
    }

    "provide information for context" in {
      createContext()
      val infoFuture = contextManagerActorRef ? GetContextInfo(defaultContextName)
      val contextInfo = Await.result(infoFuture, minorContextTimeout.duration).asInstanceOf[Context]
      contextInfo.contextName mustEqual defaultContextName
      contextInfo.state mustEqual ContextState.Running
    }

    "provide information for all active contexts" in {
      createContext("context-1")
      createContext("context-2")
      val infoFuture = contextManagerActorRef ? GetAllContexts
      val Contexts(contexts) = Await.result(infoFuture, minorContextTimeout.duration).asInstanceOf[Contexts]
      contexts.length mustEqual 2
    }

    "delete context by request" in {
      createContext()
      val Success(result) = deleteContext()
      result mustEqual defaultContextName
    }
  }

  def createContext(contextName: String = defaultContextName, jars: String = "existing.jar", configToPass: Config = contextConfig): Context = {
    val creationFuture = contextManagerActorRef ? CreateContext(contextName, jars, configToPass)
    Await.result(creationFuture, contextTimeout.duration).asInstanceOf[Context]
  }

  def deleteContext(contextName: String = defaultContextName): Any = {
    val removeFuture = contextManagerActorRef ? DeleteContext(contextName)
    Await.result(removeFuture, minorContextTimeout.duration)
  }

  val contextConfig = ConfigFactory.parseString(
    s"""
       |${ContextUtils.jarsConfigEntry} = ["existing.jar"]
       |spark.driver.allowMultipleContexts = true
     """.stripMargin
  )
}
