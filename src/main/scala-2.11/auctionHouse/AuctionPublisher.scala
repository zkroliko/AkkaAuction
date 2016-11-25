package auctionHouse

import akka.actor.Actor
import akka.event.LoggingReceive

object AuctionPublisher {
  case object Init
}

class AuctionPublisher extends Actor{
  import AuctionPublisher._

  def receive = LoggingReceive {
    case Init =>
      println("Auction publisher started")
      println(self.path)
  }
}
