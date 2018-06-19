package zhongl.stream.oauth2

import akka.util.ByteString
import spray.json._

import scala.util.Try

package object wechat {
  implicit class TryConvert(val value: ByteString) extends AnyVal {
    def as[T: JsonReader]: Try[T] = Try { value.utf8String.parseJson.convertTo[T] }
  }

  final case class UserInfo(userid: String, name: String, department: Seq[Int], email: String, status: Int)
  final case class Principal(UserId: String)
  final case class Err(errcode: Int)
  final case class AccessToken(`access_token`: String, `expires_in`: Int) extends FreshToken.Token {
    @transient private val created = System.currentTimeMillis()
    override def isInvalid: Boolean = `expires_in` * 1000 + created < System.currentTimeMillis()
  }

}
