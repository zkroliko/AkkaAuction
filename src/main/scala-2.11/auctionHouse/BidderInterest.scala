package auctionHouse

import akka.actor.{Actor, ActorRef}
import akka.event.LoggingReceive
import auctionHouse.BidderInterest.{CanBid, CantBid, Overbid, ShouldIBid}
import tools.ActorTools.ReadableActorRef

import scala.util.Random


object BidderInterest {
  case class ShouldIBid(price: BigDecimal, maxPrice: BigDecimal)
  case class CanBid(maxAddition: BigDecimal)
  case class CantBid()
  case class Overbid(amount: BigDecimal)
}

class BidderInterest(parent_ : ActorRef, val myAuction: ActorRef) extends Actor {

  import Auction._
  import Bidder._

  val parent = parent_

  var knownPrice: BigDecimal = 0
  var myBid: BigDecimal = 0
  var maxBid: BigDecimal = 0

  myAuction ! AskingForInfo

  private def processInitialInfo(info: Info) = {
    maxBid = info.current*(maxBidRatio+maxBidRatioVar*Random.nextDouble())
    processInfo(info)
  }

  private def processInfo(info: Info) = {
    this.knownPrice == info.current
    if (info.leader.getOrElse(None) != self)
      parent ! ShouldIBid(info.current,maxBid)
  }

  private def bid(bidAmount: BigDecimal): Unit = {
    myBid = bidAmount
    myAuction ! Bid(myBid)
    println(f"Bidder ${parent.name} decided to bid on auction ${myAuction.name} for '$bidAmount%1.2f'")
    context.become(waitingForBidResult)
  }

  def receive = LoggingReceive {
    case info : Info if (sender == myAuction) && info.leader.isEmpty =>
      processInitialInfo(info)
      context.become(engaged)
    case l@Lost() => // already lost
      parent ! l
      context.become(finished)
  }

  def engaged = LoggingReceive {
    case info : Info if sender == myAuction =>
      processInfo(info)
    case CanBid(bidValue) if sender == parent => bid(bidValue)
    case CantBid() if sender == parent =>
    case l@Lost() =>
      parent ! l
      context.become(finished)
  }

  def waitingForBidResult = LoggingReceive {
    case BidAck(bid) =>
      knownPrice = myBid
      context.become(winning)
    case BidNAck(bid) =>
      parent ! Overbid(myBid)
      context.become(engaged)
    case l@Lost() =>
      parent ! Overbid(myBid)
      parent ! l
      context.become(finished)
  }

  def winning = LoggingReceive {
    case info @ Info(price, leader) if sender == myAuction && leader.getOrElse(None) != self =>
//      println(s"Bidder ${parent.name} overbid at ${sender.name} for $price by ${leader.get.name} ")
      parent ! Overbid(myBid)
      processInfo(info)
      context.become(engaged)
    case w@Won(finalPrice) if sender == myAuction =>
      println(f"Bidder ${parent.name} WON ${sender.name} for '$finalPrice%1.2f'")
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
