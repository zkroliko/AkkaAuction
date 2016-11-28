import akka.actor.{Props, ActorSystem}
import auctionHouse.AuctionHouse
import auctionHouse.routingBench.RoutingBench

object AuctionApp extends App{
  val system = ActorSystem("auctionHouse")

  val auctionHouse = system.actorOf(Props[RoutingBench],"auctionHouse")

  auctionHouse ! RoutingBench.Init
}
