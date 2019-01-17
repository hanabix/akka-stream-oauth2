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
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

trait JsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val idInfoF      = jsonFormat1(IdInfo)
  implicit val principalF   = jsonFormat1(Principal)
  implicit val roleF        = jsonFormat3(Role)
  implicit val userInfoF    = jsonFormat7(UserInfo)
  implicit val accessTokenF = jsonFormat2(AccessToken)
  implicit val errF         = jsonFormat1(Err)
  implicit val userIdF      = jsonFormat1(UserId)
}
