package zhongl.stream.oauth2

import java.util.Base64

import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.model.{ContentTypes, HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.model.headers.Location
import akka.stream.Materializer
import akka.util.ByteString

import scala.concurrent.Future

trait OAuth2[Token] {
  def refresh: Future[Token]

  def authenticate(token: Token, request: HttpRequest): Future[HttpResponse]

  def authorization(state: String): Location

  def redirect: Uri

  final object Content {
    def unapply(result: HttpResponse): Option[ByteString] = result match {
      case HttpResponse(OK, _, Strict(ContentTypes.`application/json`, content), _) => Some(content)
      case _                                                                        => None
    }
  }

  final def complainIllegalResponse[T](pf: PartialFunction[HttpResponse, T])(implicit mat: Materializer): HttpResponse => T = {
    case r if pf.isDefinedAt(r)                      => pf(r)
    case HttpResponse(status, _, Strict(_, data), _) => throw new IllegalStateException(s"$status - ${data.utf8String}")
    case HttpResponse(status, _, entity, _)          => entity.discardBytes(); throw new IllegalStateException(s"$status - ignored chunk")
  }

  @inline
  final def base64Decode(v: String): String = new String(Base64.getUrlDecoder.decode(v))

  @inline
  final def base64Encode(v: String): String = Base64.getUrlEncoder.encodeToString(v.getBytes)

}
