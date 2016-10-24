package auctionHouse

import java.util.concurrent.TimeUnit

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorRef, FSM}
import akka.event.LoggingReceive
import auctionHouse.AuctionSearch._

import scala.collection.immutable.Map
import scala.concurrent.ExecutionContext.Implicits.global

object AuctionSearch {

  final case class Register(name: String)
  final case class Unregister(name: String)
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
      val s = sender // Some kind of shadowing later
      val path = s"akka://auctionHouse/*/*/seller*/*$keyword*"
      for (res <- context.actorSelection(path).resolveOne()) {
        s ! SearchResult(keyword, res)
      }
      stay()
    case Event(Register(name),Initialized(map)) =>
      stay() using Initialized(map+(name->sender))
    case Event(Unregister(name),Initialized(map)) =>
      stay() using Initialized(map - name)
  }
}
