package auctionHouse

import akka.actor.{Actor, ActorRef}
import akka.event.LoggingReceive
import auctionHouse.Notifier.Notify

object Notifier {
  case class Notify(name: String, price : BigDecimal, leader: Option[ActorRef])
}

class Notifier extends Actor{

  def receive = LoggingReceive {
    case note @ Notify(name,price,leader) =>
  }

}
