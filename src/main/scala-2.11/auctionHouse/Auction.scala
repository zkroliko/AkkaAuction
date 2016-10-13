package auctionHouse


import akka.actor._
import akka.event.LoggingReceive
import com.github.nscala_time.time.Imports._
import org.joda.time.DateTime

import scala.collection.mutable.ListBuffer

object Auction {

  case class Start(end: DateTime)
  case class AskPrice()
  case class PriceInfo(current : BigDecimal)
  case class ClosingInfo(time: DateTime)
  case class Bid(proposed : BigDecimal)
  case class Closed()
  case class Inactive()
  trait BidResult
  case class BidAck(accepted : BigDecimal) extends BidResult
  case class BidNack(price : BigDecimal) extends BidResult
  trait ParticipationResult
  case class Won(price : BigDecimal) extends ParticipationResult
  case class Lost() extends ParticipationResult

}

class Auction(val item: String, startingPrice: BigDecimal) extends Actor {
  import Auction._

  var seller: Option[ActorRef] = None
  var currentPrice: BigDecimal = startingPrice
  var endDate: DateTime = DateTime.now + 1000.years

  val interested = ListBuffer[ActorRef]()
  val bidders = ListBuffer[ActorRef]()

  private def informInterested(): Unit = {
    interested.foreach(_ ! PriceInfo(currentPrice))
  }

  private def checkBid(price: BigDecimal): BidResult = {
    if (price > currentPrice && DateTime.now < endDate) {
      println(s"Valid bid placed of $price over $currentPrice on: $item: at ${DateTime.now}")
      currentPrice = price
      BidAck(price)
    } else {
      println(s"Invalid bid placed of $price on: $item: $currentPrice at ${DateTime.now}")
      BidNack(currentPrice)
    }
  }

  private def informOfPrice(sender: ActorRef) = {
    interested += sender
    sender ! PriceInfo(currentPrice)
  }

  def receive = LoggingReceive {
    case Start(end) =>
      seller = Some(sender)
      endDate = end
      println(s"Auction for: $item for $currentPrice from seller $seller started, and will end at $endDate")
      context.become(created())
    case AskPrice =>
      interested += sender
      sender ! Inactive
    case _ => sender ! Inactive
  }

  def created(): Receive = LoggingReceive {
    case Bid(proposed) =>
      sender ! checkBid(proposed)
      interested += sender
      informInterested()
      context.become(activated(proposed))
    case AskPrice => informOfPrice(sender)
    case ClosingInfo =>
      interested += sender
      sender ! ClosingInfo
  }

  def activated(current : BigDecimal): Receive = LoggingReceive {
    case Bid(proposed) =>
      sender ! checkBid(proposed)
      informInterested()
    case AskPrice => informOfPrice(sender)
    case ClosingInfo =>
      interested += sender
      sender ! ClosingInfo
  }

  def ignored(starting : BigDecimal): Receive = LoggingReceive {
    case Bid(proposed) =>
      interested += sender
      sender ! Closed
    case _ =>
      interested += sender
      sender ! Inactive
  }

  def sold(endPrice : BigDecimal): Receive = LoggingReceive {
    case Bid(proposed) => sender ! Closed
    case _ =>
      sender ! Closed
  }

}
