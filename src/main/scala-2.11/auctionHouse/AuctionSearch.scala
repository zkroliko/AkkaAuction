package auctionHouse

import akka.actor.FSM
import auctionHouse.AuctionSearch.{Initialized, Ready, Data, State}

object AuctionSearch {

  final case class Find(keyword: String)

  sealed trait State
  case object Ready extends State

  sealed trait Data
  case object Initialized extends Data
}

class AuctionSearch extends FSM[State,Data]{

  startWith(Ready,Initialized)

}
