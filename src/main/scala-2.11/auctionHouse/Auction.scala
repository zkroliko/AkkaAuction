package auctionHouse


import java.util.concurrent.TimeUnit

import akka.actor._
import akka.event.LoggingReceive
import auctionHouse.Auction.AskPrice
import com.github.nscala_time.time.Imports._


import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration

object Auction {

  case object Start
  case object AskPrice
  case class PriceInfo(current : BigDecimal)
  case class Bid(proposed : BigDecimal)
  case class Closed()
  case class Inactive()
  trait BidResult
  case class BidAck(accepted : BigDecimal) extends BidResult
  case class BidNack(price : BigDecimal) extends BidResult
  trait ParticipationResult
  case class Won(price : BigDecimal) extends ParticipationResult
  case class Lost() extends ParticipationResult
  // For timer
  case class BidTimerExpired(time : DateTime)
  object DeleteTimerExpired

}

class Auction(val item: String, startingPrice: BigDecimal) extends Actor {
  import Auction._

  val bidWaitDuration = Duration.create(5,TimeUnit.SECONDS)
  val ignoredDuration = Duration.create(15,TimeUnit.SECONDS)


  var seller: Option[ActorRef] = None
  var currentPrice: BigDecimal = startingPrice
  var endTime: DateTime = DateTime.now + ignoredDuration.toSeconds

  val system = ActorSystem("timingSystem")
  import system.dispatcher

  val interested = ListBuffer[ActorRef]()
  val bidders = ListBuffer[ActorRef]()

  private def informInterested(): Unit = {
    interested.foreach(_ ! PriceInfo(currentPrice))
  }

  private def checkBid(price: BigDecimal): BidResult = {
    if (price > currentPrice && DateTime.now < endTime) {
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

  private def restartTimer() = {
    endTime = DateTime.now+ignoredDuration.toSeconds
    system.scheduler.scheduleOnce(bidWaitDuration,self,BidTimerExpired(endTime))
  }

  private def startDeleteTimer() = {
    system.scheduler.scheduleOnce(ignoredDuration,self,DeleteTimerExpired)

  }

  def receive = LoggingReceive {
    case Start =>
      seller = Some(sender)
      restartTimer()
      println(s"Auction for: $item for $currentPrice from seller $seller started, and will end at $endTime")
      informInterested()
      context.become(created())
    case AskPrice => informOfPrice(sender)
    case _ => sender ! Inactive
  }

  def created(): Receive = LoggingReceive {
    case Bid(proposed) =>
      restartTimer()
      sender ! checkBid(proposed)
      interested += sender
      informInterested()
      println(s"Auction $this activated")
      context.become(activated(proposed))
    case BidTimerExpired(time) => if (time == endTime) {
      println(s"Timer expired for $this at $endTime, item ignored")
      startDeleteTimer()
      context.become(ignored())
    }
    case AskPrice => informOfPrice(sender)
  }

  def activated(current : BigDecimal): Receive = LoggingReceive {
    case Bid(proposed) =>
      restartTimer()
      sender ! checkBid(proposed)
      informInterested()
    case BidTimerExpired(time) => if (time == endTime) {
      println(s"Timer expired for $this at $endTime, item sold to: ")
      context.become(sold(currentPrice))
    }
    case AskPrice => informOfPrice(sender)
  }

  def ignored(): Receive = LoggingReceive {
    case Bid(proposed) =>
      interested += sender
      sender ! Closed
    case DeleteTimerExpired =>
      println(s"Delete timer expired for $this at ${DateTime.now()}, auction deleted")
      context.stop(self)
    case _ =>
      interested += sender
      sender ! Inactive
  }

  def sold(endPrice : BigDecimal): Receive = LoggingReceive {
    case _ => sender ! Closed
  }

}
