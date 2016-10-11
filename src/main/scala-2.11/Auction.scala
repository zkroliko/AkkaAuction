
import akka.actor._
import akka.event.LoggingReceive
import org.joda.time.DateTime
import com.github.nscala_time.time.Imports._

object Auction {

  case class Start(end: DateTime) {}
  case class AskPrice() {}
  case class PriceInfo(current : BigDecimal) {}
  case class ClosingInfo(time: DateTime) {}
  case class Bid(proposed : BigDecimal) {}
  trait BidResult
  case class BidAck(accepted : BigDecimal) extends BidResult{}
  case class BidNack(price : BigDecimal) extends BidResult{}
  trait ParticipationResult
  case class Won(price : BigDecimal) extends ParticipationResult{}
  case class Lost() extends ParticipationResult{}

}

class Auction(val item: String, startingPrice: BigDecimal) extends Actor {
  import Auction._

  var seller: Option[ActorRef] = None
  var currentPrice: BigDecimal = startingPrice
  var endDate: DateTime = DateTime.now + 1000.years

  private def checkBid(price: BigDecimal): BidResult = {
    if (price > currentPrice && DateTime.now < endDate) {
      currentPrice = price
      BidAck(price)
    } else {
      BidNack(currentPrice)
    }
  }

  def receive = LoggingReceive {
    case Start(end) =>
      seller = Some(sender)
      endDate = end
      context.become(created())
  }

  def created(): Receive = LoggingReceive {
    case Bid(proposed) =>
      sender ! checkBid(proposed)
      context.become(activated(proposed))
  }

  def activated(current : BigDecimal): Receive = LoggingReceive {
    case Bid(proposed) =>
      sender ! checkBid(proposed)
      context.become(activated(proposed))
  }

  def ignored(starting : BigDecimal): Receive = LoggingReceive {
    case Bid(proposed) => ClosingInfo(endDate)
  }

  def sold(endPrice : BigDecimal): Receive = LoggingReceive {
    case Bid(proposed) => ClosingInfo(endDate)
  }

}
