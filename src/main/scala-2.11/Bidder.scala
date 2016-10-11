import _root_.Auction.{Bid, PriceInfo, Lost, Won}
import akka.actor.{Props, ActorRef, Actor}
import akka.event.LoggingReceive

import scala.collection.mutable.ListBuffer
import scala.util.Random

class Bidder extends Actor{

  import BidderInterest._

  val buyProbability = 0.2
  val bidRatio = 1.05

  var neededItems = Random.nextInt()%10
  var budgetLeft = BigDecimal(Random.nextDouble())*10000

  var interests = ListBuffer[ActorRef]()

  private def needMore: Boolean = neededItems > 0

  private def canAfford(price: BigDecimal): Boolean = price*bidRatio <= budgetLeft

  private def considerBidding(interest: ActorRef, price: BigDecimal) = {
    if (needMore && canAfford(price) && Random.nextDouble() < buyProbability) {
      neededItems -= 1
      val invested = price*bidRatio
      budgetLeft -= invested
      sender ! CanBid(invested)
    } else {
      sender ! CantBid
    }
  }

  private def acknowledgeOverbid(returned: BigDecimal) = {
    neededItems += 1
    budgetLeft += returned
  }

  private def spawnInterests(auctions: List[ActorRef]) = {
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
    case AuctionHouse.AuctionList(positions) =>
    case ShouldIBid(price) => considerBidding(sender, price)
    case Overbid(returned) => acknowledgeOverbid(returned)
    case Won(price) => acknowledgeWinning(sender)
    case Lost() => acknowledgeLoosing(sender)
  }

}
