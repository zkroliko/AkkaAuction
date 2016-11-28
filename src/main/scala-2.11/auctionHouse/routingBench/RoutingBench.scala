package auctionHouse.routingBench

import akka.actor.{ActorRef, Actor, Props}
import akka.event.LoggingReceive
import akka.routing.RoundRobinRoutingLogic
import auctionHouse.Seller.BuildFromDescriptions
import auctionHouse.search.MasterSearch
import auctionHouse.{AuctionDescription, AuctionHouse, Bidder, Seller}

object RoutingBench {
  case object Init
  case class AuctionList(auctions: List[ActorRef])
  case class LookAtDescriptions(descriptions: List[AuctionDescription])
  case object RegistrationFinished
  case object SearchFinished

}


class RoutingBench extends Actor{
  import RoutingBench._

  val nSellers = 1
  val nAuctionsPerSeller = 50000
  val nBidders = 1

  var searchCount = 0

  val registration = context.actorOf(MasterSearch.props(1,RoundRobinRoutingLogic(),self),"auctionSearch")
  val sellers = (1 to nSellers).map{n => context.actorOf(Props[Seller],"seller"+n)}
  val descriptions = (1 to nAuctionsPerSeller).map(n => AuctionDescription("item"+n.toString,200.0+n))
  val bidders = (1 to nBidders).map(n => context.actorOf(Props(new Bidder(self)),s"bidder$n")).toList

  def receive = LoggingReceive {
    case Init =>
      sellers.foreach{s => s ! BuildFromDescriptions(descriptions.toList)}
      context.become(countRegistrations)
  }

  def countRegistrations = LoggingReceive {
    case RegistrationFinished =>
      println("Registration finished! Commencing buyer searches!")
      bidders.foreach { b => b  ! LookAtDescriptions(descriptions.toList.take(10000)) }
      context.become(countSearches)
  }

  def countSearches = LoggingReceive {
    case SearchFinished =>
      searchCount += 1
      println(searchCount + " searchesFinished")
      if (searchCount >= 10000) {

      }
  }
}
