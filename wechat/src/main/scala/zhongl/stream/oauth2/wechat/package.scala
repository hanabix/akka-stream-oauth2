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

package object wechat {
  final case class UserInfo(
      userid: String,
      name: String,
      department: Seq[Int],
      email: String,
      avatar: String,
      status: Int,
      isleader: Int,
      enable: Int,
      alias: String
  )
  final case class Principal(UserId: String)
  final case class Err(errcode: Int)
  final case class AccessToken(`access_token`: String, `expires_in`: Int) extends FreshToken.Token {
    @transient private val created  = System.currentTimeMillis()
    override def isInvalid: Boolean = `expires_in` * 1000 + created < System.currentTimeMillis()
  }

}
