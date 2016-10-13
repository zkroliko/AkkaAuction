package auctionHouse

import akka.actor.{Actor, ActorRef}
import akka.event.{Logging, LoggingReceive}
import auctionHouse.Auction.{Lost, PriceInfo, AskPrice}
import auctionHouse.BidderInterest.{Overbid, CantBid, CanBid, ShouldIBid}

object BidderInterest {
  case class ShouldIBid(price: BigDecimal)
  case class CanBid(maxAddition: BigDecimal)
  case class CantBid()
  case class Overbid(amount: BigDecimal)
}

class BidderInterest(val parent: ActorRef, val myAuction: ActorRef) extends Actor{

  val log = Logging(context.system, this)

  import Auction._

  var knownPrice: BigDecimal = 0
  var myBid: BigDecimal = 0

  myAuction ! AskPrice

  def receive = LoggingReceive {
    case PriceInfo(price) if sender == myAuction =>
      this.knownPrice == price
      parent ! ShouldIBid(price)
    case CanBid(maxOverbid) if sender == parent => bid(maxOverbid)
    case CantBid() if sender == parent =>
    case l@Lost =>
      parent ! l
      context.become(finished)
  }

  private def bid(bidAmount: BigDecimal): Unit = {
    myBid = bidAmount
    myAuction ! Bid(myBid)
    println(s"Bidder $self decided to bid on $myAuction for $bidAmount")
    context.become(waitingForBidResult)
  }

  def waitingForBidResult = LoggingReceive {
    case BidAck(price) =>
      knownPrice = myBid
      context.become(winning)
    case BidNack(price) =>
      parent ! Overbid(myBid)
      context.become(receive)
    case l@Lost =>
      parent ! Overbid(myBid)
      parent ! l
      context.become(finished)
  }

  def winning = LoggingReceive {
    case PriceInfo(price) if sender == myAuction =>
      this.knownPrice == price
      parent ! Overbid(myBid)
      context.become(receive)
    case w@Won =>
      parent ! w
      context.become(finished)
  }

  def finished = LoggingReceive {
    case _ =>
  }

}