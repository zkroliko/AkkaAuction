package auctionHouse


import java.util.concurrent.TimeUnit

import akka.actor._
import auctionHouse.Auction._
import auctionHouse.search.AuctionSearch
import auctionHouse.search.AuctionSearch.{Unregister, Register, RegistrationMessage}
import com.github.nscala_time.time.Imports._
import sun.plugin.dom.exception.InvalidStateException
import tools.ActorTools.ReadableActorRef
import tools.TimeTools

import scala.collection.immutable.SortedSet
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

object Auction {

  val registerTimeout = Duration.create(1,TimeUnit.SECONDS)
  val bidWaitDuration = Duration.create(50,TimeUnit.SECONDS)
  val ignoredDuration = Duration.create(10,TimeUnit.SECONDS)

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

  case class KnowThatSold(price : BigDecimal, winner: ActorRef)
  case object KnowThatNotSold

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
  final case class WaitingData(seller: ActorRef, startingPrice: BigDecimal, interested: SortedSet[ActorRef],
                               endTime: DateTime) extends Data
  final case class BiddingData(seller: ActorRef, price: BigDecimal, interested: SortedSet[ActorRef], endTime: DateTime,
                               currentWinner: ActorRef) extends Data
  final case class IgnoredData(seller: ActorRef, price: BigDecimal, interested: SortedSet[ActorRef]) extends Data
  final case class SoldData(seller: ActorRef, endPrice: BigDecimal, winner: ActorRef) extends Data
}

class Auction(description: AuctionDescription) extends FSM[State, Data] {
  import Auction._

  val title : String = description.title
  val startingPrice: BigDecimal = description.price

  val system = context.system
  import system.dispatcher

  private def messageToRegistration(msg: RegistrationMessage) {
    context.actorSelection(AuctionSearch.path).resolveOne(registerTimeout).onComplete {
      case Success(searcher) => searcher ! msg
      case Failure(ex) => println("Searcher not found")
    }
  }

  private def register() = {
      messageToRegistration(Register(title))

  }

  private def unregister() = {
    messageToRegistration(Unregister(title))

  }

  private def informInterested(interested: SortedSet[ActorRef], price: BigDecimal, winning: Option[ActorRef]): Unit = {
    interested.foreach(_ ! Info(price,winning))
  }

  private def processedBid(bid: Bid, currentPrice: BigDecimal, sender: ActorRef): BidResult = {
    val proposed = bid.proposed
    if (proposed > currentPrice) {
//      println(f"Valid bid of '$proposed%1.2f' > '$currentPrice%1.2f' placed on: ${self.name}: at ${TimeTools.timeNow}")
      BidAck(bid)
    } else {
//      println(s"Too low bid of '$proposed' placed on: ${self.name} at '$currentPrice' at ${TimeTools.timeNow}")
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

  register()
  startWith(Idle,Uninitialized(startingPrice,SortedSet()))

  when(Idle) {
    case Event(Start, Uninitialized(price,interested)) =>
      val endTime = restartedTimer()
//      println(f"Auction ${self.name} for $price%1.2f created, and will end at ${TimeTools.timeFormatted(endTime)}")
      informInterested(interested,price,None)
      goto(Created) using WaitingData(sender,price,interested,endTime)
    case Event(AskingForInfo, u: Uninitialized) =>
      sender ! Info(u.startingPrice,None)
      stay() using u.copy (interested = u.interested+sender)
  }

  when(Created) {
    case Event(bid@Bid(proposed), data @ WaitingData(seller, currentPrice, interested, endTime)) =>
      val result = processedBid(bid, currentPrice, sender)
      sender ! result
      result match {
        case BidAck(value) =>
//          println(s"Auction ${self.name} activated")
          informInterested(interested,currentPrice,Some(sender))
          goto(Activated) using BiddingData(seller, proposed, interested + sender, restartedTimer(), sender)
        case BidNAck(value) =>
          stay() using data.copy(interested = interested + sender)
      }
    case Event(BidTimerExpired(time), data : WaitingData) =>
      if (time == data.endTime) {
//        println(s"Timer expired for ${self.name} at ${TimeTools.timeFormatted(data.endTime)}, item ignored")
        startDeleteTimer()
        goto(Ignored) using IgnoredData(data.seller,data.startingPrice,data.interested)
      } else {
        stay()
      }
    case Event(_, data : WaitingData) =>
      sender ! Info(data.startingPrice, None)
      stay() using data.copy(interested = data.interested + sender)
  }

  when(Activated) {
    case Event(bid@Bid(proposed), data @ BiddingData(seller,currentPrice, interested, endTime, previousLeader)) =>
      val result = processedBid(bid, currentPrice, sender)
      sender ! result
      result match {
        case BidAck(value) =>
          informInterested(interested,currentPrice,Some(sender))
          goto(Activated) using BiddingData(seller, proposed, interested + sender, endTime, sender)
        case BidNAck(value) =>
          stay() using data.copy(interested = interested + sender)
      }
    case Event(BidTimerExpired(time), BiddingData(seller,endPrice, interested, endTime, winner)) =>
      if (time == endTime) {
        informOfResult(interested,Some(winner),endPrice)
//        println(f"Timer expired for ${self.name}, item sold for '$endPrice%1.2f' at ${TimeTools.timeNow}")
        goto(Sold) using SoldData(seller, endPrice,winner)
      } else {
        stay()
      }
    case Event(_, data : BiddingData) =>
      sender ! Info(data.price, Some(data.currentWinner))
      stay() using data.copy(interested = data.interested + sender)
  }

  when(Ignored) {
    case Event(Start,IgnoredData(seller, price,interested)) =>
      val endTime = restartedTimer()
      goto(Created) using WaitingData(seller, price,interested,endTime)
    case Event(DeleteTimerExpired,IgnoredData(seller, price,interested)) =>
//      println(s"Delete timer expired for ${self.name} at ${TimeTools.timeNow}, auction deleted")
      seller ! KnowThatNotSold
      unregister()
      stop(FSM.Normal)
    case Event(_,Uninitialized(price,interested)) =>
      sender ! Info(price,None)
      stay() using Uninitialized(price,interested+sender)
  }

  when(Sold) {
    case Event(_,SoldData(seller,price,winner)) =>
      sender ! Info(price,Some(winner))
      stop(FSM.Normal)
  }

  onTransition {
    case Activated -> Sold =>
      val data = nextStateData.asInstanceOf[SoldData]
      data.seller ! KnowThatSold(data.endPrice,data.winner)
      unregister()
  }

  initialize()

}
