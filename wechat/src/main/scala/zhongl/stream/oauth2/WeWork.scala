package zhongl.stream.oauth2

import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._

class WeWork(jt: JwtCookie, authorizedPath: Path) extends Guard {
  import WeWork._

  override type Token     = AccessToken
  override type Principal = UserInfo
  override type Code      = String
  override type State     = String

  override protected def legal(req: HttpRequest) = jt.unapply(req).isDefined

  override protected def authorized(req: HttpRequest) = {
    req.uri.toRelative.path == authorizedPath
  }

  override protected def refresh = ???

  override protected def principal(token: AccessToken, request: HttpRequest) = ???

  override protected def redirectToAuthorizeWith(uri: Uri) = ???
}

object WeWork {
  final case class AccessToken(`access_token`: String)
  final case class UserInfo(`UserId`: String)

}
