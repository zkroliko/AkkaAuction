package auctionHouse.routingBench

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive
import akka.routing.{ScatterGatherFirstCompletedRoutingLogic, RoundRobinRoutingLogic}
import auctionHouse.Seller.BuildFromDescriptions
import auctionHouse.search.MasterSearch
import auctionHouse.{AuctionDescription, Bidder, Seller}
import org.joda.time.{DateTime, Period}
import scala.concurrent.duration.Duration


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

  var startTime : DateTime = null
  var afterReg: DateTime = null
  var afterSearch: DateTime = null

  def timeToReg = new Period(startTime,afterReg)
  def timeToSearch = new Period(afterReg,afterSearch)

  var searchCount = 0

  val within = Duration.create(5, TimeUnit.SECONDS)

  val registration = context.actorOf(MasterSearch.props(8,ScatterGatherFirstCompletedRoutingLogic(within),self),"auctionSearch")
  val sellers = (1 to nSellers).map{n => context.actorOf(Props[Seller],"seller"+n)}
  val descriptions = (1 to nAuctionsPerSeller).map(n => AuctionDescription("item"+n.toString,200.0+n))
  val bidder = context.actorOf(Props(new Bidder(self)),s"bidder")

  def receive = LoggingReceive {
    case Init =>
      startTime = DateTime.now()
      sellers.foreach{s => s ! BuildFromDescriptions(descriptions.toList)}
      context.become(countRegistrations)
  }

  def countRegistrations = LoggingReceive {
    case RegistrationFinished =>
      afterReg = DateTime.now
      println("Registration finished!")
      println("Registration took: " + timeToReg)
      println("Registration finished! Commencing buyer searches!")
      bidder ! LookAtDescriptions(descriptions.toList.take(10000))
      context.become(countSearches)
  }

  def countSearches = LoggingReceive {
    case SearchFinished =>
      searchCount += 1
      println(searchCount + " searches Finished")
      if (searchCount >= 10000) {
        println("Searches finished")
        afterSearch = DateTime.now
        println("Registration took: " + timeToReg)
        println("Searches took: " + timeToSearch)
        context.stop(self)
      }
  }
}
