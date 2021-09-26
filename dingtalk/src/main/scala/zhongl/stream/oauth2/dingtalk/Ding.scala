/*
 *  Copyright 2019 Zhong Lunfu
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package zhongl.stream.oauth2.dingtalk
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.util.FastFuture
import akka.stream._
import akka.util.ByteString
import zhongl.stream.oauth2.FreshToken.InvalidToken
import zhongl.stream.oauth2._

import java.net.URLEncoder
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.concurrent._

class Ding(authenticated: (UserInfo, Uri) => HttpResponse)(implicit system: ActorSystem) extends OAuth2[AccessToken] {
  import Ding._

  implicit private val mat = Materializer(system)
  implicit private val ec  = system.dispatcher

  private val http                            = Http()
  private val config                          = system.settings.config.getConfig("dingtalk")
  private val appid                           = config.getString("mobile.appid")
  private val secret                          = config.getString("mobile.secret")
  private val `authorization.uri`             = Uri(config.getString("authorization.uri"))
  private val `api.uri.access-token`          = Uri(config.getString("api.uri.access-token"))
  private val `api.uri.user-get`              = Uri(config.getString("api.uri.user-get"))
  private val `api.uri.user-info-by-code`     = Uri(config.getString("api.uri.user-info-by-code"))
  private val `api.uri.get-userid-by-unionid` = Uri(config.getString("api.uri.get-userid-by-unionid"))
  private val fixedQueryString                = s"redirect_uri=${URLEncoder.encode(redirect.toString(), "utf-8")}&state="

  override def refresh: Future[AccessToken]                                                 = {
    http
      .singleRequest(HttpRequest(uri = `api.uri.access-token`))
      .map(complainIllegalResponse { case Content(AccessTokenE(token)) => token })
  }

  override def authenticate(token: AccessToken, request: HttpRequest): Future[HttpResponse] = {
    val q = request.uri.query()
    (for (code <- q.get("code"); state <- q.get("state")) yield {
      userInfo(code, token.`access_token`).map(authenticated(_, base64Decode(state)))
    }).getOrElse(FastFuture.successful(HttpResponse(StatusCodes.BadRequest, entity = HttpEntity("missing code or state"))))
  }

  override def authorization(state: String): Location                                       = {
    val qs = s"$fixedQueryString${base64Encode(state)}"
    Location(`authorization.uri`.withRawQueryString(`authorization.uri`.rawQueryString.map(_ + s"&$qs").getOrElse(qs)))
  }

  override def redirect: Uri                                                                = Uri(config.getString("authorization.redirect"))

  private def userInfo(code: String, token: String): Future[UserInfo] = {
    http
      .singleRequest(getUserInfoBy(code))
      .map(complainIllegalResponse { case Content(PrincipalE(p)) =>
        p.`user_info`.`unionid`
      })
      .flatMap { uid =>
        http.singleRequest(getUseridBy(uid, token))
      }
      .map(complainIllegalResponse {
        case Content(UserIdE(uid))             => uid.userid
        case Content(ErrE(Err(40014 | 42001))) => throw InvalidToken
      })
      .flatMap { uid =>
        http.singleRequest(getUserBy(uid, token))
      }
      .map(complainIllegalResponse {
        case Content(UserInfoE(info))          => info
        case Content(ErrE(Err(40014 | 42001))) => throw InvalidToken
      })
  }

  private def getUserInfoBy(code: String)                             = {
    val timestamp = System.currentTimeMillis().toString
    val uri       = `api.uri.user-info-by-code`.withQuery(
      Query(
        "timestamp" -> timestamp,
        "accessKey" -> appid,
        "signature" -> sign("HmacSHA256", "UTF-8", timestamp, secret)
      )
    )
    HttpRequest(POST, uri, entity = HttpEntity(ContentTypes.`application/json`, s"""{"tmp_auth_code":"$code"}"""))
  }

  private def getUserBy(uid: String, token: String)                   = {
    HttpRequest(uri = `api.uri.user-get`.withQuery(queryWith(token, "userid" -> uid)))
  }

  private def getUseridBy(unionid: String, token: String)             = {
    HttpRequest(uri = `api.uri.get-userid-by-unionid`.withQuery(queryWith(token, "unionid" -> unionid)))
  }

  @inline
  private def queryWith(token: String, q: (String, String)*) = Query(("access_token" -> token) +: q: _*)
}

object Ding extends JsonSupport {
  object AccessTokenE {
    def unapply(arg: ByteString): Option[AccessToken] = arg.as[AccessToken].toOption
  }

  object PrincipalE {
    def unapply(arg: ByteString): Option[Principal] = arg.as[Principal].toOption
  }

  object UserIdE {
    def unapply(arg: ByteString): Option[UserId] = arg.as[UserId].toOption
  }

  object UserInfoE {
    def unapply(arg: ByteString): Option[UserInfo] = arg.as[UserInfo].toOption
  }

  object ErrE {
    def unapply(arg: ByteString): Option[Err] = arg.as[Err].toOption
  }

  def sign(algorithm: String, charset: String, content: String, secret: String): String = {
    val mac = Mac.getInstance(algorithm)
    mac.init(new SecretKeySpec(secret.getBytes(charset), algorithm))
    new String(Base64.getEncoder.encode(mac.doFinal(content.getBytes(charset))))
  }

  def apply(authenticated: (UserInfo, Uri) => HttpResponse)(implicit system: ActorSystem): Ding = new Ding(authenticated)

}
