package auctionHouse.search

import akka.actor.{Props, Actor, ActorRef}
import akka.event.LoggingReceive
import akka.routing.{ActorRefRoutee, Broadcast, Router, RoutingLogic}

object MasterSearch {

  def props(numberOfRoutees: Int, dispatchingStrategy: RoutingLogic): Props =
      Props(new MasterSearch(numberOfRoutees, dispatchingStrategy))

  val path = "akka://auctionHouse/*/*/auctionSearch"
}

class MasterSearch(numberOfWorkers: Int, dispatchingStrategy: RoutingLogic) extends Actor{
  import AuctionSearch._

  val router = createRouterWith(workersCount = numberOfWorkers, routingStrategy = dispatchingStrategy)

  private def createRouterWith(workersCount: Int, routingStrategy: RoutingLogic): Router = {
    val workers = Vector.fill(workersCount)(singleWorker)
    Router(routingStrategy, workers)
  }

  private def singleWorker: ActorRefRoutee = {
    val worker = context.actorOf(Props[AuctionSearch])
    ActorRefRoutee(worker)
  }

  def receive = LoggingReceive {
    case m@Find(keyword) =>
      router.route(m, sender())
    case m@Register(name) =>
      router.route(Broadcast(m), self)
    case m@Unregister(name) =>
      router.route(Broadcast(m), self)
  }
}
