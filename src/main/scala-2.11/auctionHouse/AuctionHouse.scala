package auctionHouse

import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive
import auctionHouse.Auction.Start

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

  val nAuctions = 5
  val nBidders = 2

  def receive = LoggingReceive {
    case Init =>
      val descriptions = (1 to nAuctions).map(n => AuctionDescription(n.toString,200.0+n))
      val auctions = descriptions.map(desc =>context.actorOf(Props(new Auction(desc)))).toList
      val bidders = (1 to nBidders).map(n => context.actorOf(Props[Bidder])).toList

      auctions.foreach(_ ! Start)
      bidders.foreach(_ ! AuctionList(auctions))
  }
}
