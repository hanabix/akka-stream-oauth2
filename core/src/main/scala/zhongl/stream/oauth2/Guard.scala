package zhongl.stream.oauth2

import akka.NotUsed
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model.StatusCodes.{Found, Unauthorized}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Accept, Host, Location}
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Partition, Source, Unzip, Zip}
import akka.stream.{FanOutShape2, FlowShape, Graph}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Failure

object Guard {

  def graph[T](oauth: OAuth2[T], redirect: Uri, ignore: HttpRequest => Boolean)(
      implicit ec: ExecutionContext
  ): Graph[FanOutShape2[HttpRequest, HttpRequest, Future[HttpResponse]], NotUsed] = {

    @inline
    def location(state: String) = Location(oauth.authorization(redirect, state))

    GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val partitionRequest = b.add(Partition(3, partitioner(redirect, ignore)))
      val mergeResponse    = b.add(Merge[Future[HttpResponse]](2))
      val zip              = b.add(Zip[Future[T], HttpRequest])
      val unzip            = b.add(Unzip[Future[T], Future[HttpResponse]])
      val mergeToken       = b.add(Merge[Future[T]](2))
      val initToken        = b.add(Source.single(Future.failed(oauth.invalidToken)))

      // format: OFF
                                            mergeToken          <~ initToken
                                 zip.in0 <~ mergeToken          <~ unzip.out0
      partitionRequest.out(1) ~> zip.in1
                                 zip.out ~> authenticate(oauth) ~> unzip.in
                                                                   unzip.out1 ~> mergeResponse
      partitionRequest.out(2) ~>            challenge(location)               ~> mergeResponse
      // format: ON

      new FanOutShape2(partitionRequest.in, partitionRequest.out(0), mergeResponse.out)
    }

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

  private def challenge(location: String => Location): Graph[FlowShape[HttpRequest, Future[HttpResponse]], NotUsed] = {
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

  private def authenticate[T](oauth: OAuth2[T])(
      implicit ec: ExecutionContext
  ): Graph[FlowShape[(Future[T], HttpRequest), (Future[T], Future[HttpResponse])], NotUsed] = {

    object InvalidToken {
      def unapply(t: Throwable): Boolean = t == oauth.invalidToken
    }

    Flow.fromFunction[(Future[T], HttpRequest), (Future[T], Future[HttpResponse])] {
      case (tokenF, request) =>
        val p = Promise[T]
        val responseF = tokenF.recoverWith { case InvalidToken() => oauth.refresh }.flatMap { t =>
          oauth.authenticate(t, request).transform {
            case r @ Failure(InvalidToken()) => p.failure(oauth.invalidToken); r
            case r                           => p.success(t); r
          }
        }
        (p.future, responseF)
    }
  }

}

trait OAuth2[Token] {
  def refresh: Future[Token]

  def authenticate(token: Token, request: HttpRequest): Future[HttpResponse]

  def authorization(redirect: Uri, state: String): Uri

  val invalidToken: Throwable
}
