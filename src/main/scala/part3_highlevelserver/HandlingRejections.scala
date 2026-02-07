package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MethodRejection, MissingQueryParamRejection, Rejection, RejectionHandler}
import akka.stream.ActorMaterializer

/**
 * Notes: Rejections
 * If a request doesn't match a filter directive, its rejected
 * - reject = pass the request to another branch in the routing tree
 * - a rejection is NOT a failure
 *
 * Rejections are aggregate
 * - If request is not a GET, add rejection
 * - If request is not a POST, add rejection
 * - If request has a query param, clear the rejections list (other rejections might be added within)
 *
 * We can choose how to handle the rejections list
 *
 *
 * -----
 * Handle rejections
 * - Rejection handler = function between a rejection list and a route
 * - rejection handling directives --- careful with ordering
 *
 *
 */

object HandlingRejections extends App {

  implicit val system = ActorSystem("HandlingRejections")
  implicit val materializer = ActorMaterializer()

  val simpleRoute =
    path("api" / "myEndpoint") {
      get {
        complete(StatusCodes.OK)
      } ~
        parameter('id) { _ =>
          complete(StatusCodes.OK)
        }
    }

  // Rejection handlers
  val badRequestHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"I've encountered rejections: $rejections")
    Some(complete(StatusCodes.BadRequest))
  }

  val forbiddenHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"I have encountered rejections: $rejections")
    Some(complete(StatusCodes.Forbidden))
  }

  val simpleRouteWithHandlers =
    handleRejections(badRequestHandler) { // handle rejections from the top level
      // define server logic inside
      path("api" / "myEndpoint") {
        get {
          complete(StatusCodes.OK)
        } ~
          post {
            handleRejections(forbiddenHandler) { // handle rejections WITHIN
              parameter('myParam) { _ =>
                complete(StatusCodes.OK)
              }
            }
          }
      }
    }

//  Http().bindAndHandle(simpleRouteWithHandlers, "localhost", 8080)

  // list(method rejection, query param rejection)
  implicit val customerRejectionHandler = RejectionHandler.newBuilder()
    .handle {
      case m: MethodRejection =>
        println(s"I got a method rejection: $m")
        complete("Rejected method!")
    }
    .handle {
      case m: MissingQueryParamRejection =>
        println(s"I got a query rejection: $m")
        complete("Rejection query param!")
    }
    .result()

  // sealing a route

  Http().bindAndHandle(simpleRoute, "localhost", 8080)

}
