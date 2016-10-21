package auctionHouse

import akka.actor.{Props, ActorRef, FSM}
import auctionHouse.Seller._

object Seller {

  case class BuildFromDescriptions(descriptions: List[AuctionDescription])

  sealed trait State
  case object Waiting extends State
  case object AfterPostingAuctions extends State

  sealed trait Data
  case object UninitializedData extends Data
  final case class AuctionListData(auctions: List[ActorRef]) extends Data
}

class Seller extends FSM[State,Data]{

  when(Waiting) {
    case Event(d: BuildFromDescriptions, UninitializedData) =>
      val auctions = d.descriptions.map(desc =>context.actorOf(Props(new Auction(desc)))).toList
      goto(AfterPostingAuctions) using AuctionListData(auctions)
  }

  when (AfterPostingAuctions){
    case Event(Auction.Sold,a : AuctionListData) =>
      stay()
  }

}
