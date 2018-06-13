package zhongl.stream.oauth2

import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.model.headers.Location

import scala.concurrent.Future

trait OAuth2[Token] {
  def refresh: Future[Token]

  def authenticate(token: Token, request: HttpRequest): Future[HttpResponse]

  def authorization(state: String): Location

  def redirect: Uri

  val invalidToken: Throwable
}
