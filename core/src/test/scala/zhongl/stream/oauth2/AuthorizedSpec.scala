package zhongl.stream.oauth2
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.model.{HttpRequest, Uri}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AuthorizedSpec extends AnyWordSpec with Matchers {
  "Authorized redirect" should {
    "only matches path" in {
      Authorized(Uri("/path")).unapply(HttpRequest(uri = Uri("http://any/path"))) shouldBe true
    }

    "matches both authority and path" in {
      Authorized(Uri("http://spec/path")).unapply(HttpRequest(uri = Uri("http://spec/path"))) shouldBe true
    }

    "matches Host header first" in {
      Authorized(Uri("http://spec/path")).unapply(HttpRequest(uri = Uri("http://any/path"), headers = List(Host("spec")))) shouldBe true
    }
  }
}
