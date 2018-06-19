package zhongl.stream.oauth2.wechat

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

trait JsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val principalF   = jsonFormat1(Principal)
  implicit val accessTokenF = jsonFormat2(AccessToken)
  implicit val userInfoF    = jsonFormat5(UserInfo)
  implicit val errF         = jsonFormat1(Err)
}
