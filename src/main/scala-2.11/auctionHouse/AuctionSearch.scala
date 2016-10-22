package auctionHouse

import akka.actor.{ActorRef, FSM}
import auctionHouse.AuctionSearch.{Initialized, Ready, Data, State}

object AuctionSearch {

  final case class Find(keyword: String)
  final case class SearchResult(keyword: String, results: List[ActorRef])

  sealed trait State
  case object Ready extends State

  sealed trait Data
  case object Initialized extends Data
}

class AuctionSearch extends FSM[State,Data]{

  startWith(Ready,Initialized)

}
