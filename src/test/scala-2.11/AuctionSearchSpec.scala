import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import auctionHouse.AuctionDescription
import auctionHouse.search.AuctionSearch
import auctionHouse.search.AuctionSearch.{Ready, _}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration._

class AuctionSearchSpec extends TestKit(ActorSystem("auctionHouse")) with WordSpecLike with BeforeAndAfterAll {

  val desc = List(
    AuctionDescription("car", 10000.0),
    AuctionDescription("computer", 2000.0),
    AuctionDescription("toothbrush", 10.0)
  )
//
//  implicit class HasDataAccessible(as :AuctionSearch) {
//    def data = {
//      as.stateData.asInstanceOf[Initialized]
//    }
//  }
//
//  override def afterAll(): Unit = {
//    system.terminate
//  }
//
//  "An auction search" when {
//    "created" must {
//      val search = TestActorRef[AuctionSearch]
//      val probe = TestProbe("auctionHouse")
//      "be in Ready state" in {
//        assert(search.underlyingActor.stateName == Ready)
//      }
//    }
//    val search = TestActorRef[AuctionSearch]
//    val auctionProbe = TestProbe("auction")
//    "enable registering" in {
//      auctionProbe.send(search,Register("foo"))
//      assert(search.underlyingActor.data.nameToAuction("foo") == auctionProbe.ref)
//      assert(search.underlyingActor.data.nameToAuction.size == 1)
//    }
//    "receiving Find message" must {
//      "not respond when there is nothing found" in {
//        auctionProbe.send(search,Find("SomeVery starenge sadsd2322 stuff"))
//        auctionProbe.expectNoMsg()
//      }
//      "respond if there is an auction found" in {
//        val bidder = TestProbe("bidder")
//        bidder.send(search,Find("foo"))
//        bidder.expectMsgPF(500 millis) {
//          case SearchResult("foo",res) if res == auctionProbe.ref =>
//        }
//      }
//    }
//    "enable unregistering" in {
//      auctionProbe.send(search,Unregister("foo"))
//      assert(!search.underlyingActor.data.nameToAuction.contains("foo"))
//      assert(search.underlyingActor.data.nameToAuction.isEmpty)
//    }
//  }

}
