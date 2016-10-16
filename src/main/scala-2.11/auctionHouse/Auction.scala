package auctionHouse


import java.util.concurrent.TimeUnit

import akka.actor._
import akka.event.LoggingReceive
import auctionHouse.Auction.AskForInfo
import com.github.nscala_time.time.Imports._
import sun.plugin.dom.exception.InvalidStateException


import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration

object Auction {

  case object Start
  case object AskForInfo
  case class Info(current : BigDecimal, leader: Option[ActorRef])
  case class Bid(proposed : BigDecimal)
  case object Closed
  case object Inactive
  trait BidResult
  case class BidAck(bid: Bid) extends BidResult
  case class BidNack(bid: Bid) extends BidResult
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
  var currentBid: Option[Bid] = None
  def currentPrice: BigDecimal = if (currentBid.nonEmpty) currentBid.get.proposed else startingPrice
  var currentWinner : Option[ActorRef] = None
  var endTime: DateTime = DateTime.now + ignoredDuration.toSeconds

  val system = ActorSystem("timingSystem")
  import system.dispatcher

  val interested = scala.collection.mutable.SortedSet[ActorRef]()

  private def start() {
    restartTimer()
    println(s"Auction for: $item for $currentPrice from seller $seller started, and will end at $endTime")
    informInterested()
    context.become(created)
  }

  private def informInterested(): Unit = {
    interested.foreach(_ ! Info(currentPrice,currentWinner))
  }

  private def processedBid(bid: Bid, sender: ActorRef): BidResult = {
    val proposed = bid.proposed
    if (proposed > currentPrice) {
      println(s"Valid bid placed of $proposed over $currentPrice on: $item: at ${DateTime.now}")
      currentBid = Some(bid)
      currentWinner = Some(sender)
      informInterested()
      restartTimer()
      BidAck(bid)
    } else {
      println(s"Too low bid placed of $proposed on: $item: $currentPrice at ${DateTime.now}")
      BidNack(bid)
    }
  }

  private def inform(sender: ActorRef) = {
    interested += sender
    context match {
      case created =>
        interested += sender
        sender ! Info(currentPrice,currentWinner)
      case activated =>
        interested += sender
        sender ! Info(currentPrice,currentWinner)
      case ignored =>
        interested += sender
        sender ! Inactive
      case sold => sender ! Closed
    }
  }

  private def informOfResult() = {
    currentWinner.getOrElse(throw new InvalidStateException("")) ! Won(currentPrice)
    interested.filter(_ != currentWinner.getOrElse(None)).foreach {
      _ ! Lost
    }
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
      start()
    case _ => inform(sender)
  }

  def created: Receive = LoggingReceive {
    case bid @ Bid(proposed) =>
      interested += sender
      val result = processedBid(bid,sender)
      result match {
        case BidAck(value) =>
          sender ! bid
          println(s"Auction $this activated")
          context.become(activated)
        case BidNack(value) =>
      }
    case BidTimerExpired(time) => if (time == endTime) {
      println(s"Timer expired for $this at $endTime, item ignored")
      startDeleteTimer()
      context.become(ignored)
    }
    case _ => inform(sender)
  }

  def activated: Receive = LoggingReceive {
    case bid @ Bid(proposed) =>
      interested += sender
      sender ! processedBid(bid, sender)
    case BidTimerExpired(time) => if (time == endTime) {
      println(s"Timer expired for $this at $endTime, item sold to: ${currentWinner.getOrElse("anonymous")}")
      informOfResult()
      context.become(sold)
    }
    case _ => inform(sender)
  }

  def ignored: Receive = LoggingReceive {
    case Start if sender == seller.getOrElse(None) =>
      start()
    case DeleteTimerExpired =>
      println(s"Delete timer expired for $this at ${DateTime.now()}, auction deleted")
      context.stop(self)
    case _ =>
  }

  def sold: Receive = LoggingReceive {
    case _ => inform(sender)
  }

}
