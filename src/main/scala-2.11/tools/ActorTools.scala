package tools

import akka.actor.ActorRef

object ActorTools {
  implicit class ReadableActorRef(ref: ActorRef) {
    def id: String = {
      s"-${Integer.toHexString(ref.hashCode).toUpperCase}-"
    }
    def name: String = {
      s"-${ref.path.parent.name}/${ref.path.name}-"
    }
  }
}
