package zhongl.stream.oauth2

import java.util.Date

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.{Cookie, HttpCookie}
import com.auth0.jwt.{JWT, JWTCreator}
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT

import scala.concurrent.duration._
import scala.util.{Random, Try}

case class JwtCookie(
    name: String,
    domain: String,
    algorithm: Algorithm = Algorithm.HMAC256(new Random(System.nanoTime()).nextString(32)),
    expiration: FiniteDuration = 7.days
) {

  private val verifier = JWT.require(algorithm).acceptExpiresAt(expiration.toSeconds).build()

  def unapply(request: HttpRequest): Option[DecodedJWT] = {
    request.headers
      .find(_.isInstanceOf[Cookie])
      .flatMap {
        case c: Cookie => c.cookies.find(_.name == name).map(_.value)
      }
      .flatMap { t =>
        Try { verifier.verify(t) } toOption
      }
  }

  def generate(b: JWTCreator.Builder): HttpCookie = {
    val token = b.withExpiresAt(new Date(System.currentTimeMillis() + expiration.toMillis)).sign(algorithm)
    HttpCookie(name, token, domain = Some(domain))
  }

}
