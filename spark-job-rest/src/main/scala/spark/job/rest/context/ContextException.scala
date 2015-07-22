package spark.job.rest.context

import akka.actor.FSM.Reason
import spark.job.rest.server.domain.actors.ContextProviderActor.DispatcherStopReason

class ContextException(contextName: String, message: String, cause: Throwable = null) extends RuntimeException

class ContextProcessStartException(contextName: String, cause: Throwable = null)
  extends ContextException(contextName, s"Exception during context $contextName creation", cause)

class UnexpectedContextDispatcherStopException(contextName: String, reason: DispatcherStopReason)
  extends ContextException(contextName, s"Dispatcher for context $contextName unexpected stop with $reason.")

class UnexpectedContextProviderStop(contextName: String, reason: Reason)
  extends ContextException(contextName, s"Context $contextName provider unexpected stop due to $reason.")

class JobContextStartException(contextName: String, cause: Throwable = null)
  extends ContextException(contextName, s"Exception during job context $contextName start", cause)