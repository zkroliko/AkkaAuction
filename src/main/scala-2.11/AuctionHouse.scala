import _root_.Auction.Start
import akka.actor.{ActorRef, Props, ActorSystem, Actor}
import org.joda.time.DateTime
import com.github.nscala_time.time.Imports._

object AuctionHouse extends App{

  import Auction._

  case class AuctionList(auctions: List[ActorRef])

  val system = ActorSystem("auctionHouse")
  val auction = system.actorOf(Props(new Auction("a book",200.0)))

  auction ! Auction.Start(DateTime.now +5.days )

}
