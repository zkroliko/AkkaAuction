package auctionHouse

import akka.actor.{Actor, ActorRef}
import akka.event.LoggingReceive
import akka.util.Timeout
import auctionHouse.Notifier.Notify
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import ExecutionContext.Implicits.global

object Notifier {
  case class Notify(name: String, price : BigDecimal, leader: Option[ActorRef])
}

class Notifier extends Actor{

  val publisher = context.actorSelection("akka.tcp://auctionPublisher@127.0.0.1:2552/user/auctionPublisher")

  implicit val timeout = Timeout(5 seconds)
  publisher.resolveOne().onComplete {
    case Success(pub) => println("Success")
    case Failure(e) => throw e
  }

  def receive = LoggingReceive {
    case note @ Notify(name,price,leader) =>
  }

}
