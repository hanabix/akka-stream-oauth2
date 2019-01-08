package zhongl.stream
import akka.util.ByteString
import spray.json._

import scala.util.Try

package object oauth2 {
  final implicit class TryConvert(val value: ByteString) extends AnyVal {
    def as[T: JsonReader]: Try[T] = Try { value.utf8String.parseJson.convertTo[T] }
  }
}
