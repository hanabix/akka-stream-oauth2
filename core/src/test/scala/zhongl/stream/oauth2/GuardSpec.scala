package zhongl.stream.oauth2

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes.{OK, Unauthorized}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, Graph}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class GuardSpec extends WordSpec with BeforeAndAfterAll with Matchers {
  implicit val system = ActorSystem(getClass.getSimpleName)
  implicit val mat    = ActorMaterializer()


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

  override def reject: Graph[FlowShape[HttpRequest, Future[HttpResponse]], NotUsed] = {
    Flow.fromFunction(_ => Future.successful(HttpResponse(Unauthorized)))
  }

  override def principal: Graph[FlowShape[(Future[Token], HttpRequest), (Future[Token], Future[HttpResponse])], NotUsed] = {
    Flow.fromFunction { case (tf, _) => (tf, Future.successful(HttpResponse(OK))) }
  }
}
