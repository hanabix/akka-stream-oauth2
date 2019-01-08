package zhongl.stream.oauth2

package object wechat {
  final case class UserInfo(userid: String, name: String, department: Seq[Int], email: String, status: Int, isleader: Int, enable: Int, alias: String)
  final case class Principal(UserId: String)
  final case class Err(errcode: Int)
  final case class AccessToken(`access_token`: String, `expires_in`: Int) extends FreshToken.Token {
    @transient private val created = System.currentTimeMillis()
    override def isInvalid: Boolean = `expires_in` * 1000 + created < System.currentTimeMillis()
  }

}
