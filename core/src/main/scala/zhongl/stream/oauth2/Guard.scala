package zhongl.stream.oauth2

import akka.NotUsed
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model.StatusCodes.{Found, Unauthorized}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Accept, Host, Location}
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Partition}
import akka.stream.{FanOutShape2, FlowShape, Graph}
import zhongl.stream.oauth2.FreshToken.Token

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

object Guard {

  type Shape = FanOutShape2[HttpRequest, HttpRequest, Future[HttpResponse]]

  def graph[T <: Token](oauth: OAuth2[T], ignore: HttpRequest => Boolean)(implicit ec: ExecutionContext): Graph[Shape, NotUsed] =
    GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val partition = b.add(Partition(3, partitioner(oauth.redirect, ignore)))
      val merge     = b.add(Merge[Future[HttpResponse]](2))

      // format: OFF
      partition.out(1) ~> authenticate(oauth)            ~> merge
      partition.out(2) ~> challenge(oauth.authorization) ~> merge
      // format: ON

      new FanOutShape2(partition.in, partition.out(0), merge.out)
    }

  def asFlow(graph: Graph[Shape, NotUsed]) = GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._

    val fos2  = b.add(graph)
    val left  = b.add(Flow.fromFunction(Left(_: HttpRequest)))
    val right = b.add(Flow.fromFunction(Right(_: Future[HttpResponse])))
    val merge = b.add(Merge[Either[HttpRequest, Future[HttpResponse]]](2))

    // format: OFF
    fos2.out0 ~> left  ~> merge
    fos2.out1 ~> right ~> merge
    // format: ON

    FlowShape(fos2.in, merge.out)

  }

  private def partitioner(redirect: Uri, ignore: HttpRequest => Boolean): HttpRequest => Int = {
    object Ignore {
      def unapply(request: HttpRequest): Boolean = ignore(request)
    }

    object Authorized {
      def unapply(request: HttpRequest): Boolean = {
        request.headers.find(_.isInstanceOf[Host]).orElse(Some(Host(request.uri.authority.host))).exists {
          case Host(host, _) => host == redirect.authority.host
        }
      }
    }

    {
      case Ignore()     => 0
      case Authorized() => 1
      case _            => 2
    }
  }

  private def challenge(location: String => Location) = {
    object AcceptHtml {
      def unapply(headers: immutable.Seq[HttpHeader]): Boolean = {
        headers.exists {
          case a: Accept => a.mediaRanges.exists(_.matches(MediaTypes.`text/html`))
          case _         => false
        }
      }
    }

    Flow
      .fromFunction[HttpRequest, HttpResponse] {
        case HttpRequest(GET, uri, AcceptHtml(), HttpEntity.Empty, _) => HttpResponse(Found, List(location(uri.toString())))
        case _                                                        => HttpResponse(Unauthorized)
      }
      .map(FastFuture.successful)
  }

  private def authenticate[T <: Token](oauth: OAuth2[T])(implicit ec: ExecutionContext) =
    Flow
      .fromFunction[(Future[T], HttpRequest), Future[HttpResponse]] {
        case (tf, req) => tf.flatMap(oauth.authenticate(_, req))
      }
      .join(FreshToken.graph(oauth.refresh))

}
