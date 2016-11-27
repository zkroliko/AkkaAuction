import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import auctionHouse.Auction.{AskingForInfo, Lost, Won}
import auctionHouse.AuctionHouse.LookAtDescriptions
import auctionHouse.BidderInterest.{CanBid, CantBid, Overbid, ShouldIBid}
import auctionHouse.search.AuctionSearch
import auctionHouse.search.AuctionSearch.SearchResult
import auctionHouse.{AuctionDescription, Bidder}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration._


class BidderSpec extends TestKit(ActorSystem("SellerSpec")) with WordSpecLike with BeforeAndAfterAll {

  val desc = List(
    AuctionDescription("car", 10000.0),
    AuctionDescription("computer", 2000.0),
    AuctionDescription("toothbrush", 10.0)
  )

  override def afterAll(): Unit = {
    system.terminate
  }

  "A bidder" must {
    val bidder = TestActorRef[Bidder]
    "react to message with description list" in {
      bidder ! LookAtDescriptions(desc)
    }
    "react to search telling him that something has been found" in {
      val auction = TestProbe("auction")
      bidder ! SearchResult("foo", auction.ref)
      auction.expectMsgPF(1000 millis) {
        case AskingForInfo =>
      }
    }
    "respond to question of bidding positively" in {
      val bidder = TestActorRef[Bidder]
      val auction = TestProbe("auction")
      val child = TestProbe("child")
      bidder.underlyingActor.budgetLeft = 100.0
      bidder.underlyingActor.neededItems = 1
      child.send(bidder,ShouldIBid(10.0,20.0))
      child.expectMsgPF(1000 millis) {
        case CanBid(a) =>
      }
    }
    "respond to question of bidding negatively because of budget" in {
      val bidder = TestActorRef[Bidder]
      val auction = TestProbe("auction")
      val child = TestProbe("child")
      bidder.underlyingActor.budgetLeft = 10.0
      bidder.underlyingActor.neededItems = 1
      child.send(bidder,ShouldIBid(100.0,200.0))
      child.expectMsgPF(1000 millis) {
        case CantBid =>
      }
    }
    "respond to question of bidding negatively because of needs" in {
      val bidder = TestActorRef[Bidder]
      val child = TestProbe("child")
      bidder.underlyingActor.budgetLeft = 10000.0
      bidder.underlyingActor.neededItems = 0
      child.send(bidder,ShouldIBid(100.0,200.0))
      child.expectMsgPF(1000 millis) {
        case CantBid =>
      }
    }
    "acknowledge winning" in {
      val bidder = TestActorRef[Bidder]
      val child = TestProbe("child")
      bidder.underlyingActor.interests += child.ref
      assert(bidder.underlyingActor.interests.contains(child.ref))
      child.send(bidder,Won(100.0))
      assert(!bidder.underlyingActor.interests.contains(child.ref))
    }
    "acknowledge loosing" in {
      val bidder = TestActorRef[Bidder]
      val child = TestProbe("child")
      bidder.underlyingActor.interests += child.ref
      assert(bidder.underlyingActor.interests.contains(child.ref))
      child.send(bidder,Lost())
      assert(!bidder.underlyingActor.interests.contains(child.ref))
    }
    "acknowledge overbidding" in {
      val bidder = TestActorRef[Bidder]
      val child = TestProbe("child")
      bidder.underlyingActor.budgetLeft = 0.0
      bidder.underlyingActor.neededItems = 0
      child.send(bidder,Overbid(100.0))
      assert(bidder.underlyingActor.budgetLeft == 100.0)
      assert(bidder.underlyingActor.neededItems == 1)
    }
  }

}
