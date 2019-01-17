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
import akka.http.scaladsl.model.Uri.Authority
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.model.{HttpRequest, Uri}

class Authorized(redirect: Uri) {

  def unapply(request: HttpRequest): Boolean = {
    @inline
    def authority: Authority =
      request.headers
        .find(_.isInstanceOf[Host])
        .map(_.asInstanceOf[Host])
        .map(h => Authority(h.host, h.port))
        .getOrElse(request.uri.authority)

    request.uri.path == redirect.path && (redirect.authority.isEmpty || authority == redirect.authority)
  }
}
object Authorized {
  def apply(redirect: Uri): Authorized = new Authorized(redirect)
}
