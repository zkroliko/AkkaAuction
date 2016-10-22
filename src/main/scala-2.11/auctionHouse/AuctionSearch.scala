package auctionHouse

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, FSM}
import auctionHouse.AuctionSearch._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration

object AuctionSearch {

  final case class Find(keyword: String)
  final case class SearchResult(keyword: String, result: ActorRef)

  sealed trait State
  case object Ready extends State

  sealed trait Data
  case object Initialized extends Data
}

class AuctionSearch extends FSM[State,Data]{

  startWith(Ready,Initialized)

  implicit val timeout = akka.util.Timeout(1L, TimeUnit.SECONDS)

  when(Ready) {
    case Event(Find(keyword),Initialized) =>
      val s = sender // Some kind of shadowing later
      val path = "akka://auctionHouse/user/auctionHouse/*/"+keyword
      for (res <- context.actorSelection(path).resolveOne()) {
        s ! SearchResult(keyword, res)
      }
      stay()
  }

}
