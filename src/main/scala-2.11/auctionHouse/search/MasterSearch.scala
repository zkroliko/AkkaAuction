package auctionHouse.search

import akka.actor.{Props, Actor, ActorRef}
import akka.event.LoggingReceive
import akka.routing.{ActorRefRoutee, Broadcast, Router, RoutingLogic}
import auctionHouse.routingBench.RoutingBench
import auctionHouse.routingBench.RoutingBench.RegistrationFinished

import scala.collection.immutable.Map

object MasterSearch {

  def props(numberOfRoutees: Int, dispatchingStrategy: RoutingLogic,parent: ActorRef): Props =
      Props(new MasterSearch(numberOfRoutees, dispatchingStrategy,parent))

  val path = "akka://auctionHouse/*/*/auctionSearch"
}

class MasterSearch(numberOfWorkers: Int, dispatchingStrategy: RoutingLogic, parent: ActorRef) extends Actor{
  import AuctionSearch._

  val router = createRouterWith(workersCount = numberOfWorkers, routingStrategy = dispatchingStrategy)

  var registrations = 50000
  var registrationAcks = 0
  def requiredAcks = registrations*numberOfWorkers

  def finished: Boolean = registrationAcks >= requiredAcks

  private def createRouterWith(workersCount: Int, routingStrategy: RoutingLogic): Router = {
    val workers = Vector.fill(workersCount)(singleWorker)
    Router(routingStrategy, workers)
  }

  private def singleWorker: ActorRefRoutee = {
    val worker = context.actorOf(Props(new AuctionSearch(self)))
    ActorRefRoutee(worker)
  }

  def receive = LoggingReceive {
    case m@Find(keyword) =>
      router.route(m, sender())
    case m@Register(name) =>
      router.route(Broadcast(m), sender)
    case m@Unregister(name) =>
      router.route(Broadcast(m), sender)
    case RoutingBench.RegistrationFinished =>
      registrationAcks += 1
      println(registrationAcks + " registrations")
      if (finished) {
        parent ! RegistrationFinished
      }
  }
}
