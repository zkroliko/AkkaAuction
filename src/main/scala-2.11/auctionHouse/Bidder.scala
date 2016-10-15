package auctionHouse

import akka.actor.{Actor, ActorRef, Props}
import akka.event.{Logging, LoggingReceive}
import auctionHouse.Auction.{Lost, Won}

import scala.collection.mutable.ListBuffer
import scala.util.Random

class Bidder extends Actor with akka.actor.ActorLogging{

  import BidderInterest._

  val bidProbability = 1.0
  val bidRatio = 1.05

  var neededItems = Math.abs(Random.nextInt().toDouble)%10+1
  var budgetLeft = BigDecimal(Random.nextDouble())*10000

  var interests = ListBuffer[ActorRef]()

  private def needMore: Boolean = neededItems > 0

  private def canAfford(price: BigDecimal): Boolean = price*bidRatio <= budgetLeft

  private def considerBidding(interest: ActorRef, price: BigDecimal) = {
    if (needMore && canAfford(price) && Random.nextDouble() < bidProbability) {
      neededItems -= 1
      val invested = price*bidRatio
      budgetLeft -= invested
      interest ! CanBid(invested)
    } else {
      interest ! CantBid
    }
  }

  private def acknowledgeOverbid(returned: BigDecimal) = {
    neededItems += 1
    budgetLeft += returned
  }

  private def spawnInterests(auctions: List[ActorRef]) = {
    println(s"Bidder $self looking at ${auctions.length} auctions")
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
