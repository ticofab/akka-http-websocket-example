package io.ticofab.example

import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketRequest}
import akka.http.scaladsl.server.Directives.{path, _}
import akka.pattern.ask
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, OverflowStrategy}
import akka.{Done, NotUsed}
import io.ticofab.example.Route.GetWebsocketFlow

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object AkkaHttpWSExampleApp extends App {
  implicit val as = ActorSystem("example")
  implicit val am = ActorMaterializer()

  Http()
    .bindAndHandle(Route.websocketRoute, "0.0.0.0", 8123)
    .onComplete {
      case Success(value) => println(value)
      case Failure(err) => println(err)
    }

  // test client
  // print each incoming strict text message
  val printSink: Sink[Message, Future[Done]] =
  Sink.foreach { case message: TextMessage.Strict => println("client received: " + message.text) }

  val helloSource: Source[Message, NotUsed] = Source.single(TextMessage("hello world!"))

  // the Future[Done] is the materialized value of Sink.foreach
  // and it is completed when the stream completes
  val flow: Flow[Message, Message, Future[Done]] =
  Flow.fromSinkAndSourceMat(printSink, helloSource)(Keep.left)

  // upgradeResponse is a Future[WebSocketUpgradeResponse] that
  // completes or fails when the connection succeeds or fails
  // and closed is a Future[Done] representing the stream completion from above
  val (upgradeResponse, closed) =
  Http().singleWebSocketRequest(WebSocketRequest("ws://localhost:8123/connect"), flow)

  val connected = upgradeResponse.map { upgrade =>
    // just like a regular http request we can access response status which is available via upgrade.response.status
    // status code 101 (Switching Protocols) indicates that server support WebSockets
    if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
      println("switching protocols")
      Done
    } else {
      throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
    }
  }

  // in a real application you would not side effect here
  // and handle errors more carefully
  connected.onComplete(println)
  closed.foreach(_ => println("closed"))

}

object Route {

  case object GetWebsocketFlow

  implicit val as = ActorSystem("example")
  implicit val am = ActorMaterializer()

  val websocketRoute =
    pathEndOrSingleSlash {
      complete("WS server is alive\n")
    } ~ path("connect") {

      val handler = as.actorOf(Props[ClientHandlerActor])
      val futureFlow = (handler ? GetWebsocketFlow) (3.seconds).mapTo[Flow[Message, Message, _]]

      onComplete(futureFlow) {
        case Success(flow) => handleWebSocketMessages(flow)
        case Failure(err) => complete(err.toString)
      }

    }
}

class ClientHandlerActor extends Actor {

  implicit val as = context.system
  implicit val am = ActorMaterializer()

  val (down, publisher) = Source
    .actorRef[String](1000, OverflowStrategy.fail)
    .toMat(Sink.asPublisher(fanout = false))(Keep.both)
    .run()

  // test
  var counter = 0
  as.scheduler.schedule(0.seconds, 0.5.second, new Runnable {
    override def run() = {
      counter = counter + 1
      self ! counter
    }
  })

  override def receive = {
    case GetWebsocketFlow =>

      val flow = Flow.fromGraph(GraphDSL.create() { implicit b =>
        val textMsgFlow = b.add(Flow[Message]
          .mapAsync(1) {
            case tm: TextMessage => tm.toStrict(3.seconds).map(_.text)
            case bm: BinaryMessage =>
              // consume the stream
              bm.dataStream.runWith(Sink.ignore)
              Future.failed(new Exception("yuck"))
          })

        val pubSrc = b.add(Source.fromPublisher(publisher).map(TextMessage(_)))

        textMsgFlow ~> Sink.foreach[String](self ! _)
        FlowShape(textMsgFlow.in, pubSrc.out)
      })

      sender ! flow

    // replies with "hello XXX"
    case s: String =>
      println(s"client actor received $s")
      down ! "Hello " + s + "!"

    // passes any int down the websocket
    case n: Int =>
      println(s"client actor received $n")
      down ! n.toString
  }
}
