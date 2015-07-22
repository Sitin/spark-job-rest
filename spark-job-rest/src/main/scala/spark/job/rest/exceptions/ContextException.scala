package spark.job.rest.exceptions

import akka.actor.FSM.Reason
import spark.job.rest.server.domain.actors.ContextProviderActor.DispatcherStopReason

class ContextException(val contextName: String, message: String, cause: Throwable = null)
  extends RuntimeException (s"$contextName: $message", cause)

class ContextDispatcherStartException(contextName: String, cause: Throwable = null)
  extends ContextException(contextName, s"Exception during context $contextName creation", cause)

class UnexpectedContextDispatcherStopException(contextName: String, val reason: DispatcherStopReason)
  extends ContextException(contextName, s"Dispatcher for context $contextName unexpected stop with $reason.")

class UnexpectedContextProviderStop(contextName: String, val reason: Reason)
  extends ContextException(contextName, s"Context $contextName provider unexpected stop due to $reason.")

class JobContextStartException(contextName: String, cause: Throwable = null)
  extends ContextException(contextName, s"Exception during job context $contextName start", cause)