package zhongl.stream.oauth2

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
import akka.stream.ActorMaterializer
import akka.util.ByteString
import spray.json._

import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NoStackTrace

class WeWork()(implicit system: ActorSystem) extends OAuth2[String] {
  import WeWork._

  implicit val mat = ActorMaterializer()

  private val http                   = Http()
  private val config                 = system.settings.config.getConfig("wechat.work")
  private val `authorization.uri`    = Uri(config.getString("authorization.uri"))
  private val `api.uri.access-token` = Uri(config.getString("api.uri.access-token"))
  private val `api.uri.user-info`    = Uri(config.getString("api.uri.user-info"))
  private val `api.uri.user-get`     = Uri(config.getString("api.uri.user-get"))

  override val redirect     = Uri(config.getString("authorization.redirect"))
  override val invalidToken = new IllegalArgumentException with NoStackTrace

  private val fixedQueryString = s"redirect_uri=${URLEncoder.encode(redirect.toString(), "utf-8")}&state="

  override def refresh: Future[String] = {
    http
      .singleRequest(HttpRequest(uri = `api.uri.access-token`))
      .map(complainIllegalResponse {
        case Content(AccessToken(token)) => token
      })
  }

  override def authenticate(token: String, request: HttpRequest): Future[HttpResponse] = {
    val q = request.uri.query()
    val result = for (code <- q.get("code"); state <- q.get("state")) yield {
      val r = HttpRequest(uri = `api.uri.user-info`.withQuery(Query("code" -> code, "access_token" -> token)))
      http
        .singleRequest(r)
        .map(complainIllegalResponse {
          case Content(Principal(uid))     => uid
          case Content(Err(40014 | 42001)) => throw invalidToken
        })
        .flatMap { uid =>
          http.singleRequest(HttpRequest(uri = `api.uri.user-get`.withQuery(Query("userid" -> uid, "access_token" -> token))))
        }
        .map(complainIllegalResponse {
          case Content(UserInfo(uid, name, dept, email, stat)) => setCookie(UserInfo(uid, name, dept, email, stat), state)
          case Content(Err(40014 | 42001))                      => throw invalidToken
        })
    }

    result.getOrElse(FastFuture.successful(HttpResponse(StatusCodes.BadRequest, entity = HttpEntity("missing code or state"))))
  }

  override def authorization(state: String): Location = {
    val qs = s"$fixedQueryString${Base64.getUrlEncoder.encode(state.getBytes)}"
    Location(`authorization.uri`.withRawQueryString(`authorization.uri`.rawQueryString.map(_ + s"&$qs").getOrElse(qs)))
  }

  private def setCookie(info: UserInfo, state: String): HttpResponse = ???

}

object WeWork extends DefaultJsonProtocol {
  final case class Principal(UserId: String)
  implicit val principalF = jsonFormat1(Principal.apply)
  object Principal {
    def apply(UserId: String): Principal = new Principal(UserId)

    def unapply(arg: Principal): Option[String] = Some(arg.UserId)

    def unapply(arg: ByteString): Option[String] = arg.as[Principal].toOption.flatMap(unapply)
  }

  final case class AccessToken(`access_token`: String)
  implicit val accessTokenF = jsonFormat1(AccessToken.apply)
  object AccessToken {
    def apply(`access_token`: String): AccessToken = new AccessToken(`access_token`)

    def unapply(arg: AccessToken): Option[String] = Some(arg.`access_token`)

    def unapply(arg: ByteString): Option[String] = arg.as[AccessToken].toOption.flatMap(unapply)
  }

  final case class Err(errcode: Int)
  implicit val errF = jsonFormat1(Err.apply)
  object Err {
    def apply(errcode: Int): Err = new Err(errcode)

    def unapply(arg: Err): Option[Int] = Some(arg.errcode)

    def unapply(arg: ByteString): Option[Int] = arg.as[Err].toOption.flatMap(unapply)

  }

  final case class UserInfo(userid: String, name: String, department: Seq[Int], email: String, status: Int)
  implicit val userInfoF = jsonFormat5(UserInfo.apply)
  object UserInfo {
    def apply(userid: String, name: String, department: Seq[Int], email: String, status: Int): UserInfo =
      new UserInfo(userid, name, department, email, status)

    def unapply(arg: UserInfo): Option[(String, String, Seq[Int], String, Int)] =
      Some((arg.userid, arg.name, arg.department, arg.email, arg.status))

    def unapply(arg: ByteString): Option[(String, String, Seq[Int], String, Int)] =
      arg.as[UserInfo].toOption.flatMap(unapply)
  }

  final object Content {
    def unapply(result: HttpResponse): Option[ByteString] = result match {
      case HttpResponse(OK, _, Strict(ContentTypes.`application/json`, content), _) => Some(content)
      case _                                                                        => None
    }
  }

  implicit class TryConvert(val value: ByteString) extends AnyVal {
    def as[T: JsonReader]: Try[T] = Try { value.utf8String.parseJson.convertTo[T] }
  }

  final def complainIllegalResponse[T](pf: PartialFunction[HttpResponse, T]): HttpResponse => T = {
    case r if pf.isDefinedAt(r)                      => pf(r)
    case HttpResponse(status, _, Strict(_, data), _) => throw new IllegalStateException(s"$status - $data")
    case HttpResponse(status, _, entity, _)          => entity.discardBytes(); throw new IllegalStateException(s"$status - ignored chunk")
  }
}
