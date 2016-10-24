import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestProbe, TestActorRef, TestKit}
import auctionHouse.AuctionSearch.Ready
import auctionHouse.Seller.BuildFromDescriptions
import auctionHouse.{Seller, Auction, AuctionDescription, AuctionSearch}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import AuctionSearch._
import scala.concurrent.duration._

class AuctionSearchSpec extends TestKit(ActorSystem("auctionHouse")) with WordSpecLike with BeforeAndAfterAll {

  val desc = List(
    AuctionDescription("car", 10000.0),
    AuctionDescription("computer", 2000.0),
    AuctionDescription("toothbrush", 10.0)
  )

  override def afterAll(): Unit = {
    system.terminate
  }

  "An auction search" when {
    val search = TestActorRef[AuctionSearch]
    val probe = TestProbe("auctionHouse")
    "created" must {
      "be in Ready state" in {
        assert(search.underlyingActor.stateName == Ready)
      }
    }
    "receiving Find message" must {
      "not respond when there is nothing found" in {
        probe.send(search,Find("SomeVery starenge sadsd2322 stuff"))
        probe.expectNoMsg()
      }
      "respond if there is an auction found" in {
        val name = "Bulbulator9000X"
        val namePart = "ulbu"
        val s = probe.childActorOf(Props[Seller],"seller1")
        println(s.path)
        probe.send(s,BuildFromDescriptions(List(AuctionDescription(name,4815162342.0))))
        probe.send(search,Find(namePart))
        probe.expectMsgPF(2000 millis) {
          case SearchResult(foundK,foundA) if foundK == namePart =>
        }
      }
    }
  }

}
