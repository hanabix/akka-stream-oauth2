package zhongl.stream.oauth2

import akka.NotUsed
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Source, Zip, ZipWith}
import akka.stream.{BidiShape, Graph}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Failure

object FreshToken {
  type Shape[T] = BidiShape[Future[HttpResponse], Future[HttpResponse], HttpRequest, (Future[T], HttpRequest)]

  def graph[T](fresh: => Future[T], invalidToken: Throwable)(implicit ec: ExecutionContext): Graph[Shape[T], NotUsed] =
    GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val init           = b.add(Source.single(Future.failed(invalidToken)))
      val zip            = b.add(Zip[Future[T], HttpRequest])
      val mergeToken     = b.add(Merge[Future[T]](2))
      val bcastToken     = b.add(Broadcast[Future[T]](2))
      val bcastResponse  = b.add(Broadcast[Future[HttpResponse]](2))
      val transformToken = b.add(transform[T](invalidToken))

      // format: OFF
      mergeToken <~ init
      mergeToken ~> refresh(fresh, invalidToken) ~> bcastToken ~>zip.in0
                                                    bcastToken ~> transformToken.in0
                                          bcastResponse.out(1) ~> transformToken.in1
      mergeToken <~                                               transformToken.out
      // format: ON

      BidiShape(bcastResponse.in, bcastResponse.out(0), zip.in1, zip.out)
    }

  private def refresh[T](fresh: => Future[T], invalidToken: Throwable)(implicit ec: ExecutionContext) =
    Flow.fromFunction[Future[T], Future[T]](f => f.recoverWith { case `invalidToken` => fresh })

  private def transform[T](invalidToken: Throwable)(implicit ec: ExecutionContext) =
    ZipWith[Future[T], Future[HttpResponse], Future[T]]((tf, rf) => {
      val p = Promise[T]
      tf.flatMap { t =>
        rf.onComplete {
          case Failure(`invalidToken`) => p.failure(invalidToken)
          case _                       => p.success(t)
        }
        p.future
      }
    })
}
