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

package zhongl.stream
import java.util.Base64

import akka.http.scaladsl.model.{ContentTypes, HttpResponse}
import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model.StatusCodes.OK
import akka.stream.Materializer
import akka.util.ByteString
import spray.json._

import scala.util.Try

package object oauth2 {
  final implicit class TryConvert(val value: ByteString) extends AnyVal {
    def as[T: JsonReader]: Try[T] = Try { value.utf8String.parseJson.convertTo[T] }
  }

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

  @inline
  final def base64Decode(v: String): String = new String(Base64.getUrlDecoder.decode(v))

  @inline
  final def base64Encode(v: String): String = Base64.getUrlEncoder.encodeToString(v.getBytes)

}
