package auctionHouse

import akka.actor.{ActorRef, FSM, Props}
import auctionHouse.Auction.Start
import tools.ActorTools.ReadableActorRef
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

  startWith(Waiting,UninitializedData)

  when(Waiting) {
    case Event(d: BuildFromDescriptions, UninitializedData) =>
      val auctions = d.descriptions.map(desc =>context.actorOf(Props(new Auction(desc)),desc.title)).toList
      goto(AfterPostingAuctions) using AuctionListData(auctions)
  }

  when (AfterPostingAuctions){
    case Event(k: Auction.KnowThatSold, a : AuctionListData) =>
      println(s"${self.name} knows that ${sender.name} has been sold ")
      stay()
    case Event(Auction.KnowThatNotSold, a : AuctionListData) =>
      println(s"${self.name} knows that his ${sender.name} has NOT been sold ")
      stay()
  }
  onTransition {
    case Waiting -> AfterPostingAuctions => nextStateData.asInstanceOf[AuctionListData].auctions.foreach {
      auction => auction ! Start
    }
  }

}
