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
