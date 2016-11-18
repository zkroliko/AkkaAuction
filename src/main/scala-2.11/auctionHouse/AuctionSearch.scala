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
  case class Initialized(nameToAuction: Map[String,ActorRef], senderToKeyword: Map[ActorRef,String]) extends Data

  val path = "akka://auctionHouse/*/*/auctionSearch"
}

class AuctionSearch extends FSM[State,Data]{

  startWith(Ready,Initialized(Map(),Map()))

  implicit val timeout = akka.util.Timeout(1L, TimeUnit.SECONDS)

  when(Ready) {
    case Event(Find(keyword),Initialized(auctions,bidders)) =>
      stay() using Initialized(auctions,bidders+(sender->keyword))
    case Event(Register(name),data @ Initialized(auctions,bidders)) =>
      val newData = data.copy(nameToAuction = auctions+(name->sender))
      processFinds(newData)
      stay() using newData
    case Event(Unregister(name),Initialized(map,bidders)) =>
      stay() using Initialized(map - name,bidders)
  }

  private def processFinds(data: Initialized) = {
    data.senderToKeyword.foreach{
      case (bidder,keyword) =>
        data.nameToAuction.keys.filter(_.contains(keyword)).foreach {
          r => bidder ! SearchResult(keyword, data.nameToAuction(keyword))
        }
    }
  }
}
