import _root_.Auction._
import _root_.BidderInterest._
import akka.actor.{Actor, ActorRef}
import akka.event.LoggingReceive

import scala.util.Random

object BidderInterest {
  case class ShouldIBid(price: BigDecimal)
  case class CanBid(maxAddition: BigDecimal)
  case class CantBid()
  case class Overbid(ammount: BigDecimal)
}

class BidderInterest(val parent: ActorRef, val myAuction: ActorRef) extends Actor{

  var knownPrice: BigDecimal = 0
  var myBid: BigDecimal = 0

  myAuction ! AskPrice

  def receive = LoggingReceive {
    case PriceInfo(price) if sender == myAuction =>
      this.knownPrice == price
      parent ! ShouldIBid
    case CanBid(maxOverbid) if sender == parent => bid(maxOverbid)
    case CantBid() if sender == parent =>
    case l@Lost =>
      parent ! l
      context.become(finished)
  }

  private def bid(bidAmmount: BigDecimal): Unit = {
    myBid = bidAmmount
    myAuction ! bid(myBid)
    context.become(waitingForBidResult)
  }

  def waitingForBidResult = LoggingReceive {
    case BidAck =>
      knownPrice = myBid
      context.become(winning)
    case BidNack =>
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
