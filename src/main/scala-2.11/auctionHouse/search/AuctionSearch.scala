package auctionHouse.search

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef}
import akka.event.LoggingReceive

import scala.collection.immutable.Map

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

class AuctionSearch extends Actor{
  import AuctionSearch._

  var nameToAuction: Map[String,ActorRef] = scala.collection.immutable.Map[String,ActorRef]()

  implicit val timeout = akka.util.Timeout(1L, TimeUnit.SECONDS)

  def receive = LoggingReceive {
    case Find(keyword) =>
      nameToAuction.keys.filter(_.contains(keyword)).foreach {
        r => sender ! SearchResult(keyword,nameToAuction(r))
      }
    case Register(name) =>
      nameToAuction += (name->sender)
    case Unregister(name) =>
      nameToAuction -= name
  }
}
