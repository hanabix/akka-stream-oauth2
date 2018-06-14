package zhongl.stream.oauth2.wechat

import java.net.URLEncoder
import java.util.Base64

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.util.FastFuture
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import zhongl.stream.oauth2.FreshToken.InvalidToken
import zhongl.stream.oauth2._

import scala.concurrent.{ExecutionContext, Future}

class WeWork(authenticated: (UserInfo, Uri) => HttpResponse)(implicit system: ActorSystem) extends OAuth2[AccessToken] {
  import WeWork._

  implicit val mat: Materializer    = ActorMaterializer()
  implicit val ec: ExecutionContext = system.dispatcher

  private val http                   = Http()
  private val config                 = system.settings.config.getConfig("wechat.work")
  private val `authorization.uri`    = Uri(config.getString("authorization.uri"))
  private val `api.uri.access-token` = Uri(config.getString("api.uri.access-token"))
  private val `api.uri.user-info`    = Uri(config.getString("api.uri.user-info"))
  private val `api.uri.user-get`     = Uri(config.getString("api.uri.user-get"))

  override val redirect = Uri(config.getString("authorization.redirect"))

  private val fixedQueryString = s"redirect_uri=${URLEncoder.encode(redirect.toString(), "utf-8")}&state="

  override def refresh: Future[AccessToken] = {
    http
      .singleRequest(HttpRequest(uri = `api.uri.access-token`))
      .map(complainIllegalResponse { case Content(AccessTokenE(token)) => token })
  }

  override def authenticate(token: AccessToken, request: HttpRequest): Future[HttpResponse] = {
    def queryWithToken(q: (String, String)*) = Query(("access_token" -> token.`access_token`) +: q: _*)

    val q = request.uri.query()
    val result = for (code <- q.get("code"); state <- q.get("state")) yield {
      val r = HttpRequest(uri = `api.uri.user-info`.withQuery(queryWithToken("code" -> code)))
      http
        .singleRequest(r)
        .map(complainIllegalResponse {
          case Content(PrincipalE(p))            => p.UserId
          case Content(ErrE(Err(40014 | 42001))) => throw InvalidToken
        })
        .flatMap { uid =>
          http.singleRequest(HttpRequest(uri = `api.uri.user-get`.withQuery(queryWithToken("userid" -> uid))))
        }
        .map(complainIllegalResponse {
          case Content(UserInfoE(info))          => authenticated(info, base64Decode(state))
          case Content(ErrE(Err(40014 | 42001))) => throw InvalidToken
        })
    }

    result.getOrElse(FastFuture.successful(HttpResponse(StatusCodes.BadRequest, entity = HttpEntity("missing code or state"))))
  }

  override def authorization(state: String): Location = {
    val qs = s"$fixedQueryString${base64Encode(state)}"
    Location(`authorization.uri`.withRawQueryString(`authorization.uri`.rawQueryString.map(_ + s"&$qs").getOrElse(qs)))
  }

}

object WeWork extends JsonSupport {

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

  object AccessTokenE {
    def unapply(arg: ByteString): Option[AccessToken] = arg.as[AccessToken].toOption
  }

  object PrincipalE {
    def unapply(arg: ByteString): Option[Principal] = arg.as[Principal].toOption
  }

  object UserInfoE {
    def unapply(arg: ByteString): Option[UserInfo] = arg.as[UserInfo].toOption
  }

  object ErrE {
    def unapply(arg: ByteString): Option[Err] = arg.as[Err].toOption
  }

  @inline
  final def base64Decode(v: String): String = new String(Base64.getUrlDecoder.decode(v))

  @inline
  final def base64Encode(v: String): String = Base64.getUrlEncoder.encodeToString(v.getBytes)

  def apply(authenticated: (UserInfo, Uri) => HttpResponse)(implicit system: ActorSystem): WeWork = new WeWork(authenticated)
}
