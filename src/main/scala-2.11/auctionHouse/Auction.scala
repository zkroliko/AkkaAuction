package auctionHouse


import java.util.concurrent.TimeUnit

import akka.actor._
import auctionHouse.Auction._
import auctionHouse.AuctionHouse.ReadableActorRef
import com.github.nscala_time.time.Imports._
import sun.plugin.dom.exception.InvalidStateException

import scala.collection.immutable.SortedSet
import scala.concurrent.duration.Duration

object Auction {

  val bidWaitDuration = Duration.create(5,TimeUnit.SECONDS)
  val ignoredDuration = Duration.create(15,TimeUnit.SECONDS)

  case object Start
  case object AskingForInfo
  case object Closed
  case object Inactive

  case class Info(current : BigDecimal, leader: Option[ActorRef])
  case class Bid(proposed : BigDecimal)

  trait BidResult
  case class BidAck(bid: Bid) extends BidResult
  case class BidNAck(bid: Bid) extends BidResult

  trait ParticipationResult
  case class Won(price : BigDecimal) extends ParticipationResult
  case class Lost() extends ParticipationResult

  // For timer
  case class BidTimerExpired(time : DateTime)
  object DeleteTimerExpired

  sealed trait State
  case object Idle extends State
  case object Created extends State
  case object Ignored extends State
  case object Activated extends State
  case object Sold extends State

  sealed trait Data
  final case class Uninitialized(startingPrice: BigDecimal,interested: SortedSet[ActorRef]) extends Data
  final case class WaitingData(startingPrice: BigDecimal, interested: SortedSet[ActorRef],
                               endTime: DateTime) extends Data
  final case class BiddingData(price: BigDecimal, interested: SortedSet[ActorRef], endTime: DateTime,
                               currentWinner: ActorRef) extends Data
  final case class IgnoredData(price: BigDecimal, interested: SortedSet[ActorRef]) extends Data
  final case class SoldData(endPrice: BigDecimal, winner: ActorRef) extends Data
}

class Auction(val title : String, val startingPrice: BigDecimal) extends FSM[State, Data] {
  import Auction._

  val system = context.system
  import system.dispatcher

  private def informInterested(interested: SortedSet[ActorRef], price: BigDecimal, winning: Option[ActorRef]): Unit = {
    interested.foreach(_ ! Info(price,winning))
  }

  private def processedBid(bid: Bid, currentPrice: BigDecimal, sender: ActorRef): BidResult = {
    val proposed = bid.proposed
    if (proposed > currentPrice) {
      println(s"Valid bid of '$proposed' > '$currentPrice' placed on: ${self.id}: at ${DateTime.now}")
      BidAck(bid)
    } else {
      println(s"Too low bid of '$proposed' placed on: ${self.id} at '$currentPrice' at ${DateTime.now}")
      BidNAck(bid)
    }
  }

  private def informOfResult(interested: SortedSet[ActorRef], currentWinner: Option[ActorRef], price: BigDecimal) {
    currentWinner.getOrElse(throw new InvalidStateException("")) ! Won(price)
    interested.filter(_ != currentWinner.getOrElse(None)).foreach {
      _ ! Lost
    }
  }

  private def restartedTimer(): DateTime = {
    val endTime = DateTime.now+ignoredDuration.toSeconds
    system.scheduler.scheduleOnce(bidWaitDuration,self,BidTimerExpired(endTime))
    endTime
  }

  private def startDeleteTimer() = {
    system.scheduler.scheduleOnce(ignoredDuration,self,DeleteTimerExpired)

  }


  startWith(Idle,Uninitialized(startingPrice,SortedSet()))

  when(Idle) {
    case Event(Start, Uninitialized(price,interested)) =>
      val endTime = restartedTimer()
      println(s"Auction ${self.id} for $price created, and will end at $endTime")
      informInterested(interested,price,None)
      goto(Created) using WaitingData(price,interested,endTime)
    case Event(AskingForInfo, u: Uninitialized) =>
      sender ! Info(u.startingPrice,None)
      stay() using u.copy (interested = u.interested+sender)
  }

  when(Created) {
    case Event(bid@Bid(proposed), WaitingData(currentPrice, interested, endTime)) =>
      val result = processedBid(bid, currentPrice, sender)
      sender ! result
      result match {
        case BidAck(value) =>
          println(s"Auction ${self.id} activated")
          informInterested(interested,currentPrice,Some(sender))
          goto(Activated) using BiddingData(proposed, interested + sender, restartedTimer(), sender)
        case BidNAck(value) =>
          stay() using WaitingData(currentPrice, interested + sender, endTime)
      }
    case Event(BidTimerExpired(time), WaitingData(price, interested, endTime)) =>
      if (time == endTime) {
        println(s"Timer expired for ${self.id} at $endTime, item ignored")
        startDeleteTimer()
        goto(Ignored)
      } else {
        stay()
      }
    case Event(_, WaitingData(price, interested, endTime)) =>
      sender ! Info(price, None)
      stay() using WaitingData(price, interested + sender, endTime)
  }

  when(Activated) {
    case Event(bid@Bid(proposed), BiddingData(currentPrice, interested, endTime, previousLeader)) =>
      val result = processedBid(bid, currentPrice, sender)
      sender ! result
      result match {
        case BidAck(value) =>
          informInterested(interested,currentPrice,Some(sender))
          goto(Activated) using BiddingData(proposed, interested + sender, endTime, sender)
        case BidNAck(value) =>
          stay() using BiddingData(currentPrice, interested + sender, endTime, previousLeader)
      }
    case Event(BidTimerExpired(time), BiddingData(endPrice, interested, endTime, winner)) =>
      if (time == endTime) {
        informOfResult(interested,Some(winner),endPrice)
        println(s"Timer expired for ${self.id}, item sold for '$endPrice' at ${DateTime.now}")
        goto(Sold) using SoldData(endPrice,winner)
      } else {
        stay()
      }
    case Event(_, BiddingData(price, interested, endTime, currentLeader)) =>
      sender ! Info(price, Some(currentLeader))
      stay() using BiddingData(price, interested + sender, endTime, currentLeader)
  }

  when(Ignored) {
    case Event(Start,IgnoredData(price,interested)) =>
      val endTime = restartedTimer()
      goto(Created) using WaitingData(price,interested,endTime)
    case Event(DeleteTimerExpired,IgnoredData(price,interested)) =>
      println(s"Delete timer expired for ${self.id} at ${DateTime.now()}, auction deleted")
      stay()
    case Event(_,Uninitialized(price,interested)) =>
      sender ! Info(price,None)
      stay() using Uninitialized(price,interested+sender)
  }

  when(Sold) {
    case Event(_,SoldData(price,winner)) =>
      sender ! Info(price,Some(winner))
      stay()
  }

  initialize()

}
