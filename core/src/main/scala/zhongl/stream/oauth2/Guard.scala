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
import akka.http.scaladsl.model.HttpEntity.Empty
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model.StatusCodes.{Found, Unauthorized}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
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

    val AuthorizedRedirect = Authorized(redirect)

    {
      case Ignore()             => 0
      case AuthorizedRedirect() => 1
      case _                    => 2
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

    @inline
    def state(uri: Uri, headers: immutable.Seq[HttpHeader]): String = {
      headers
        .foldLeft(uri) {
          case (u, `X-Forwarded-Host`(host))      => u.copy(authority = u.authority.copy(host = host))
          case (u, `X-Forwarded-Proto`(protocol)) => u.copy(scheme = protocol)
          case (u, _)                             => u
        }
        .toString()
    }

    Flow
      .fromFunction[HttpRequest, HttpResponse] {
        case HttpRequest(GET, uri, headers @ AcceptHtml(), Empty, _) => HttpResponse(Found, List(location(state(uri, headers))))
        case _                                                       => HttpResponse(Unauthorized)
      }
      .map(FastFuture.successful)
  }

  private def authenticate[T <: Token](oauth: OAuth2[T])(implicit ec: ExecutionContext) =
    Flow
      .fromFunction[(Future[T], HttpRequest), Future[HttpResponse]] { case (tf, req) =>
        tf.flatMap(oauth.authenticate(_, req))
      }
      .join(FreshToken.graph(oauth.refresh))

}
