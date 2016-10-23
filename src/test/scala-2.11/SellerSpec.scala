import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestProbe, TestActorRef, TestKit}
import auctionHouse.Auction.{KnowThatNotSold, KnowThatSold, Won}
import auctionHouse.{Auction, AuctionDescription, Seller}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import Seller._

class SellerSpec extends TestKit(ActorSystem("SellerSpec")) with WordSpecLike with BeforeAndAfterAll {

  val desc = List(
    AuctionDescription("car", 10000.0),
    AuctionDescription("computer", 2000.0),
    AuctionDescription("toothbrush", 10.0)
  )

  "An auction" when {
    "created" must {
      val seller = TestActorRef[Seller]
      "be in Waiting state" in {
        assert(seller.underlyingActor.stateName == Waiting)
      }
    }
    "in Waiting state" must {
      val seller = TestActorRef[Seller]
      "react to BuildFromDescriptions by building auctions" in {
        seller ! BuildFromDescriptions(desc)
        val auctions = seller.underlyingActor.stateData.asInstanceOf[AuctionListData].auctions
        assert(auctions.length == desc.length)
      }
    }
    "in AfterPostingAuctions state" must {
      val seller = TestActorRef[Seller]
      seller ! BuildFromDescriptions(desc)
      val auctions = seller.underlyingActor.stateData.asInstanceOf[AuctionListData].auctions
      val probes = auctions.map(a => TestProbe("AuctionProbe"))
      "react to message Won sent to him from auction" in {
        // Why can I do it externally? O_o
//        seller.underlyingActor.goto(AfterPostingAuctions) using AuctionListData(probes.map(_.ref))
        probes.foreach { p =>
          p.send(seller,KnowThatSold(100.0,p.ref))
        }
      }
      "react to message Lost sent to him from auction" in {
        // Why can I do it externally? O_o
        seller.underlyingActor.goto(AfterPostingAuctions) using AuctionListData(probes.map(_.ref))
        probes.foreach { p =>
          p.send(seller,KnowThatNotSold)
        }
      }
    }
  }

}
