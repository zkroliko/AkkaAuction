package auctionHouse

import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive
import auctionHouse.Auction.{Bid, Start}
import auctionHouse.Seller.BuildFromDescriptions

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

  val nSellers = 1
  val nAuctionsPerSeller = 5
  val nBidders = 2

  def receive = LoggingReceive {
    case Init =>
      val sellers = (1 to nSellers).map{n => context.actorOf(Props[Seller],"seller1")}
      val descriptions = (1 to nAuctionsPerSeller).map(n => AuctionDescription("item"+n.toString,200.0+n))
      sellers.foreach{s => s ! BuildFromDescriptions(descriptions.toList)}
      val bidders = (1 to nBidders).map(n => context.actorOf(Props[Bidder])).toList

      descriptions.foreach { d =>
        val sel = context.actorSelection("akka://auctionHouse/user/auctionHouse/*/"+d.title)
        println(sel)
      }

  }
}
