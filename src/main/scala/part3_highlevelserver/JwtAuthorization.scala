package part3_highlevelserver

/**
 * Notes - JSON web tokens
 *
 * JSON Web Tokens
 * - authorization
 * - exchange of data
 * ---> Not Akka HTTP specific, but often used in web apps/microservices
 *
 * JWT Authorization
 * Principles
 * - you authenticate to the server (username + password, OAuth, you blood etc)
 * - server sends you back a string aka token
 * - you then use that string for secure endpoints
 *    - special HTTP header Authorization: (token)
 *    - the endpoint will check the token for permissions
 *    - you call is allowed / rejected
 *
 * Result: Authorization
 * - Not authentication: You receive the token after authenticating
 *
 * JWT Structure
 * Part 1: header JSON
 * {
 *  "typ": "JWT" <-- type
 *  "alg": "HS256" <--- hashing algorithm --- HMAC SHA256
 * }
 * This is then encoded as Base64
 *
 * Part 2: payload (registered claims)
 * {
 *  "iss": "rockthejvm.com" <-- issuer
 *  "exp": 123234456123, <--- expiration date
 *  "name": "Weevie Stunder" <--- public claims. Name/user role type, any kind of permissions
 *  "admin": true
 * }
 * Also encoded as Base64
 *
 * Part 3: signature
 * - take encoded header + "." + encoded claims
 * - sign with the algorithm in the header and a secret key
 * - encoded with base64
 *
 * encodedHeader + "." + encodedClaims ----HS256 (secretKey) ---> encryptedString ----Base64Encode--> base64EncryptedString
 *
 */

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.stream.ActorMaterializer
import pdi.jwt.JwtAlgorithm
import spray.json._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtSprayJson}
import spray.json._

import java.util.concurrent.TimeUnit
import scala.util.{Failure, Success}

object SecurityDomain extends DefaultJsonProtocol {
  case class LoginRequest(username: String, password: String)
  implicit val loginReqeustFormat = jsonFormat2(LoginRequest)
}

object JwtAuthorization extends App with SprayJsonSupport {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  import SecurityDomain._

  val superSecretPasswordDb = Map(
    "admin" -> "admin",
    "daniel" -> "Rockthejvm1!"
  )

  val algorithm = JwtAlgorithm.HS256
  val secretKey = "rockthejvmsecret"

  def checkPassword(username: String, password: String): Boolean =
    superSecretPasswordDb.contains(username) && superSecretPasswordDb(username) == password

  def createToken(username: String, expirationPeriodInDays: Int): String = {
    val claims = JwtClaim(
      expiration = Some(System.currentTimeMillis() / 1000 + TimeUnit.DAYS.toSeconds(expirationPeriodInDays)),
      issuedAt = Some(System.currentTimeMillis() / 1000),
      issuer = Some("rockthejvm.com"),
    )

    JwtSprayJson.encode(claims, secretKey, algorithm) // JWT String
  }

  def isTokenExpired(token: String): Boolean = JwtSprayJson.decode(token, secretKey, Seq(algorithm)) match {
    case Success(claims) => claims.expiration.getOrElse(0L) < System.currentTimeMillis() / 1000
    case Failure(_) => true
  }

  def isTokenValid(token: String): Boolean = JwtSprayJson.isValid(token, secretKey, Seq(algorithm))

  val loginRoute =
    post {
      entity(as[LoginRequest]) {
        case LoginRequest(username, password) if checkPassword(username, password) =>
          val token = createToken(username, 1)
          respondWithHeader(RawHeader("Access-Token", token)) {
            complete(StatusCodes.OK)
          }
        case _ => complete(StatusCodes.Unauthorized)
      }
    }

  val authenticatedRoute =
    (path("secureEndpoint") & get) {
      optionalHeaderValueByName("Authorization") {
        case Some(token) =>
          if (isTokenValid(token)) {
            if (isTokenExpired(token)) {
              complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "Token expired."))
            } else {
              complete("User accessed authorized endpoint!")
            }
          } else {
            complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "Token is invalid, or has been tampered with."))
          }
        case _ => complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "No token provided!"))
      }
    }

  val route = loginRoute ~ authenticatedRoute

  Http().bindAndHandle(route, "localhost", 8080)

}
