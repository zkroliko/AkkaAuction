package auctionHouse

import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive
import auctionHouse.Auction.{Lost, Won}

import scala.collection.mutable.ListBuffer
import scala.util.Random
import auctionHouse.AuctionHouse.ReadableActorRef

class Bidder extends Actor with akka.actor.ActorLogging{

  import BidderInterest._

  val bidProbability = 0.8
  val bidRatioMin = 1.05
  val bidRatioMax = 1.10

  def randomBidRatio = bidRatioMin+Random.nextDouble*(bidRatioMax-bidRatioMin)

  var neededItems = Math.abs(Random.nextInt().toDouble)%10+1
  var budgetLeft = BigDecimal(Random.nextDouble())*10000

  var interests = ListBuffer[ActorRef]()

  private def needMore: Boolean = neededItems > 0

  private def canAfford(price: BigDecimal): Boolean = price <= budgetLeft

  private def considerBidding(interest: ActorRef, price: BigDecimal) = {
    val investment = price*randomBidRatio
    if (needMore && canAfford(investment) && Random.nextDouble() < bidProbability) {
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

  private def spawnInterests(auctions: List[ActorRef]) = {
    println(s"Bidder ${self.id} looking at ${auctions.length} auctions")
    val additions = auctions.map(a => context.actorOf(Props(new BidderInterest(self,a))))
    interests ++= additions
  }

  private def acknowledgeWinning(interest: ActorRef): Unit = {
    interests -= interest
  }

  private def acknowledgeLoosing(interest: ActorRef): Unit = {
    interests -= interest
  }

  def receive = LoggingReceive {
    case AuctionHouse.AuctionList(positions) => spawnInterests(positions)
    case ShouldIBid(price) => considerBidding(sender, price)
    case Overbid(returned) => acknowledgeOverbid(returned)
    case Won(price) => acknowledgeWinning(sender)
    case Lost() => acknowledgeLoosing(sender)
  }

}
