package io.ticofab.example

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import akka.stream.ActorMaterializer
import org.scalatest.{Matchers, WordSpec}

class AkkaHttpWSExampleSpec extends WordSpec with Matchers
  with Directives with ScalatestRouteTest {

  implicit val as = ActorSystem("example-test")
  implicit val am = ActorMaterializer()


  "A handler Websocket" should {

    "Send the expected messages" in {

      // tests:
      // create a testing probe representing the client-side
      val wsClient = WSProbe()

      // WS creates a WebSocket request for testing
      WS("/connect", wsClient.flow) ~> Route.websocketRoute ~>
        check {
          // check response for WS Upgrade headers
          isWebSocketUpgrade shouldEqual true

          wsClient.expectMessage("1")
          wsClient.expectMessage("2")
          wsClient.expectMessage("3")

          wsClient.sendMessage("Peter")
          wsClient.expectMessage("Hello Peter!")

          wsClient.sendMessage("John")
          wsClient.expectMessage("Hello John!")
        }
    }



  }


}
