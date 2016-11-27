package auctionHouse

import akka.actor.{ActorLogging, Actor}
import akka.event.LoggingReceive
import auctionHouse.Notifier.NotificationAck

object AuctionPublisher {
  case object Init
}

class AuctionPublisher extends Actor with ActorLogging{
  import AuctionPublisher._

  def receive = LoggingReceive {
    case Init =>
      println(s"Auction publisher has started: ${self.path}")
    case n: Notifier.NotificationContent if n.leader.isEmpty =>
      println(s"Publisher: ${n.name} is inactive with starting price ${n.price}")
      sender ! NotificationAck
    case n: Notifier.NotificationContent if n.leader.isDefined =>
      println(s"Publisher: ${n.name} is active with price ${n.price}")
      sender ! NotificationAck
  }
}
