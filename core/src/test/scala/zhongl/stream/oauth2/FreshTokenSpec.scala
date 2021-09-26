package zhongl.stream.oauth2

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{Graph, Materializer}
import org.scalatest.BeforeAndAfterAll
import zhongl.stream.oauth2.FreshToken.{InvalidToken, Shape, Token}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class FreshTokenSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  implicit private val system = ActorSystem(getClass.getSimpleName)
  implicit private val mat    = Materializer(system)
  implicit private val ec     = system.dispatcher

  "FreshToken" should {
    "refresh token first" in {
      val future = runFreshToken(FreshToken.graph(FastFuture.successful(Example)))
      Await.result(future, 1.second) shouldBe HttpResponse()
    }

    "abnormal when refresh failed" in {
      val cause  = new RuntimeException()
      val future = runFreshToken(FreshToken.graph(FastFuture.failed(cause)))
      intercept[RuntimeException] {
        Await.result(future, 1.second)
      } shouldBe cause
    }

    "refresh token after latest token invalid" in {
      val sf = Source(List(HttpRequest(), HttpRequest(POST)))
        .via(
          Flow
            .fromFunction[(Future[Example.type], HttpRequest), Future[HttpResponse]] {
              case (tf, HttpRequest(GET, _, _, _, _))  => tf.flatMap { case Example => FastFuture.failed(InvalidToken) }
              case (tf, HttpRequest(POST, _, _, _, _)) => tf.map { case Example => HttpResponse() }
            }
            .join(FreshToken.graph(FastFuture.successful(Example)))
        )
        .runWith(Sink.seq)

      val Seq(rf1, rf2) = Await.result(sf, 1.second)

      intercept[IllegalArgumentException] {
        Await.result(rf1, 1.second)
      } shouldBe InvalidToken

      Await.result(rf2, 1.second) shouldBe HttpResponse()
    }

    "refresh token after latest token expired" in {
      val sf = Source(List(HttpRequest(), HttpRequest()))
        .via(
          Flow
            .fromFunction[(Future[Expiration], HttpRequest), Future[HttpResponse]] { case (tf, _) =>
              tf.flatMap { case _ => Thread.sleep(10); FastFuture.successful(HttpResponse()) }
            }
            .join(FreshToken.graph(FastFuture.successful(Expiration(System.currentTimeMillis() + 1))))
        )
        .runWith(Sink.seq)

      val Seq(rf1, rf2) = Await.result(sf, 1.second)
      Await.result(rf1, 1.second) shouldBe HttpResponse()
      Await.result(rf2, 1.second) shouldBe HttpResponse()
    }
  }

  private def runFreshToken[T <: Token](freshToken: Graph[Shape[T], NotUsed]) = {
    val f      = Source
      .single(HttpRequest())
      .via(
        Flow
          .fromFunction[(Future[T], HttpRequest), Future[HttpResponse]] { case (tf, _) =>
            tf.map { case Example => HttpResponse() }
          }
          .join(freshToken)
      )
      .runWith(Sink.head)
    val future = Await.result(f, 1.second)
    future
  }

  override protected def afterAll(): Unit                                     = system.terminate()

  object Example                         extends Token {
    override def isInvalid: Boolean = false
  }

  case class Expiration(timestamp: Long) extends Token {
    override def isInvalid: Boolean = timestamp < System.currentTimeMillis()
  }
}
