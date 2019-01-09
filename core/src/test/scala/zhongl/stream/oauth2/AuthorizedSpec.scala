package zhongl.stream.oauth2
import akka.http.scaladsl.model.{HttpRequest, Uri}
import org.scalatest.{Matchers, WordSpec}

class AuthorizedSpec extends WordSpec with Matchers {
  "Authorized redirect" should {
    "only path" in {
      Authorized(Uri("/path")).unapply(HttpRequest(uri = Uri("http://any/path"))) shouldBe true
    }

    "authority and path" in {
      Authorized(Uri("http://spec/path")).unapply(HttpRequest(uri = Uri("http://spec/path"))) shouldBe true
    }
  }
}
