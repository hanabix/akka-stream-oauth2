package zhongl.stream.oauth2

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Cookie
import com.auth0.jwt.JWT
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._

class JwtCookieSpec extends WordSpec with Matchers {

  classOf[JwtCookie].getSimpleName should {
    "work" in {
      val jc             = JwtCookie("jwt", "zhongl.me")
      val jc(decodedJWT) = HttpRequest(headers = List(Cookie(jc.generate(JWT.create().withIssuer("iss")).pair())))
      decodedJWT.getIssuer shouldBe "iss"
    }

    "expired" in {
      val jc = JwtCookie("jwt", "zhongl.me", expiration = 1.milli)
      val token = jc.generate(JWT.create().withIssuer("iss"))
      intercept[MatchError] {
        Thread.sleep(1001)
        val jc(_) = HttpRequest(headers = List(Cookie(token.pair())))
      }

    }
  }
}
