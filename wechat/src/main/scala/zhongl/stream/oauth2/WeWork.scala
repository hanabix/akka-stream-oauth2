package zhongl.stream.oauth2

import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._

import scala.concurrent.Future

class WeWork(jt: JwtCookie, authorizedPath: Path) extends OAuth2[String] {
  import WeWork._

  override def refresh: Future[String] = ???

  override def authenticate(token: String, request: HttpRequest): Future[HttpResponse] = ???

  override def authorization(redirect: Uri, state: String): Uri = ???

  override val invalidToken: Throwable = ???
}

object WeWork {
  final case class AccessToken(`access_token`: String)
  final case class UserInfo(`UserId`: String)

}
