package zhongl.stream.oauth2

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.util.FastFuture
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

class GuardSpec extends WordSpec with BeforeAndAfterAll with Matchers {
  implicit val system = ActorSystem(getClass.getSimpleName)
  implicit val mat    = ActorMaterializer()
  implicit val ec     = system.dispatcher

  classOf[Guard].getSimpleName should {

    "let legal request pass through" in {
      val request = HttpRequest(uri = Uri("/legal"))
      runGuard(request) shouldBe Right(request)
    }

    "get principal response" in {
      val Left(f) = runGuard(HttpRequest(uri = Uri("/authorized")))
      Await.result(f, 1.second).status shouldBe OK
    }

    "reject illegal request" in {
      val Left(f) = runGuard(HttpRequest())
      Await.result(f, 1.second).status shouldBe Unauthorized
    }

    "redirect illegal get html request" in {
      val Left(f) = runGuard(HttpRequest(headers = Accept(MediaRanges.`text/*`) :: Nil))
      Await.result(f, 1.second).status shouldBe Found
    }

  }

  private def runGuard(req: HttpRequest) = {
    Await.result(Source.single(req).via(Flow.fromGraph(Guard.flow(Example))).runWith(Sink.head), 1.second)
  }

  override protected def afterAll(): Unit = system.terminate()
}

object Example extends Guard {
  override type Token     = String
  override type Principal = String
  override type Code      = String
  override type State     = String

  override def legal(req: HttpRequest): Boolean = req.uri == Uri("/legal")

  override def authorized(req: HttpRequest): Boolean = req.uri == Uri("/authorized")

  override protected def refresh = FastFuture.successful("token")

  override protected def principal(token: String, request: HttpRequest) = FastFuture.successful(HttpResponse())

  override protected def redirectToAuthorizeWith(uri: Uri) = HttpResponse(StatusCodes.Found)
}
