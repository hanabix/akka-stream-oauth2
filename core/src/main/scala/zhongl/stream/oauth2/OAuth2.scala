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

package zhongl.stream.oauth2

import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}

import scala.concurrent.Future

trait OAuth2[Token] {

  /** @return a new valid access_token for API calling */
  def refresh: Future[Token]

  /** @param token
    *   for API calling
    * @param authorized
    *   request redirected with code and state
    * @return
    *   a response with visitor's authenticated information
    */
  def authenticate(token: Token, authorized: HttpRequest): Future[HttpResponse]

  /** @param state
    * @return
    *   URI will be redirected for authorization
    */
  def authorization(state: String): Location

  /** @return URI should be redirect with code and state normally after authorized. */
  def redirect: Uri

}
