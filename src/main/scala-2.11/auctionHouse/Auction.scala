package auctionHouse


import java.util.concurrent.TimeUnit

import akka.actor._
import akka.persistence.fsm.PersistentFSM.FSMState
import auctionHouse.Auction._
import auctionHouse.AuctionSearch.{Register, RegistrationMessage, Unregister}
import auctionHouse.Notifier.NotificationContent
import com.github.nscala_time.time.Imports._
import sun.plugin.dom.exception.InvalidStateException
import tools.ActorTools.ReadableActorRef
import tools.TimeTools

import scala.collection.immutable.SortedSet
import scala.concurrent.duration.{FiniteDuration, Duration}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}
import akka.actor.actorRef2Scala
import scala.reflect._
import akka.persistence.fsm._

object Auction {

  type InterestedSet = SortedSet[ActorRef]

  val registerTimeout = Duration.create(1,TimeUnit.SECONDS)
  val bidWaitDuration = Duration.create(5,TimeUnit.SECONDS)
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

  sealed trait State extends FSMState
  case object Idle extends State {
    override def identifier: String = "Idle"
  }
  case object Created extends State {
    override def identifier: String = "Created"
  }
  case object Ignored extends State {
    override def identifier: String = "Ignored"
  }
  case object Activated extends State {
    override def identifier: String = "Activated"
  }
  case object Sold extends State {
    override def identifier: String = "Sold"
  }
  case object Deleted extends State {
    override def identifier: String = "Deleted"
  }

  sealed trait Data
  final case class Uninitialized(startingPrice: BigDecimal, interested: InterestedSet) extends Data
  final case class WaitingData(seller: ActorRef, startingPrice: BigDecimal, interested: InterestedSet,
                               endTime: DateTime) extends Data
  final case class BiddingData(seller: ActorRef, price: BigDecimal, interested: InterestedSet, endTime: DateTime,
                               currentWinner: ActorRef) extends Data
  final case class IgnoredData(seller: ActorRef, price: BigDecimal, interested: InterestedSet) extends Data
  final case class SoldData(seller: ActorRef, endPrice: BigDecimal, winner: Option[ActorRef], interested: InterestedSet) extends Data

  sealed trait DomainEvent
  case class BidderSubscribed(bidder: ActorRef) extends DomainEvent
  case class BidAccepted(price: BigDecimal, sender: ActorRef, time: DateTime) extends DomainEvent
  case class BecameCreated(creator: ActorRef, time: DateTime) extends DomainEvent
  case class BecameIgnored(time: DateTime) extends DomainEvent
  case object BecameDeleted extends DomainEvent
  case object BecameSold extends DomainEvent
}

class Auction(description: AuctionDescription) extends PersistentFSM[State, Data, DomainEvent] {
  import Auction._

  override def domainEventClassTag: ClassTag[DomainEvent] = classTag[DomainEvent]

  override def persistenceId: String = "persistent-auction-fsm"+self.path

  // For Auction Publisher system
  val notifier = context.actorOf(Props[Notifier])

  val title : String = description.title
  val startingPrice: BigDecimal = description.price

  val system = context.system
  import system.dispatcher

  register()

  startWith(Idle,Uninitialized(startingPrice,SortedSet()))

  override def applyEvent(domainEvent: DomainEvent, currentData: Data): Data = {
    domainEvent match {
      case BecameCreated(creator,time) =>
        currentData match {
          case Uninitialized(price,interested) =>
            val endTime = setTimer(time)
            informInterested(interested,price,None)
            WaitingData(sender,price,interested,endTime)
          case IgnoredData(seller,price,interested) =>
            val endTime = setTimer(time)
            informInterested(interested,price,None)
            WaitingData(sender,price,interested,endTime)
          case _ => throw new AssertionError
        }
      case BidderSubscribed(bidder) =>
        currentData match {
          case d: Uninitialized  => d.copy(interested = d.interested + bidder)
          case d: WaitingData =>  d.copy(interested = d.interested + bidder)
          case d: BiddingData => d.copy(interested = d.interested + bidder)
          case d: IgnoredData => d.copy(interested = d.interested + bidder)
          case d: SoldData => d.copy(interested = d.interested + bidder)
        }
      case BidAccepted(newValue, bidder, time) =>
        currentData match {
          case WaitingData(seller,price,interested,oldTime) =>
            informInterested(interested,price,Some(bidder))
            val endTime = setTimer(time)
            BiddingData(seller, newValue, interested + bidder, endTime, bidder)
          case BiddingData(seller,price,interested,oldTime,prevLeader) =>
            informInterested(interested,price,Some(bidder))
            val endTime = setTimer(time)
            BiddingData(seller, newValue, interested + bidder, endTime, bidder)
          case _ => throw new AssertionError
        }
      case BecameIgnored(time) =>
        currentData match {
          case WaitingData(seller,price,interested,endTime) =>
            setDeleteTimer(DateTime.now)
            IgnoredData(seller,price,interested)
          case _ => throw new AssertionError
        }
      case BecameSold =>
        currentData match {
          case BiddingData(seller,price,interested,oldTime,leader) =>
            informOfResult(interested,Some(leader),price)
            seller ! KnowThatSold(price, leader)
            unregister()
            SoldData(seller,price,Some(leader),interested)
//            stop(PersistentFSM.Normal)
          case _ => throw new AssertionError
        }
      case BecameDeleted =>
        currentData match {
          case data @ IgnoredData(seller,price,interested) =>
            seller ! KnowThatNotSold
            unregister()
            data
//            stop(PersistentFSM.Normal)
          case _ => throw new AssertionError
        }
      case _ => throw new AssertionError
    }
  }

