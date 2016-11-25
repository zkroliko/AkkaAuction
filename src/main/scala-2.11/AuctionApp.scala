import akka.actor.{Props, ActorSystem}
import auctionHouse.{AuctionPublisher, AuctionHouse}
import com.typesafe.config.ConfigFactory

object AuctionApp extends App{

  val config = ConfigFactory.load()

  val publisher = ActorSystem("auctionPublisher", config.getConfig("auctionPublisher").withFallback(config))
  val auctionPublisher = publisher.actorOf(Props[AuctionPublisher],"auctionPublisher")

  auctionPublisher ! AuctionPublisher.Init

  val auction = ActorSystem("auctionHouse", config.getConfig("auctionHouse").withFallback(config))
  val auctionHouse = auction.actorOf(Props[AuctionHouse],"auctionHouse")

  auctionHouse ! AuctionHouse.Init
}
