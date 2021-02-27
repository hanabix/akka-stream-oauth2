package zhongl.stream.oauth2
import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentTypes.`application/octet-stream`
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ComplainIllegalResponseSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  implicit private val system = ActorSystem(getClass.getSimpleName)
  implicit private val mat    = Materializer(system)

  "Complain illegal response" should {

    " with strict entity" in {
      val f = complainIllegalResponse { case HttpResponse(StatusCodes.OK, _, _, _) =>
        ""
      }

      intercept[IllegalStateException](f(HttpResponse(BadRequest))).getMessage shouldBe "400 Bad Request - "
    }

    "complain illegal response with non strict entity" in {
      val f = complainIllegalResponse { case HttpResponse(StatusCodes.OK, _, _, _) =>
        ""
      }

      val response = HttpResponse(BadRequest, entity = HttpEntity(`application/octet-stream`, Source.repeat(ByteString("1"))))
      intercept[IllegalStateException](f(response)).getMessage shouldBe "400 Bad Request - ignored chunk"
    }

  }

  override protected def afterAll(): Unit = system.terminate()
}
