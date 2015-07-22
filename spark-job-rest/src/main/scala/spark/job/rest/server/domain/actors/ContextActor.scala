package spark.job.rest.server.domain.actors

import akka.actor.{Actor, Stash}
import com.google.gson.GsonBuilder
import com.typesafe.config.Config
import org.apache.commons.lang.exception.ExceptionUtils
import org.slf4j.LoggerFactory
import spark.job.rest.api.types.ID
import spark.job.rest.api.{ContextLike, SparkJobBase, SparkJobInvalid, SparkJobValid}
import spark.job.rest.context.JobContextFactory
import spark.job.rest.exceptions.JobContextStartException
import spark.job.rest.server.domain.actors.JobActor.{JobFailure, JobResult, JobStarted, RunJob}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * Context actor responsible for creation and managing Spark Context
 */
class ContextActor(contextName: String, contextId: ID, config: Config) extends Actor with Stash {
  // Internal imports
  import ContextApplicationActor.{ContextStarted, StartContext}
  import context.become

  val log = LoggerFactory.getLogger(getClass)

  /**
   * Spark job context which may be either directly [[org.apache.spark.SparkContext]]
   * or anything else (normally SQL context) based on top of it.
   */
  var jobContext: ContextLike = _

  /**
   * JSON serializer for job results
   */
  val gsonTransformer = new GsonBuilder().serializeSpecialFloatingPointValues().create()

  override def postStop(): Unit ={
    log.info(s"Shutting down SparkContext $contextName")
    Option(jobContext).foreach(_.stop())
  }

  def receive: Receive = {
    case StartContext =>
      stash()
      log.info(s"Context actor $contextName:$contextId received final context start command from ${sender()} .")
      become(started)
      try {
        jobContext = JobContextFactory.makeContext(config, contextName)
        sender() ! ContextStarted
      } catch {
        case e: Throwable =>
          log.error("Failed to start context", e)
          sender() ! new JobContextStartException(contextName, e)
      }
      unstashAll()
  }

  def started: Receive = {
    case RunJob(runningClass, _, jobConfig, jobId) =>
      log.info(s"Received RunJob message : runningClass=$runningClass contextName=$contextName uuid=$jobId ")
      val questioner = sender()

      val jobExecutionFuture = Future {
        Try {
          // Create job instance
          val sparkJob = try {
            val classLoader = Thread.currentThread.getContextClassLoader
            val runnableClass = classLoader.loadClass(runningClass)
            runnableClass.newInstance.asInstanceOf[SparkJobBase]
          } catch {
            // Job instantiation error is not fatal for the context
            case e: Throwable => throw new Exception("Job instantiation error", e)
          }

          jobContext.validateJob(sparkJob) match {
            case SparkJobValid => log.info(s"Job $jobId passed context validation.")
            case SparkJobInvalid(message) => throw new IllegalArgumentException(s"Invalid job $jobId: $message")
          }

          val jobConfigValidation = sparkJob.validate(jobContext.asInstanceOf[sparkJob.C], jobConfig.withFallback(config))
          jobConfigValidation match {
            case SparkJobInvalid(message) => throw new IllegalArgumentException(message)
            case SparkJobValid => log.info("Job config validation passed.")
          }

          val finalJobConfig = jobConfig.withFallback(config)
          questioner ! JobStarted(jobId, contextName, contextId, finalJobConfig)

          sparkJob.runJob(jobContext.asInstanceOf[sparkJob.C], finalJobConfig)
        }
      }
      try {
        jobExecutionFuture map {
          case Success(result) =>
            log.info(s"Finished running job : runningClass=$runningClass contextName=$contextName uuid=$jobId ")
            questioner ! JobResult(jobId, gsonTransformer.toJson(result))
          case Failure(e: Throwable) =>
            log.error(s"Error running job : runningClass=$runningClass contextName=$contextName uuid=$jobId ", e)
            JobFailure(jobId, "Job error: " + ExceptionUtils.getStackTrace(e))
          case x: Any =>
            log.error("Received ANY from running job !!! " + x)
            JobFailure(jobId, "Received ANY from running job !!! " + x)
        } onFailure {
          case e: Throwable =>
            log.error(s"Error running job : runningClass=$runningClass contextName=$contextName uuid=$jobId ", e)
            JobFailure(jobId, "Job execution error: " + ExceptionUtils.getStackTrace(e))
        }
      } catch {
        case e: Throwable =>
          val errorReport = ExceptionUtils.getStackTrace(e)
          log.error(s"Error during processing job $jobId result at context $contextName : $contextId: $errorReport")
      }
  }
}
