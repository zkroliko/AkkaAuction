package auctionHouse

import akka.actor._
import auctionHouse.Notifier.{Notification, NotificationAck, NotificationContent}
import auctionHouse.NotifierRequest.NotificationDeliverySuccessful

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}


object NotifierRequest {

  def props(notificationToSend: Notification): Props = Props(new NotifierRequest(notificationToSend))

  case class NotificationDeliverySuccessful(payload: NotificationContent)
}

class NotifierRequest(toSend: Notification) extends Actor with ActorLogging {

  override def receive: Actor.Receive = {
    case NotificationAck => notifyParentAndFinish()
    case ex: Exception => throw ex
  }

  override def preStart(): Unit = {
    println(s"About to send notification on ${toSend.content.name}")
    sendNotification()
  }

  def sendNotification(): Unit = {
    val actorSelection: ActorSelection = toSend.target

    val publisher: Future[ActorRef] = actorSelection.resolveOne(5 seconds)
    publisher.onComplete {
      case Success(ref: ActorRef) => ref ! toSend.content
      case Failure(ex) => self ! ex // Resolved in different thread, have to resend
    }
  }

  def notifyParentAndFinish(): Unit = {
    context.parent ! NotificationDeliverySuccessful(toSend.content)
    context.stop(self)
  }
}
