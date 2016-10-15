package auctionHouse

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import auctionHouse.Auction.Start
import com.github.nscala_time.time.Imports._
import scala.concurrent.duration.Duration

object AuctionHouse extends App {

  case class AuctionList(auctions: List[ActorRef])

  val nAuctions = 20
  val nBidders = 1

  val system = ActorSystem("auctionHouse")
  val auction = system.actorOf(Props(new Auction("a book",200.0)))
  val bidder = system.actorOf(Props[Bidder])
  val bidder2 = system.actorOf(Props[Bidder])

  val auctions = (1 to nAuctions).map(n =>system.actorOf(Props(new Auction("a book"+n,200.0+n)))).toList
  val bidders = (1 to nBidders).map(n => system.actorOf(Props[Bidder])).toList

  auctions.foreach(_ ! Start)
  bidders.foreach(_ ! AuctionList(auctions))
}