  when(Idle) {
    case Event(Start, Uninitialized(price,interested)) =>
      val endTime = DateTime.now+ignoredDuration.toSeconds
      println(f"Auction ${self.name} for $price%1.2f created, and will end at ${TimeTools.timeFormatted(endTime)}")
      goto(Created) applying BecameCreated(sender,endTime)
    case Event(AskingForInfo, u: Uninitialized) =>
      sender ! Info(u.startingPrice,None)
      stay() applying BidderSubscribed(sender)
  }

  when(Created) {
    case Event(bid@Bid(proposed), data @ WaitingData(seller, currentPrice, interested, endTime)) =>
      val result = processedBid(bid, currentPrice, sender)
      sender ! result
      result match {
        case BidAck(value) =>
          println(s"Auction ${self.name} activated")
          goto(Activated) applying BidAccepted(value.proposed,sender,DateTime.now)
        case BidNAck(value) =>
          stay() applying BidderSubscribed(sender)
      }
    case Event(BidTimerExpired(time), data : WaitingData) =>
      if (time == data.endTime) {
        println(s"Timer expired for ${self.name} at ${TimeTools.timeFormatted(data.endTime)}, item ignored")
        goto(Ignored) applying BecameIgnored(DateTime.now)
      } else {
        stay()
      }
    case Event(_, data : WaitingData)  =>
      sender ! Info(data.startingPrice, None)
      stay() applying BidderSubscribed(sender)
  }

  when(Activated) {
    case Event(bid@Bid(proposed), data @ BiddingData(seller,currentPrice, interested, endTime, previousLeader)) =>
      val result = processedBid(bid, currentPrice, sender)
      sender ! result
      result match {
        case BidAck(value) =>
          goto(Activated) applying BidAccepted(value.proposed,sender,DateTime.now)
        case BidNAck(value) =>
          stay() applying BidderSubscribed(sender)
      }
    case Event(BidTimerExpired(time), BiddingData(seller,endPrice, interested, endTime, winner)) =>
      if (time == endTime) {
        println(f"Timer expired for ${self.name}, item sold for '$endPrice%1.2f' at ${TimeTools.timeNow}")
        goto(Sold) applying BecameSold
      } else {
        stay()
      }
    case Event(_, data : BiddingData) =>
      sender ! Info(data.price, Some(data.currentWinner))
      stay() applying BidderSubscribed(sender)
  }

  when(Ignored) {
    case Event(Start,IgnoredData(seller, price,interested)) =>
      val endTime = setTimer(DateTime.now)
      goto(Created) applying BecameCreated(seller, endTime)
    case Event(DeleteTimerExpired,IgnoredData(seller, price,interested)) =>
      println(s"Delete timer expired for ${self.name} at ${TimeTools.timeNow}, auction deleted")
      goto(Deleted) applying BecameDeleted
    case Event(_,Uninitialized(price,interested)) =>
      sender ! Info(price,None)
      stay() applying BidderSubscribed(sender)
  }

  when(Sold) {
    case Event(_,SoldData(seller,price,winner,interested)) =>
      sender ! Info(price,winner)
      stay()
  }

  when(Deleted) {
    case Event(_,IgnoredData(seller,price,interested)) =>
      sender ! Info(price,None)
      stay()
  }

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
    // Auction publisher
    notifier ! NotificationContent(description.title,price,winning)
  }

  private def processedBid(bid: Bid, currentPrice: BigDecimal, sender: ActorRef): BidResult = {
    val proposed = bid.proposed
    if (proposed > currentPrice) {
      println(f"Valid bid of '$proposed%1.2f' > '$currentPrice%1.2f' placed on: ${self.name}: at ${TimeTools.timeNow}")
      BidAck(bid)
    } else {
      println(s"Too low bid of '$proposed' placed on: ${self.name} at '$currentPrice' at ${TimeTools.timeNow}")
      BidNAck(bid)
    }
  }

  private def informOfResult(interested: SortedSet[ActorRef], currentWinner: Option[ActorRef], price: BigDecimal) {
    currentWinner.getOrElse(throw new InvalidStateException("")) ! Won(price)
    interested.filter(_ != currentWinner.getOrElse(None)).foreach {
      _ ! Lost
    }
    notifier ! NotificationContent(description.title,price,None)
  }

  private def setTimer(startTime: DateTime): DateTime = {
    val endTime: DateTime = startTime+bidWaitDuration.toSeconds
    val diff = new Period(startTime,endTime)
    val duration = FiniteDuration(diff.toDurationFrom(startTime).getMillis,TimeUnit.SECONDS)
    system.scheduler.scheduleOnce(duration,self,BidTimerExpired(endTime))
    endTime
  }

  private def setDeleteTimer(startTime: DateTime): DateTime = {
    val endTime: DateTime = startTime+ignoredDuration.toSeconds
    val diff = new Period(startTime,endTime)
    val duration = FiniteDuration(diff.toDurationFrom(startTime).getMillis,TimeUnit.SECONDS)
    system.scheduler.scheduleOnce(duration,self,DeleteTimerExpired)
    endTime
  }
}
