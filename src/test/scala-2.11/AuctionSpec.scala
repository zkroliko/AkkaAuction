import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import auctionHouse.Auction._
import auctionHouse.{Auction, AuctionDescription}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import scala.concurrent.duration._

class AuctionSpec extends TestKit(ActorSystem("AuctionSpec")) with WordSpecLike with BeforeAndAfterAll {

  val desc = AuctionDescription("car", 1000.0)
  import system.dispatcher

  "An auction" when {
    "created from description in Idle state" must {
      val auction = TestActorRef(Props(new Auction(desc)))
      "have title set" in {
        assert(auction.underlyingActor.asInstanceOf[Auction].title == desc.title)
      }
      "have starting price set" in {
        assert(auction.underlyingActor.asInstanceOf[Auction].startingPrice == desc.price)
      }
    }
    "being in Idle state" must {
      "when receiving Start message," must {
        val auction = TestActorRef(Props(new Auction(desc)))
        "react to Start message, by going to Crated state" in {
          val auction = TestActorRef(Props(new Auction(desc)))
          auction ! Start
          assert(auction.underlyingActor.asInstanceOf[Auction].stateName == Created)
        }
        val probe = TestProbe("interested")
        "inform of its state" in {
          probe.send(auction,AskingForInfo)
          probe.expectMsgPF(500 millis) {
            case i : Info =>
          }
        }
        "inform actors in subscription list" in {
          probe.send(auction,Start)
          probe.expectMsgPF(500 millis)  {
            case i : Info =>
          }
        }
      }
    }
    "being in Created state" must {
      "accept good bids" in {
        val probe = TestProbe("interested")
        val auction = TestActorRef(Props(new Auction(desc)))
        auction ! Start
        probe.send(auction,Bid(2000.0))
        probe.expectMsgPF(500 millis) {
          case i : BidAck =>
            assert(auction.underlyingActor.asInstanceOf[Auction].stateData.
              asInstanceOf[BiddingData].price.doubleValue == 2000.0)
        }
      }
      "refuse bad bids" in {
        val probe = TestProbe("interested")
        val auction = TestActorRef(Props(new Auction(desc)))
        auction ! Start
        probe.send(auction,Bid(1.0))
        probe.expectMsgPF(500 millis) {
          case i : BidNAck =>
            assert(auction.underlyingActor.asInstanceOf[Auction].stateData.
              asInstanceOf[WaitingData].startingPrice.doubleValue == 1000.0)
        }
      }
      "after a bid transition to activated state" in {
        val auction = TestActorRef(Props(new Auction(desc)))
        auction ! Start
        auction ! Bid (2000.0)
        assert(auction.underlyingActor.asInstanceOf[Auction].stateName == Activated)
      }
      "without a bid transition to ignored state after some time" in {
        val probe = TestProbe("interested")
        val auction = TestActorRef(Props(new Auction(desc)))
        auction ! Start
        val endTime = auction.underlyingActor.asInstanceOf[Auction].stateData.asInstanceOf[WaitingData].endTime
        val expMsg = BidTimerExpired(endTime)
        auction ! expMsg
        system.scheduler.scheduleOnce(bidWaitDuration,probe.ref,"nowTest")
        probe.expectMsgPF(5500 millis) {
          case "nowTest" =>
            assert(auction.underlyingActor.asInstanceOf[Auction].stateName == Ignored)
        }
      }
      "inform of its state" in {
        val probe = TestProbe("interested")
        val auction = TestActorRef(Props(new Auction(desc)))
        auction ! Start
        probe.send(auction,AskingForInfo)
        probe.expectMsgPF(500 millis) {
          case i : Info =>
        }
      }
    }
    "being in Activated state" must {
      "inform of its state" in {
        val probe = TestProbe("interested")
        val auction = TestActorRef(Props(new Auction(desc)))
        auction ! Start
        auction ! Bid(1005.0)
        probe.send(auction,AskingForInfo)
        probe.expectMsgPF(500 millis) {
          case i : Info =>
        }
      }
      "accept good bids" in {
        val probe = TestProbe("interested")
        val auction = TestActorRef(Props(new Auction(desc)))
        auction ! Start
        auction ! Bid(1005.0)
        probe.send(auction,Bid(2000.0))
        probe.expectMsgPF(500 millis) {
          case i : BidAck =>
            assert(auction.underlyingActor.asInstanceOf[Auction].stateData.
              asInstanceOf[BiddingData].price.doubleValue == 2000.0)
        }
      }
      "refuse bad bids" in {
        val probe = TestProbe("interested")
        val auction = TestActorRef(Props(new Auction(desc)))
        auction ! Start
        auction ! Bid(1005.0)
        probe.send(auction,Bid(1.0))
        probe.expectMsgPF(500 millis) {
          case i : BidNAck =>
            assert(auction.underlyingActor.asInstanceOf[Auction].stateData.
              asInstanceOf[BiddingData].price.doubleValue == 1005.0)
        }
      }
      "without a bid transition to be sold after some time" in {
        val probe = TestProbe("interested")
        val auction = TestActorRef(Props(new Auction(desc)))
        auction ! Start
        probe.send(auction,Bid(1001.0))
        probe.expectMsgPF(500 millis) {
          case b : BidAck =>
        }
        probe.expectMsgPF(5500 millis) {
          case Won(value) if value.doubleValue() == 1001.0 =>
        }
      }
    }
  }

}
