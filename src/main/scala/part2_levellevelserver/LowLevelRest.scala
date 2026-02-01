package part2_levellevelserver

import akka.actor.TypedActor.dispatcher
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import part2_levellevelserver.GuitarDB._
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps // step 1

case class Guitar(make: String, model: String, quantity: Int = 0)

object GuitarDB {
  case class CreateGuitar(guitar: Guitar)

  case class GuitarCreated(id: Int)

  case class FindGuitar(id: Int)

  case object FindAllGuitars

  case class AddQuantity(id: Int, quantity: Int)

  case class FindGuitarsInStock(inStock: Boolean)
}

class GuitarDB extends Actor with ActorLogging {

  import GuitarDB._

  var guitars: Map[Int, Guitar] = Map()
  var currentGuitarId: Int = 0

  override def receive: Receive = {
    case FindAllGuitars =>
      log.info("Searching for all guitars")
      sender() ! guitars.values.toList

    case FindGuitar(id) =>
      log.info(s"Searching guitar by id: $id")
      sender() ! guitars.get(id)

    case CreateGuitar(guitar) =>
      log.info(s"Adding guitar $guitar with id $currentGuitarId")
      guitars = guitars + (currentGuitarId -> guitar)
      sender() ! GuitarCreated(currentGuitarId)
      currentGuitarId += 1

    case AddQuantity(id, quantity) =>
      log.info(s"Trying to add quantity items for guitar $id")
      val guitar: Option[Guitar] = guitars.get(id)
      val newGuitar: Option[Guitar] = guitar.map {
        case Guitar(make, model, q) => Guitar(make, model, q + quantity)
      }

      newGuitar.foreach(guitar => guitars = guitars + (id -> guitar))
      sender() ! newGuitar

    case FindGuitarsInStock(inStock) =>
      log.info(s"Searching for all guitars ${if (inStock) "in" else "out of"} stock")
      if (inStock)
        sender() ! guitars.values.filter(_.quantity > 0)
      else
        sender() ! guitars.values.filter(_.quantity == 0)

  }
}

// step 2
trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
  // step 3
  implicit val guitarFormat = jsonFormat3(Guitar) // x parameters if your case class has x parameters
}

object LowLevelRest extends App with GuitarStoreJsonProtocol {

  implicit val system = ActorSystem("LowLevelRest")
  implicit val materializer = ActorMaterializer()

  /*
  GET on localhost:8080/api/guitar => All the guitars in the store
  GET on localhost:8080/api/guitar?id=X => fetches the guitar associated with the id X
  POST on localhost:8080/api/guitar => insert the guitar into the store
   */

  // Using JSON for marshalling
  val simpleGuitar = Guitar("Fender", "Stratocaster")
  println(simpleGuitar.toJson.prettyPrint)

  // unmarshalling
  val simpleGuitarJsonString =
    """
      |{
      |  "make": "Fender",
      |  "model": "Stratocaster",
      |  "quantity": 3
      |}
    """.stripMargin
  println(simpleGuitarJsonString.parseJson.convertTo[Guitar])

  /**
   * Setup
   */
  val guitarDb = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = List(
    Guitar("Fender", "Stratocaster"),
    Guitar("Gibson", "Les Paul"),
    Guitar("Martin", "LX1")
  )

  guitarList.foreach { guitar =>
    guitarDb ! CreateGuitar(guitar)
  }

  /**
   * Server code
   * - use futures when interacting with an external resource otherwise the response times will be very slow
   */
  implicit val defaultTimeout: Timeout = Timeout(2.seconds)

  def getGuitar(query: Query): Future[HttpResponse] = {
    val guitarId = query.get("id").map(_.toInt) // Option[Int]
    guitarId match {
      case None => Future(HttpResponse(StatusCodes.NotFound))
      case Some(id: Int) =>
        val guitarFuture: Future[Option[Guitar]] = (guitarDb ? FindGuitar(id)).mapTo[Option[Guitar]]
        guitarFuture.map {
          case None => HttpResponse(StatusCodes.NotFound)
          case Some(guitar) =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitar.toJson.prettyPrint
              )
            )
        }
    }
  }

  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.POST, uri@Uri.Path("/api/guitar/inventory"), _, _, _) =>
      val query = uri.query()
      val guitarId: Option[Int] = query.get("id").map(_.toInt)
      val guitarQuantity: Option[Int] = query.get("quantity").map(_.toInt)

      val validGuitarResponseFuture: Option[Future[HttpResponse]] = for {
        id <- guitarId
        quantity <- guitarQuantity
      } yield {
        val newGuitarFuture: Future[Option[Guitar]] = (guitarDb ? AddQuantity(id, quantity)).mapTo
        newGuitarFuture.map(_ => HttpResponse(StatusCodes.OK))
      }

      validGuitarResponseFuture.getOrElse(Future(HttpResponse(StatusCodes.BadRequest)))

    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar/inventory"), _, _, _) =>
      val query = uri.query()
      val inStockOption = query.get("inStock").map(_.toBoolean)

      inStockOption match {
        case Some(inStock) =>
          val guitarsFuture: Future[List[Guitar]] = (guitarDb ? FindGuitarsInStock(inStock)).mapTo[List[Guitar]]
          guitarsFuture.map { guitars =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitars.toJson.prettyPrint
              )
            )
          }
        case None => Future(HttpResponse(StatusCodes.BadRequest))
      }

    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar"), _, _, _) =>
      /*
      Query parameter handling code here
       */
      val query = uri.query() // query object <=> map[String, String]
      if (query.isEmpty) {

        val guitarsFuture: Future[List[Guitar]] = ((guitarDb ? FindAllGuitars)).mapTo[List[Guitar]]
        guitarsFuture.map { guitars =>
          HttpResponse(
            entity = HttpEntity(
              ContentTypes.`text/html(UTF-8)`,
              guitars.toJson.prettyPrint
            )
          )
        }
      } else {
        // fetch guitar associated to the guitar id
        // localhost:8080/api/guitar?id=45
        getGuitar(query)
      }
    case HttpRequest(HttpMethods.POST, Uri.Path("/api/guitar"), _, entity, _) =>
      // entities are a Source[ByteString]
      val strictEntityFuture = entity.toStrict(3 seconds)
      strictEntityFuture.flatMap { strictEntity =>
        val guitarJsonString = strictEntity.data.utf8String
        val guitar = guitarJsonString.parseJson.convertTo[Guitar]

        val guitarCreatedFuture: Future[GuitarCreated] = (guitarDb ? CreateGuitar(guitar)).mapTo[GuitarCreated]
        guitarCreatedFuture.map { _ =>
          HttpResponse(StatusCodes.OK)
        }

      }

    case request: HttpRequest => // This case is also very important because if you don't reply to an existing request that will be interpreted as back pressure which will go all they way down to the TCP layer and slow the server down
      request.discardEntityBytes()
      Future {
        HttpResponse(status = StatusCodes.NotFound)
      }

  }
  Http().bindAndHandleAsync(requestHandler, "localhost", 8080)

  /**
   * Exercise: enhance the guitar case class with a quantity field, by default it should be 0
   * - GET to /api/guitar/inventory?inStock=true/false which return the guitars in stock as a JSON
   * - POST to /api/guitar/inventory?id=X&quantity=Y which adds Y guitars to the stock for guitar with id X
   */

}
