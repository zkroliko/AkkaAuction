package auctionHouse

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.github.nscala_time.time.Imports._
import scala.concurrent.duration.Duration

object AuctionHouse extends App {

  case class AuctionList(auctions: List[ActorRef])

  val system = ActorSystem("auctionHouse")
  val auction = system.actorOf(Props(new Auction("a book",200.0)))
  val bidder = system.actorOf(Props[Bidder])
  val bidder2 = system.actorOf(Props[Bidder])

  auction ! Auction.Start
  bidder ! AuctionList(List(auction))
  bidder2 ! AuctionList(List(auction))
}
