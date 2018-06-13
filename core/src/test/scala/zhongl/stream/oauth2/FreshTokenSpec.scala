package zhongl.stream.oauth2

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, Graph}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import zhongl.stream.oauth2.FreshToken.Shape

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NoStackTrace

class FreshTokenSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  implicit val system = ActorSystem(getClass.getSimpleName)
  implicit val mat    = ActorMaterializer()
  implicit val ec     = system.dispatcher

  object InvalidToken extends IllegalArgumentException with NoStackTrace

  "FreshToken" should {
    "refresh token first" in {
      val future = runFreshToken(FreshToken.graph(FastFuture.successful("token"), InvalidToken))
      Await.result(future, 1.second) shouldBe HttpResponse()
    }

    "abnormal when refresh failed" in {
      val cause  = new RuntimeException()
      val future = runFreshToken(FreshToken.graph(FastFuture.failed(cause), InvalidToken))
      intercept[RuntimeException] {
        Await.result(future, 1.second)
      } shouldBe cause
    }

    "refresh token after latest token invalid" in {
      val sf = Source(List(HttpRequest(), HttpRequest(POST)))
        .via(
          Flow
            .fromFunction[(Future[String], HttpRequest), Future[HttpResponse]] {
              case (tf, HttpRequest(GET, _, _, _, _))  => tf.flatMap { case "token" => FastFuture.failed(InvalidToken) }
              case (tf, HttpRequest(POST, _, _, _, _)) => tf.map { case "token"     => HttpResponse() }
            }
            .join(FreshToken.graph(FastFuture.successful("token"), InvalidToken))
        )
        .runWith(Sink.seq)

      val Seq(rf1, rf2) = Await.result(sf, 1.second)

      intercept[IllegalArgumentException] {
        Await.result(rf1, 1.second)
      } shouldBe InvalidToken

      Await.result(rf2, 1.second) shouldBe HttpResponse()
    }
  }

  private def runFreshToken(freshToken: Graph[Shape[String], NotUsed]) = {
    val f = Source
      .single(HttpRequest())
      .via(
        Flow
          .fromFunction[(Future[String], HttpRequest), Future[HttpResponse]] {
            case (tf, _) => tf.map { case "token" => HttpResponse() }
          }
          .join(freshToken)
      )
      .runWith(Sink.head)
    val future = Await.result(f, 1.second)
    future
  }

  override protected def afterAll(): Unit = system.terminate()
}
