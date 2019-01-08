package zhongl.stream.oauth2
import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentTypes.`application/octet-stream`
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.Future

class OAuth2Spec extends WordSpec with Matchers with BeforeAndAfterAll {

  final implicit val system = ActorSystem(getClass.getSimpleName)
  final implicit val mat    = ActorMaterializer()

  "OAuth2" should {

    "complain illegal response with strict entity" in {
      val f = Mock.complainIllegalResponse {
        case HttpResponse(StatusCodes.OK, _, _, _) => ""
      }

      intercept[IllegalStateException](f(HttpResponse(BadRequest))).getMessage shouldBe "400 Bad Request - "
    }

    "complain illegal response with non strict entity" in {
      val f = Mock.complainIllegalResponse {
        case HttpResponse(StatusCodes.OK, _, _, _) => ""
      }

      val response = HttpResponse(BadRequest, entity = HttpEntity(`application/octet-stream`, Source.repeat(ByteString("1"))))
      intercept[IllegalStateException](f(response)).getMessage shouldBe "400 Bad Request - ignored chunk"
    }

  }

  final class Token extends FreshToken.Token {
    override def isInvalid: Boolean = false
  }

  final object Mock extends OAuth2[Token] {
    override def refresh: Future[OAuth2Spec.this.Token]                                                 = ???
    override def authenticate(token: OAuth2Spec.this.Token, request: HttpRequest): Future[HttpResponse] = ???
    override def authorization(state: String): Location                                                 = ???
    override def redirect: Uri                                                                          = ???
  }

  override protected def afterAll(): Unit = system.terminate()
}
