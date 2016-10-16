package auctionHouse

import akka.actor.{Actor, ActorRef}
import akka.event.LoggingReceive
import auctionHouse.BidderInterest.{CanBid, CantBid, Overbid, ShouldIBid}
import auctionHouse.AuctionHouse.ReadableActorRef


object BidderInterest {
  case class ShouldIBid(price: BigDecimal)
  case class CanBid(maxAddition: BigDecimal)
  case class CantBid()
  case class Overbid(amount: BigDecimal)
}

class BidderInterest(parent_ : ActorRef, val myAuction: ActorRef) extends Actor {

  import Auction._

  val parent = parent_

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
    case l@Lost() =>
      parent ! l
      context.become(finished)
  }

  private def bid(bidAmount: BigDecimal): Unit = {
    myBid = bidAmount
    myAuction ! Bid(myBid)
    println(s"Bidder ${parent.id} decided to bid on auction ${myAuction.id} for '$bidAmount'")
    context.become(waitingForBidResult)
  }

  def waitingForBidResult = LoggingReceive {
    case BidAck(bid) =>
      knownPrice = myBid
      context.become(winning)
    case BidNAck(bid) =>
      parent ! Overbid(myBid)
      context.become(receive)
    case l@Lost() =>
      parent ! Overbid(myBid)
      parent ! l
      context.become(finished)
  }

  def winning = LoggingReceive {
    case Info(price, winner) if sender == myAuction && winner.getOrElse(None) != self =>
      this.knownPrice == price
      parent ! Overbid(myBid)
      context.become(receive)
    case w@Won(finalPrice) if sender == myAuction =>
      println(s"Bidder ${parent.id} WON ${sender.id} for '$finalPrice'")
      parent ! w
      context.become(finished)
    case l@Lost() =>
      parent ! Overbid(myBid)
      parent ! l
      context.become(finished)
  }

  def finished = LoggingReceive {
    case _ =>
  }
}
