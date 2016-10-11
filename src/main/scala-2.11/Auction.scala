
import akka.actor._
import akka.dispatch.sysmsg.Create
import akka.event.LoggingReceive
import org.joda.time.DateTime
import scala.concurrent.Await
import scala.concurrent.duration._


object Action {

  case class Start(end: DateTime) {}

  case class AskPrice() {}

  case class PriceInfo(current : BigDecimal) {}

  case class ClosingInfo(time: DateTime) {}

  case class Bid(proposed : BigDecimal) {}

  case class BidAck(accepted : BigDecimal) {}

  case class BidNack(price : BigDecimal){}

}

class Auction extends Actor {
  import Action._

  var startingPrice = BigDecimal

  def receive = LoggingReceive {
    case Start(end) => context.become(created(end))
  }

  def created(end: DateTime): Receive = LoggingReceive {
    case Bid(proposed) => context.become(activated(proposed))
  }

  def activated(current : BigDecimal): Receive = LoggingReceive {
    case Bid(proposed) => context.become(activated(proposed))
  }

  def ignored(starting : BigDecimal): Receive = LoggingReceive {
    case Bid(proposed) => context.become(activated(proposed))
  }

  def sold(endPrice : BigDecimal): Receive = LoggingReceive {
    case Bid(proposed) => context.become(activated(proposed))
  }

}
