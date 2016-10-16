import akka.actor.{Props, ActorSystem}
import auctionHouse.AuctionHouse

object AuctionApp extends App{
  val system = ActorSystem("auctionHouse")

  val auctionHouse = system.actorOf(Props[AuctionHouse])

  auctionHouse ! AuctionHouse.Init
}
