package auctionHouse

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, FSM}
import auctionHouse.AuctionSearch._

import scala.collection.immutable.Map
import scala.concurrent.duration.Duration

object AuctionSearch {

  trait RegistrationMessage
  final case class Register(name: String) extends RegistrationMessage
  final case class Unregister(name: String) extends RegistrationMessage

  final case class Find(keyword: String)
  final case class SearchResult(keyword: String, result: ActorRef)

  sealed trait State
  case object Ready extends State

  sealed trait Data
  case class Initialized(nameToAuction: Map[String,ActorRef]) extends Data

  val path = "akka://auctionHouse/*/*/auctionSearch"
}

class AuctionSearch extends FSM[State,Data]{

  startWith(Ready,Initialized(Map()))

  implicit val timeout = akka.util.Timeout(1L, TimeUnit.SECONDS)

  when(Ready) {
    case Event(Find(keyword),Initialized(map)) =>
      map.keys.filter(_.contains(keyword)).foreach {
        r => sender ! SearchResult(keyword,map(r))
      }
      stay()
    case Event(Register(name),Initialized(map)) =>
      stay() using Initialized(map+(name->sender))
    case Event(Unregister(name),Initialized(map)) =>
      stay() using Initialized(map - name)
  }
}
