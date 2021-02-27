package zhongl.stream.oauth2

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Cookie
import com.auth0.jwt.JWT

import scala.concurrent.duration._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JwtCookieSpec extends AnyWordSpec with Matchers {

  classOf[JwtCookie].getSimpleName should {
    "work" in {
      val jc             = JwtCookie("jwt", "zhongl.me")
      val token          = jc.generate(JWT.create().withIssuer("iss")).pair
      val jc(decodedJWT) = HttpRequest(headers = List(Cookie(token)))
      decodedJWT.getIssuer shouldBe "iss"
    }

    "expired" in {
      val jc    = JwtCookie("jwt", "zhongl.me", expiration = 1.milli)
      val token = jc.generate(JWT.create().withIssuer("iss"))
      Thread.sleep(1001)
      jc.unapply(HttpRequest(headers = List(Cookie(token.pair())))) shouldBe None
    }
  }
}
