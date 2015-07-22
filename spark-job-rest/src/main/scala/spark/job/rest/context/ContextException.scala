package spark.job.rest.context

import akka.actor.FSM.Reason

class ContextException(contextName: String, message: String, cause: Throwable = null) extends RuntimeException

class ContextProcessStartException(contextName: String, cause: Throwable = null)
  extends ContextException(contextName, s"Exception during context $contextName creation", cause)

class UnexpectedContextProcessStopException(contextName: String, statusCode: Int)
  extends ContextException(contextName, s"Process for context $contextName unexpected exit with code $statusCode.")

class UnexpectedContextProviderStop(contextName: String, reason: Reason)
  extends ContextException(contextName, s"Context $contextName provider unexpected stop due to $reason.")

class JobContextStartException(contextName: String, cause: Throwable = null)
  extends ContextException(contextName, s"Exception during job context $contextName start", cause)