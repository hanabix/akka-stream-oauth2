package zhongl.stream.oauth2

import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}

import scala.concurrent.Future

trait OAuth2[Token] {
  /** @return a new valid access_token for API calling */
  def refresh: Future[Token]

  /**
    * @param token for API calling
    * @param authorized request redirected with code and state
    * @return a response with visitor's authenticated information
    */
  def authenticate(token: Token, authorized: HttpRequest): Future[HttpResponse]

  /**
    * @param state
    * @return URI will be redirected for authorization
    */
  def authorization(state: String): Location

  /** @return URI should be redirect with code and state normally after authorized. */
  def redirect: Uri

}
