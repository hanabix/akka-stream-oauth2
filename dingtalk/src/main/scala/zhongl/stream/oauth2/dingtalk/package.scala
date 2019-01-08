package zhongl.stream.oauth2

package object dingtalk {

  final case class Role(id: String, name: String, groupName: String)
  final case class UserInfo(userid: String, name: String, department: Seq[Int], avatar: String, active: Boolean, roles: Seq[Role])
  final case class UserId(userid: String)
  final case class IdInfo(`unionid`: String)
  final case class Principal(`user_info`: IdInfo)
  final case class Err(errcode: Int)

  final case class AccessToken(`access_token`: String, `expires_in`: Int) extends FreshToken.Token {
    @transient private val created  = System.currentTimeMillis()
    override def isInvalid: Boolean = `expires_in` * 1000 + created < System.currentTimeMillis()
  }
}
