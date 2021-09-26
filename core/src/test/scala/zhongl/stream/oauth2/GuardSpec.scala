package zhongl.stream.oauth2

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.util.FastFuture
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, Sink, Source}
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GuardSpec extends AnyWordSpec with BeforeAndAfterAll with Matchers {
  implicit private val system = ActorSystem(getClass.getSimpleName)
  implicit private val mat    = Materializer(system)
  implicit private val ec     = system.dispatcher

  "Guard" should {

    "let ignore request pass through" in {
      val request = HttpRequest(uri = Uri("/ignore"))
      runGuard(request) shouldBe Left(request)
    }

    "authenticate request" in {
      val Right(f) = runGuard(HttpRequest(uri = Uri("/authorized"), headers = Host("guard") :: Nil))
      Await.result(f, 1.second).status shouldBe OK
    }

    "reject request" in {
      val Right(f) = runGuard(HttpRequest())
      Await.result(f, 1.second).status shouldBe Unauthorized
    }

    "challenge get html request" in {
      val Right(f) = runGuard(HttpRequest(uri = "http://a.b", headers = List(Accept(MediaRanges.`text/*`))))
      Await.result(f, 1.second) shouldBe HttpResponse(Found, List(Location("http://a.b")))
    }

    "challenge to location with origin host" in {
      val Right(f) = runGuard(HttpRequest(uri = "http://a.b", headers = List(Accept(MediaRanges.`text/*`), `X-Forwarded-Host`(Uri.Host("b.c")))))
      Await.result(f, 1.second) shouldBe HttpResponse(Found, List(Location("http://b.c")))
    }

    "challenge to location with origin proto" in {
      val Right(f) = runGuard(HttpRequest(uri = "http://a.b", headers = List(Accept(MediaRanges.`text/*`), `X-Forwarded-Proto`("https"))))
      Await.result(f, 1.second) shouldBe HttpResponse(Found, List(Location("https://a.b")))
    }

  }

  private def runGuard(req: HttpRequest)  = {
    val guard = Guard.graph(Example, _.uri.path == Path / "ignore")
    Await.result(Source.single(req).via(Flow.fromGraph(Guard.asFlow(guard))).runWith(Sink.head), 1.second)
  }

  override protected def afterAll(): Unit = system.terminate()

  class ValidToken extends FreshToken.Token {
    override def isInvalid: Boolean = false
  }

  object Example extends OAuth2[ValidToken] {

    override def refresh: Future[ValidToken] = FastFuture.successful(new ValidToken)

    override def authenticate(token: ValidToken, request: HttpRequest): Future[HttpResponse] = FastFuture.successful(HttpResponse())

    override def authorization(state: String): Location = Location(Uri(state))

    override def redirect: Uri = Uri("http://guard/authorized")
  }
}
