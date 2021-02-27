/*
 *  Copyright 2019 Zhong Lunfu
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package zhongl.stream.oauth2

import akka.NotUsed
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Source, Zip, ZipWith}
import akka.stream.{BidiShape, Graph}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Failure
import scala.util.control.NoStackTrace

/** {{{
  *
  * +------------------------------------------------+
  * |                                                |
  * | +------+                             +------+  |
  * | |      | ~  Future[HttpResponse]  ~> |      | ~ Future[HttpResponse] ~>
  * | | flow |                             | bidi |  |
  * | |      | <~(Future[T], HttpRequest)~ |      | <~      HttpRequest     ~
  * | +------+                             +------+  |
  * |                                                |
  * +------------------------------------------------+
  * }}}
  * @see akka.stream.scaladsl.Flow#join(akka.stream.Graph)
  */
object FreshToken {
  type Shape[T <: Token] = BidiShape[Future[HttpResponse], Future[HttpResponse], HttpRequest, (Future[T], HttpRequest)]

  def graph[T <: Token](fresh: => Future[T])(implicit ec: ExecutionContext): Graph[Shape[T], NotUsed] =
    GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val init           = b.add(Source.single(Future.failed(InvalidToken)))
      val zip            = b.add(Zip[Future[T], HttpRequest]())
      val mergeToken     = b.add(Merge[Future[T]](2))
      val bcastToken     = b.add(Broadcast[Future[T]](2))
      val bcastResponse  = b.add(Broadcast[Future[HttpResponse]](2))
      val transformToken = b.add(transform[T])

      // format: OFF
      mergeToken <~ init
      mergeToken ~> refresh(fresh) ~> bcastToken           ~> zip.in0
                                      bcastToken           ~> transformToken.in0
                                      bcastResponse.out(1) ~> transformToken.in1
      mergeToken <~                                           transformToken.out
      // format: ON

      BidiShape(bcastResponse.in, bcastResponse.out(0), zip.in1, zip.out)
    }

  private def refresh[T <: Token](fresh: => Future[T])(implicit ec: ExecutionContext) =
    Flow.fromFunction[Future[T], Future[T]] { f =>
      f.map(t => if (t.isInvalid) throw InvalidToken else t).recoverWith { case InvalidToken => fresh }
    }

  private def transform[T <: Token](implicit ec: ExecutionContext) =
    ZipWith[Future[T], Future[HttpResponse], Future[T]]((tf, rf) => {
      val p = Promise[T]()
      tf.flatMap { t =>
        rf.onComplete {
          case Failure(InvalidToken) => p.failure(InvalidToken)
          case _                     => p.success(t)
        }
        p.future
      }
    })

  trait Token {
    def isInvalid: Boolean
  }

  object InvalidToken extends IllegalArgumentException with NoStackTrace
}
