import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import auctionHouse.Auction._
import auctionHouse.BidderInterest.{Overbid, CanBid, CantBid, ShouldIBid}
import auctionHouse._
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import BigDecimal._
import scala.concurrent.duration._

class BidderInterestSpec extends TestKit(ActorSystem("SellerSpec")) with WordSpecLike with BeforeAndAfterAll {

  var bidder = TestProbe("bidder")
  var auction = TestProbe("auction")
  var bidInt = TestActorRef(Props(new BidderInterest(bidder.ref, auction.ref)))


  override def beforeAll() = {
    bidder = TestProbe("bidder")
    auction = TestProbe("auction")
    bidInt = TestActorRef(Props(new BidderInterest(bidder.ref, auction.ref)))
  }

  def underlying = bidInt.underlyingActor.asInstanceOf[BidderInterest]


  "A bidder interest" when {
    "created" when {
      "on receiving info from his auction" must {
        "calculate max bid value" in {
          auction.send(bidInt,Info(100.0,None))
          underlying.maxBid > 100.0
          bidder.expectMsgPF(1000 millis) {
            case ShouldIBid(a,b) =>
          }
        }
        "set known price" in {
          auction.send(bidInt,Info(100.0,None))
          underlying.knownPrice.toDouble == 100.0
          bidder.expectMsgPF(1000 millis) {
            case ShouldIBid(a,b) =>
          }
        }
        "ask his parent whether he can bid" in {
          auction.send(bidInt,Info(100.0,None))
          bidder.expectMsgPF(1000 millis) {
            case ShouldIBid(a,b) =>
          }
        }
      }
      "react to Lost" in {
        auction.send(bidInt,Lost())
        bidder.expectMsgPF(1000 millis) {
          case Overbid(p) =>
          case Lost() =>
        }
      }
    }
    "waiting for parent's response" must {
      "react to parent's Can't message" in {
        underlying.context.become(underlying.engaged)
        bidder.send(bidInt,CantBid())
        auction.expectMsgPF(100 millis) {
          case AskingForInfo =>
          case _ => fail()
        }
      }
      "react to parent's Can message" in {
        underlying.context.become(underlying.engaged)
        bidder.send(bidInt,CanBid(200.0))
        auction.expectMsgPF(400 millis) {
          case Bid(v) if v.toDouble == 200 =>
          case _ => fail()
        }
      }
      "react to Lost" in {
        underlying.context.become(underlying.engaged)
        underlying.myBid = 100.0
        auction.send(bidInt,Lost())
        bidder.expectMsgPF(1000 millis) {
          case Overbid(p) =>
          case Lost() =>
        }
      }
    }
    "after bidding" must {
      "react to BidAck" in {
        underlying.context.become(underlying.waitingForBidResult)
        underlying.myBid = 100.0
        auction.send(bidInt,BidAck(Bid(100.0)))
        assert(underlying.myBid == underlying.knownPrice)
      }
      "react to BidNAck" in {
        underlying.context.become(underlying.waitingForBidResult)
        underlying.myBid = 100.0
        auction.send(bidInt,BidNAck(Bid(102.0)))
        bidder.expectMsgPF(1000 millis) {
          case Overbid(p) if p == underlying.myBid =>
        }
      }
      "react to Lost" in {
        underlying.context.become(underlying.waitingForBidResult)
        underlying.myBid = 100.0
        auction.send(bidInt,Lost())
        bidder.expectMsgPF(1000 millis) {
          case Overbid(p) =>
          case Lost() =>
        }
      }
    }
    "when winning" must {
      "react to Info" in {
        underlying.context.become(underlying.winning)
        underlying.myBid = 100.0
        auction.send(bidInt,Info(100.0,Some(TestProbe("").ref)))
        bidder.expectMsgPF(1000 millis) {
          case Overbid(p) if p == underlying.myBid =>
          case Lost() => // fix this later
        }
      }
      "react to Lost" in {
        underlying.context.become(underlying.winning)
        underlying.myBid = 100.0
        auction.send(bidInt,Lost())
        bidder.expectMsgPF(1000 millis) {
          case Overbid(p) =>
          case Lost() =>
        }
      }
      "react to Won" in {
        underlying.context.become(underlying.winning)
        underlying.myBid = 100.0
        auction.send(bidInt,Won(99.0))
        bidder.expectMsgPF(1000 millis) {
          case Won(a) =>
          case Overbid(p) =>
          case ShouldIBid(a,b) => // fix this later
          case Lost() => // fix this later
        }
      }
    }
  }

}
