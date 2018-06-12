package zhongl.stream.oauth2

import akka.NotUsed
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model._
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Partition, Source, Unzip, Zip}
import akka.stream.{FanOutShape2, FlowShape, Graph}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Failure
import scala.util.control.NoStackTrace

trait Guard {

  type Token
  type Principal
  type Code
  type State

  final object InvalidToken extends IllegalArgumentException with NoStackTrace

  protected def authenticate(implicit ec: ExecutionContext) =
    Flow.fromFunction[(Future[Token], HttpRequest), (Future[Token], Future[HttpResponse])] {
      case (tokenF, request) =>
        val p = Promise[Token]
        val responseF = tokenF.recoverWith { case InvalidToken => refresh }.flatMap { t =>
          principal(t, request).transform {
            case r @ Failure(InvalidToken) => p.failure(InvalidToken); r
            case r                         => p.success(t); r
          }
        }
        (p.future, responseF)
    }

  protected def challenge: Graph[FlowShape[HttpRequest, Future[HttpResponse]], NotUsed] =
    Flow
      .fromFunction[HttpRequest, HttpResponse] {
        case HttpRequest(GET, uri, AcceptHtml(), HttpEntity.Empty, _) => redirectToAuthorizeWith(uri)
        case _                                                        => HttpResponse(StatusCodes.Unauthorized)
      }
      .map(FastFuture.successful)

  protected def legal(req: HttpRequest): Boolean

  protected def authorized(req: HttpRequest): Boolean

  protected def redirectToAuthorizeWith(uri: Uri): HttpResponse

  protected def refresh: Future[Token]

  protected def principal(token: Token, request: HttpRequest): Future[HttpResponse]

  private final def graph(implicit ec: ExecutionContext) =
    GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val partitionRequest = b.add(Partition(3, partitioner))
      val mergeResponse    = b.add(Merge[Future[HttpResponse]](2))
      val zip              = b.add(Zip[Future[Token], HttpRequest])
      val unzip            = b.add(Unzip[Future[Token], Future[HttpResponse]])
      val mergeToken       = b.add(Merge[Future[Token]](2))
      val initToken        = b.add(Source.single(Future.failed(InvalidToken)))

      // format: OFF
                                            mergeToken   <~ initToken
                                 zip.in0 <~ mergeToken   <~ unzip.out0
      partitionRequest.out(1) ~> zip.in1
                                 zip.out ~> authenticate ~> unzip.in
                                                            unzip.out1 ~> mergeResponse
      partitionRequest.out(2) ~>            challenge                  ~> mergeResponse
      // format: ON

      new FanOutShape2(partitionRequest.in, partitionRequest.out(0), mergeResponse.out)
    }

  private final def partitioner: HttpRequest => Int = {
    case r if legal(r)      => 0
    case r if authorized(r) => 1
    case _                  => 2
  }

  private object AcceptHtml {
    def unapply(headers: immutable.Seq[HttpHeader]): Boolean = {
      headers.exists {
        case a: Accept => a.mediaRanges.exists(_.matches(MediaTypes.`text/html`))
        case _         => false
      }
    }
  }

}

object Guard {
  def shape[T <: Guard](some: Guard)(
      implicit ec: ExecutionContext
  ): Graph[FanOutShape2[HttpRequest, HttpRequest, Future[HttpResponse]], NotUsed] = some.graph

  def flow[T <: Guard](some: Guard)(
      implicit ec: ExecutionContext
  ): Graph[FlowShape[HttpRequest, Either[Future[HttpResponse], HttpRequest]], NotUsed] = GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._

    val guard = b.add(some.graph)
    val left  = b.add(Flow.fromFunction(Left(_: Future[HttpResponse])))
    val right = b.add(Flow.fromFunction(Right(_: HttpRequest)))
    val merge = b.add(Merge[Either[Future[HttpResponse], HttpRequest]](2))

    // format: OFF
    guard.out0 ~> right ~> merge
    guard.out1 ~> left  ~> merge
    // format: ON

    FlowShape(guard.in, merge.out)
  }

}
