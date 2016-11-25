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
    case n: Notifier.NotificationContent =>
      println(s"Publisher notified on ${n.name}")
      sender ! NotificationAck
  }
}
