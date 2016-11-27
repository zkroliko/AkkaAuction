package auctionHouse

import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive
import auctionHouse.Seller.BuildFromDescriptions

object AuctionHouse {
  case object Init
  case class AuctionList(auctions: List[ActorRef])
  case class LookAtDescriptions(descriptions: List[AuctionDescription])
}

class AuctionHouse extends Actor {
  import AuctionHouse._

  val nSellers = 1
  val nAuctionsPerSeller = 1
  val nBidders = 4

  def receive = LoggingReceive {
    case Init =>
      val registration = context.actorOf(Props[AuctionSearch],"auctionSearch")
      val sellers = (1 to nSellers).map{n => context.actorOf(Props[Seller],"seller"+n)}
      val descriptions = (1 to nAuctionsPerSeller).map(n => AuctionDescription("item"+n.toString,200.0+n))
      sellers.foreach{s => s ! BuildFromDescriptions(descriptions.toList)}
      val bidders = (1 to nBidders).map(n => context.actorOf(Props[Bidder],s"bidder$n")).toList

      bidders.foreach { b => b  ! LookAtDescriptions(descriptions.toList) }

  }
}
