package zhongl.stream.oauth2.dingtalk
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

trait JsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val idInfoF      = jsonFormat1(IdInfo)
  implicit val principalF   = jsonFormat1(Principal)
  implicit val roleF        = jsonFormat3(Role)
  implicit val userInfoF    = jsonFormat6(UserInfo)
  implicit val accessTokenF = jsonFormat2(AccessToken)
  implicit val errF         = jsonFormat1(Err)
  implicit val userIdF      = jsonFormat1(UserId)
}
