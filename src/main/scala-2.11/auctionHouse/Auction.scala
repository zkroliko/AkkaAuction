package auctionHouse


import java.util.concurrent.TimeUnit

import akka.actor._
import akka.event.LoggingReceive
import auctionHouse.Auction.{Data, State}
import com.github.nscala_time.time.Imports._
import sun.plugin.dom.exception.InvalidStateException
import auctionHouse.AuctionHouse.ReadableActorRef

import scala.collection.immutable.SortedSet
import scala.collection.parallel
import scala.collection.parallel.immutable
import scala.concurrent.duration.Duration

object Auction {

  val bidWaitDuration = Duration.create(5,TimeUnit.SECONDS)
  val ignoredDuration = Duration.create(15,TimeUnit.SECONDS)

  final case class Start()
  case object AskForInfo
  case class Info(current : BigDecimal, leader: Option[ActorRef])
  case class Bid(proposed : BigDecimal)
  case object Closed
  case object Inactive
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

  final case class Uninitialized(startingPrice: BigDecimal,
                                 interested: SortedSet[ActorRef]
                                ) extends Data
  final case class Initialized(startingPrice: BigDecimal,
                                interested: SortedSet[ActorRef],
                               endTime: DateTime
                              ) extends Data

  final case class Bidding(price: BigDecimal,
                           interested: SortedSet[ActorRef],
                           endTime: DateTime,
                           currentWinner: ActorRef
                          ) extends Data
}

class Auction(startingPrice: BigDecimal) extends FSM[State, Data] {
  import Auction._

  startWith(Idle,Uninitialized(startingPrice,SortedSet()))

  val system = ActorSystem("timingSystem")
  import system.dispatcher

  private def start() {
    withRestartedTimer()
    println(s"Auction ${self.id} for $currentPrice started, and will end at $endTime")
    informInterested()
    goto(Created)
  }

  private def informInterested(): Unit = {
    interested.foreach(_ ! Info(currentPrice,currentWinner))
  }

  private def processedBid(bid: Bid, sender: ActorRef): BidResult = {
    val proposed = bid.proposed
    if (proposed > currentPrice) {
      println(s"Valid bid of '$proposed' > '$currentPrice' placed on: ${self.id}: at ${DateTime.now}")
      currentBid = Some(bid)
      currentWinner = Some(sender)
      informInterested()
      withRestartedTimer()
      BidAck(bid)
    } else {
      println(s"Too low bid of '$proposed' placed on: ${self.id} at '$currentPrice' at ${DateTime.now}")
      BidNAck(bid)
    }
  }

  private def inform(sender: ActorRef) = {
    context match {
      case activated =>
        sender ! Info(currentPrice,currentWinner)
      case ignored =>
        sender ! Inactive
      case sold => sender ! Closed
    }
  }

  private def informOfResult() {
    currentWinner.getOrElse(throw new InvalidStateException("")) ! Won(currentPrice)
    interested.filter(_ != currentWinner.getOrElse(None)).foreach {
      _ ! Lost
    }
  }

  private def withRestartedTimer(): DateTime = {
    val endTime = DateTime.now+ignoredDuration.toSeconds
    system.scheduler.scheduleOnce(bidWaitDuration,self,BidTimerExpired(endTime))
    endTime
  }

  private def startDeleteTimer() = {
    system.scheduler.scheduleOnce(ignoredDuration,self,DeleteTimerExpired)

  }

  when(Idle) {
    case Event(Start(), Uninitialized(price,interested)) =>
      val endTime = withRestartedTimer()
      println(s"Auction ${self.id} for $price started, and will end at $endTime")
      informInterested()
      goto(Created) using Initialized(price,interested,endTime)
    case Event(_,Uninitialized(price,interested)) =>
      sender ! Info(price,None)
      goto(Idle) using Uninitialized(price,interested+sender)
  }

  when(Created) {
    case Event(bid @ Bid(proposed),Initialized(starting,interested,endTime)) =>
      interested += sender
      val result = processedBid(bid,sender)
      result match {
        case BidAck(value) =>
          sender ! bid
          println(s"Auction ${self.id} activated")
          goto(Activated) using Bidding(proposed,interested,endTime,sender)
        case BidNAck(value) =>
      }
    case Event(BidTimerExpired(time),Initialized(price,interested,endTime)) =>
      if (time == endTime) {
        println(s"Timer expired for ${self.id} at $endTime, item ignored")
        startDeleteTimer()
        goto(Ignored)
      } else {
        goto(Created) using Initialized(price,interested,endTime)
      }
    case Event(_,Initialized(price,interested,endTime)) =>
      sender ! Info(price,None)
      goto(Idle) using Initialized(price,interested+sender,endTime)

  when(Activated) = {
    case bid @ Bid(proposed) =>
      interested += sender
      sender ! processedBid(bid, sender)
    case BidTimerExpired(time) => if (time == endTime) {
      informOfResult()
      println(s"Timer expired for ${self.id}, item sold for '$currentPrice' at ${DateTime.now}")
      context.become(sold)
    }
    case _ => inform(sender)
  }

  def ignored: Receive = LoggingReceive {
    case Start if sender == seller.getOrElse(None) =>
      start()
    case DeleteTimerExpired =>
      println(s"Delete timer expired for ${self.id} at ${DateTime.now()}, auction deleted")
      context.stop(self)
    case _ =>
  }

  def sold: Receive = LoggingReceive {
    case _ => inform(sender)
  }

}
