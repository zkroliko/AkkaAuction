package auctionHouse

import akka.actor.{Actor, ActorRef}
import akka.event.{Logging, LoggingReceive}
import auctionHouse.Auction.{Lost, Info, AskForInfo}
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

  myAuction ! AskForInfo

  def receive = LoggingReceive {
    case Info(price, winner) if sender == myAuction =>
      this.knownPrice == price
      if (winner.getOrElse(None) != self)
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
    case BidAck(bid) =>
      knownPrice = myBid
      context.become(winning)
    case BidNack(bid) =>
      parent ! Overbid(myBid)
      context.become(receive)
    case l@Lost =>
      parent ! Overbid(myBid)
      parent ! l
      context.become(finished)
  }

  def winning = LoggingReceive {
    case Info(price, winner) if sender == myAuction && winner.getOrElse(None) != self =>
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
