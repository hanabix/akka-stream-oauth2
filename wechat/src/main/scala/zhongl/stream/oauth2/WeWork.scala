package zhongl.stream.oauth2

import akka.http.scaladsl.model.HttpRequest

class WeWork extends Guard {
  import WeWork._

  override type Token     = AccessToken
  override type Principal = UserInfo
  override type Code      = String
  override type State     = String

  override protected def legal(req: HttpRequest) = ???

  override protected def authorized(req: HttpRequest) = ???

  override protected def reject = ???

  override protected def principal = ???
}

object WeWork {
  final case class AccessToken(`access_token`: String)
  final case class UserInfo(`UserId`: String)
}
