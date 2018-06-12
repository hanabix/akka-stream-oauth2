package zhongl.stream.oauth2

import akka.NotUsed
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Partition, Source, Unzip, Zip}
import akka.stream.{FanOutShape2, FlowShape, Graph}

import scala.concurrent.Future
import scala.util.control.NoStackTrace

trait Guard {

  type Token
  type Principal
  type Code
  type State

  final object InvalidToken extends IllegalArgumentException with NoStackTrace

  private final def graph: Graph[FanOutShape2[HttpRequest, HttpRequest, Future[HttpResponse]], NotUsed] =
    GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val partition = b.add(Partition(3, partitioner))
      val merge     = b.add(Merge[Future[HttpResponse]](2))

      // format: OFF
      partition.out(1) ~> authenticate ~> merge
      partition.out(2) ~> reject       ~> merge
      // format: ON

      new FanOutShape2(partition.in, partition.out(0), merge.out)
    }

  private final def authenticate: Graph[FlowShape[HttpRequest, Future[HttpResponse]], NotUsed] =
    GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val zip   = b.add(Zip[Future[Token], HttpRequest])
      val unzip = b.add(Unzip[Future[Token], Future[HttpResponse]])
      val merge = b.add(Merge[Future[Token]](2))
      val init  = b.add(Source.single(Future.failed(InvalidToken)))

      // format: OFF
      zip.out ~> principal ~> unzip.in
      zip.in0 <~ merge     <~ unzip.out0
                 merge     <~ init
      // format: ON

      FlowShape(zip.in1, unzip.out1)
    }

  private final def partitioner: HttpRequest => Int = {
    case r if legal(r)      => 0
    case r if authorized(r) => 1
    case _                  => 2
  }

  protected def legal(req: HttpRequest): Boolean

  protected def authorized(req: HttpRequest): Boolean

  protected def reject: Graph[FlowShape[HttpRequest, Future[HttpResponse]], NotUsed]

  protected def principal: Graph[FlowShape[(Future[Token], HttpRequest), (Future[Token], Future[HttpResponse])], NotUsed]

}

object Guard {
  def shape[T <: Guard](some: Guard): Graph[FanOutShape2[HttpRequest, HttpRequest, Future[HttpResponse]], NotUsed] = some.graph

  def flow[T <: Guard](some: Guard): Graph[FlowShape[HttpRequest, Either[Future[HttpResponse], HttpRequest]], NotUsed] = GraphDSL.create() { implicit b =>
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
