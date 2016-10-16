package auctionHouse

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.LoggingReceive
import auctionHouse.Auction.Start
import auctionHouse.AuctionHouse.AuctionList

object AuctionHouse {
  case object Init
  case class AuctionList(auctions: List[ActorRef])

  implicit class ReadableActorRef(ref: AnyRef) {
    def id: String = {
      s"-${Integer.toHexString(ref.hashCode).toUpperCase}-"
    }
  }
}

class AuctionHouse extends Actor {
  import AuctionHouse._

  val nAuctions = 10
  val nBidders = 5

  def receive = LoggingReceive {
    case Init =>
      val auctions = (1 to nAuctions).map(n =>context.actorOf(Props(new Auction(200.0+n)))).toList
      val bidders = (1 to nBidders).map(n => context.actorOf(Props[Bidder])).toList

      auctions.foreach(_ ! Start)
      bidders.foreach(_ ! AuctionList(auctions))
  }
}
