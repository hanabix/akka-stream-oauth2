package zhongl.stream.oauth2

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Accept, Host, Location}
import akka.http.scaladsl.util.FastFuture
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class GuardSpec extends WordSpec with BeforeAndAfterAll with Matchers {
  implicit val system = ActorSystem(getClass.getSimpleName)
  implicit val mat    = ActorMaterializer()
  implicit val ec     = system.dispatcher

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

    "redirect get html request" in {
      val Right(f) = runGuard(HttpRequest(headers = Accept(MediaRanges.`text/*`) :: Nil))
      Await.result(f, 1.second).status shouldBe Found
    }

  }

  private def runGuard(req: HttpRequest) = {
    val guard = Guard.graph(Example, _.uri.path == Path / "ignore")
    Await.result(Source.single(req).via(Flow.fromGraph(Guard.asFlow(guard))).runWith(Sink.head), 1.second)
  }

  override protected def afterAll(): Unit = system.terminate()

  class ValidToken extends FreshToken.Token {
    override def isInvalid: Boolean = false
  }

  object Example extends OAuth2[ValidToken] {

    override def refresh = FastFuture.successful(new ValidToken)

    override def authenticate(token: ValidToken, request: HttpRequest): Future[HttpResponse] = FastFuture.successful(HttpResponse())

    override def authorization(state: String): Location = Location(Uri())

    override val invalidToken: Throwable = new RuntimeException

    override def redirect: Uri = Uri("http://guard/authorized")
  }
}


