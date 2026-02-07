package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import akka.stream.ActorMaterializer

/**
 * Notes
 * - Exceptions are not rejections
 * - If exceptions are not handled by a custom exception handler then the  default exception handler is a 500
 */

object HandlingExceptions extends App {

  implicit val system = ActorSystem("HandlingExceptions")
  implicit val materializer = ActorMaterializer()


  val simpleRoute =
    path("api" / "people") {
      get {
        // directive that throws some exception
        throw new RuntimeException("Getting all the people took too long")
      } ~
        post {
          parameter('id) { id =>
            if (id.length > 2)
              throw new NoSuchElementException(s"Parameter $id cannot be found in the database, TABLE FLIP!")

            complete(StatusCodes.OK)
          }
        }
    }

  implicit val customExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: RuntimeException =>
      complete(StatusCodes.NotFound, e.getMessage)
    case e: IllegalArgumentException =>
      complete(StatusCodes.BadRequest, e.getMessage)
  }

  //  Http().bindAndHandle(simpleRoute, "localhost", 8080)

  val runtimeExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: RuntimeException =>
      complete(StatusCodes.NotFound, e.getMessage)
  }

  val noSuchElementExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: NoSuchElementException =>
      complete(StatusCodes.BadRequest, e.getMessage)
  }

  val delicateHandleRoute =
    handleExceptions(runtimeExceptionHandler) {
      path("api" / "people") {
        get {
          // directive that throws some exception
          throw new RuntimeException("Getting all the people took too long")
        } ~
          handleExceptions(noSuchElementExceptionHandler) {
            post {
              parameter('id) { id =>
                if (id.length > 2)
                  throw new NoSuchElementException(s"Parameter $id cannot be found in the database, TABLE FLIP!")

                complete(StatusCodes.OK)
              }
            }
          }
      }
    }

  Http().bindAndHandle(delicateHandleRoute, "localhost", 8080)
}
