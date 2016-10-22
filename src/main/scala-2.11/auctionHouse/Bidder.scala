package auctionHouse

import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive
import auctionHouse.Auction.{Lost, Won}
import auctionHouse.AuctionSearch.{SearchResult, Find}

import scala.collection.mutable.ListBuffer
import scala.util.Random
import auctionHouse.AuctionHouse.{AuctionList, LookAtDescriptions}
import tools.ActorTools.ReadableActorRef

object Bidder {

  val maxBidRatio = 2.0
  val maxBidRatioVar = 2.0

  val bidProbability = 1.0
  val bidRatioMin = 1.05
  val bidRatioMax = 1.10
}

class Bidder extends Actor with akka.actor.ActorLogging{

  import BidderInterest._
  import Bidder._
  import AuctionHouse._
  import AuctionSearch._

  def randomBidRatio = bidRatioMin+Random.nextDouble*(bidRatioMax-bidRatioMin)

  var neededItems = Math.abs(Random.nextInt().toDouble)%10+1
  var budgetLeft = BigDecimal(Random.nextDouble())*10000

  var interests = ListBuffer[ActorRef]()

  private def needMore: Boolean = neededItems > 0

  private def isViableInvestment(price: BigDecimal)(implicit maxProfitablePrice: BigDecimal) : Boolean = {
    needMore && canAfford(price)
  }

  private def canAfford(price: BigDecimal)(implicit maxProfitablePrice: BigDecimal): Boolean =
    isInBudget(price) && isProfitable(price)

  private def isInBudget(price: BigDecimal): Boolean = {
    price <= budgetLeft
  }

  private def isProfitable(price: BigDecimal)(implicit maxProfitablePrice: BigDecimal): Boolean = {
    price <= budgetLeft
  }
  
  private def considerBidding(interest: ActorRef, price: BigDecimal)(implicit profitableTo: BigDecimal) = {
    val investment = price*randomBidRatio
    if (isViableInvestment(price) && Random.nextDouble() < bidProbability) {
      neededItems -= 1
      budgetLeft -= investment
      interest ! CanBid(investment)
    } else {
      interest ! CantBid
    }
  }

  private def acknowledgeOverbid(returned: BigDecimal) = {
    neededItems += 1
    budgetLeft += returned
  }

  private def lookAtDescriptions(desc: List[AuctionDescription]) = {
    desc.foreach { d =>
      val searcher = context.actorOf(Props[AuctionSearch],s"searcher-${d.title}")
      searcher ! Find(d.title)
    }
  }

  private def spawnInterest(auction: ActorRef): ActorRef = {
    context.actorOf(Props(new BidderInterest(self,auction)))
  }

  private def spawnInterests(auctions: List[ActorRef]) = {
    println(s"Bidder ${self.name} looking at ${auctions.length} auctions")
    val additions = auctions.map(a => spawnInterest(a))
    interests ++= additions
  }

  private def acknowledgeWinning(interest: ActorRef): Unit = {
    interests -= interest
  }

  private def acknowledgeLoosing(interest: ActorRef): Unit = {
    interests -= interest
  }

  def receive = LoggingReceive {
    case LookAtDescriptions(desc) => lookAtDescriptions(desc)
    case SearchResult(keyword,auction) => spawnInterest(auction)
    case ShouldIBid(price,profitableTo) => considerBidding(sender, price)(implicitly(profitableTo))
    case Overbid(returned) => acknowledgeOverbid(returned)
    case Won(price) => acknowledgeWinning(sender)
    case Lost() => acknowledgeLoosing(sender)
  }

}
