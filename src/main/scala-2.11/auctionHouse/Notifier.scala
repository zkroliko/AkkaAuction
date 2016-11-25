package auctionHouse

import akka.actor.SupervisorStrategy.{Escalate, Restart}
import akka.actor._
import akka.event.LoggingReceive
import akka.util.Timeout
import auctionHouse.Notifier.{Notification, NotificationContent}

import scala.concurrent.duration._

object Notifier {
  case class Notification(target: ActorSelection, content: NotificationContent)
  case class NotificationContent(name: String, price : BigDecimal, leader: Option[ActorRef])
  case class NotificationAck(target: ActorSelection, content: NotificationContent)
}

class Notifier extends Actor with ActorLogging{

//  val publisher = context.actorSelection("akka://auctionHouse/user/auctionPublisher") // todo: Fix for correct system
  val publisher = context.actorSelection("akka.tcp://auctionPublisher@127.0.0.1:2552/user/auctionPublisher")

  implicit val timeout = Timeout(5 seconds)

  def receive = LoggingReceive {
    case n @ NotificationContent(name,price,leader) => passNotification(n)
  }

  def passNotification(notificationContent: NotificationContent): Unit = {
    context.actorOf(NotifierRequest.props(Notification(publisher, notificationContent)))
  }

  override val supervisorStrategy: SupervisorStrategy = OneForOneStrategy(maxNrOfRetries = 100, loggingEnabled = false) {
    case ex: ActorNotFound =>
      println("Publishing message failed, retrying:" + ex.getMessage)
      Restart

    case _ => Escalate
  }

}
