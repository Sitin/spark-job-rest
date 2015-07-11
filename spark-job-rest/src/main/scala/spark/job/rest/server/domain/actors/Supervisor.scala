package spark.job.rest.server.domain.actors

import akka.actor.SupervisorStrategy._
import akka.actor.{Actor, OneForOneStrategy, Props, actorRef2Scala}
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import spark.job.rest.config.durations.Durations
 

class Supervisor(val config: Config) extends Actor with Durations {

  val log = LoggerFactory.getLogger(getClass)

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = durations.supervisor.tries, withinTimeRange = durations.supervisor.timeRange) {
      case e: Exception =>
        log.error("Exception", e)
        Resume
    }
 
  def receive = {     
      case (p: Props, name: String) => sender ! context.actorOf(p, name)
  }

}