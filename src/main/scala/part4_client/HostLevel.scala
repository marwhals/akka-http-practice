package part4_client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source}
import spray.json.enrichAny

import java.util.UUID
import scala.util.{Failure, Success, Try}

/**
 * Host-level API benefits
 * - the freedom from managing individual connections
 * - the ability to attach data to requests (aside from payloads)
 *
 * Akka HTTP client: host-level API
 *
 * val poolFlow : Flow[(HttpRequest, Int), (Try[HttpResponse], Int), Http.HostConnectionPool] = Http().cachedHostConnectionPool[Int]("http://my.web.service.com")
 *                ^^^Attach data to each request--^^Akka HTTP will attach to the response the same data you attached to the request
 *
 *
 * Host-level API benefits:
 * - the freedom from managing individual connections
 * - best for high-volume, short-lived requests
 *
 * Do not use the host-level API for:
 * - one-off requests (use the request level API)
 * - long-lived requests (use the connection level API)
 *
 */

object HostLevel extends App with PaymentJsonProtocol {

  implicit val system: ActorSystem = ActorSystem("HostLevel")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val poolFlow: Flow[(HttpRequest, Int), (Try[HttpResponse], Int), Http.HostConnectionPool] = Http().cachedHostConnectionPool[Int]("www.google.com") // connections are now in a cached pool

  Source(1 to 10)
    .map(i => (HttpRequest(), i))
    .via(poolFlow)
    .map {
      case (Success(response), value) =>
        // Very important ---- withouth below line this can lead to leaking connections in your connection pool
        response.discardEntityBytes()
        s"Request $value has received response: $response"
      case (Failure(ex), value) =>
        s"Request $value has failed: $ex"
    }
  //    .runWith(Sink.foreach[String](println))


  import PaymentSystemDomain._
  val creditCards = List(
    CreditCard("4242-4242-4242-4242", "424", "tx-test-account"),
    CreditCard("1234-1234-1234-1234", "123", "tx-daniels-account"),
    CreditCard("1234-1234-4321-4321", "321", "my-awesome-account")
  )

  val paymentRequests = creditCards.map(creditCard => PaymentRequest(creditCard, "rtjvm-store-account", 99))
  val serverHttpRequests = paymentRequests.map(paymentRequest =>
    (
      HttpRequest(
        HttpMethods.POST,
        uri = Uri("/api/payments"),
        entity = HttpEntity(
          ContentTypes.`application/json`,
          paymentRequest.toJson.prettyPrint
        )
      ),
      UUID.randomUUID().toString
    )
  )

  Source(serverHttpRequests)
    .via(Http().cachedHostConnectionPool[String]("localhost", 8080))
    .runForeach { // (Try[HttpResponse], String)
      case (Success(response@HttpResponse(StatusCodes.Forbidden, _, _, _)), orderId) =>
        println(s"The order ID $orderId was not allowed to proceed: $response")
      case (Success(response), orderId) =>
        println(s"The order ID $orderId was successful and returned the response: $response")
      // do something with the order ID: dispatch it, send a notification to the customer, etc
      case (Failure(ex), orderId) =>
        println(s"The order ID $orderId could not be completed: $ex")
    }

  // Host level API should be used for high-volume, low latency requests

}
