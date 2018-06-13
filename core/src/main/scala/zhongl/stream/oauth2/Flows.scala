package zhongl.stream.oauth2

import akka.NotUsed
import akka.stream.scaladsl.{Flow, GraphDSL, Merge}
import akka.stream.{FanOutShape2, FlowShape, Graph}

object Flows {
  def either[A, B, C](graph: Graph[FanOutShape2[A, B, C], NotUsed]): Graph[FlowShape[A, Either[B, C]], NotUsed] =
    GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val fos2  = b.add(graph)
      val left  = b.add(Flow.fromFunction(Left(_: B)))
      val right = b.add(Flow.fromFunction(Right(_: C)))
      val merge = b.add(Merge[Either[B, C]](2))

      // format: OFF
      fos2.out0 ~> left  ~> merge
      fos2.out1 ~> right ~> merge
      // format: ON

      FlowShape(fos2.in, merge.out)

    }
}
