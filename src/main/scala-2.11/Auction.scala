
import akka.actor._
import akka.dispatch.sysmsg.Create
import akka.event.LoggingReceive
import scala.concurrent.Await
import scala.concurrent.duration._


object Action {

  case class Create(starting : BigDecimal) {}

  case class AskPrice() {}

  case class PriceInfo(current : BigDecimal) {}

  case class Bid(proposed : BigDecimal) {}

  case class BidAck(accepted : BigDecimal) {}

  case class BidNack(price : BigDecimal){}

}

class Auction extends Actor {
  import Action._

  def receive = LoggingReceive {
    case Create(starting) => context.become(created(starting))
  }

  def created(starting : BigDecimal): Receive = LoggingReceive {
    case Bid(proposed) =>
  }

  def activated(starting : BigDecimal): Receive = LoggingReceive {
    case Bid(proposed) =>
  }

}
