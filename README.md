[![CI](https://github.com/hanabix/akka-stream-oauth2/actions/workflows/ci.yml/badge.svg)](https://github.com/hanabix/akka-stream-oauth2/actions/workflows/ci.yml) [![Publish](https://github.com/hanabix/akka-stream-oauth2/actions/workflows/sbt-release.yml/badge.svg)](https://github.com/hanabix/akka-stream-oauth2/actions/workflows/sbt-release.yml) [![Codacy Badge](https://app.codacy.com/project/badge/Grade/b08b7b211d174c1c9c9c5923e3f1ed7d)](https://www.codacy.com/gh/hanabix/akka-stream-oauth2/dashboard?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=hanabix/akka-stream-oauth2&amp;utm_campaign=Badge_Grade) [![Coverage Status](https://coveralls.io/repos/github/hanabix/akka-stream-oauth2/badge.svg)](https://coveralls.io/github/hanabix/akka-stream-oauth2) [![Maven Central](https://img.shields.io/maven-central/v/com.github.zhongl/akka-stream-oauth2-core_2.13)](https://search.maven.org/artifact/com.github.zhongl/akka-stream-oauth2-core_2.13)


**akka-stream-oauth2** provides some useful graph shapes of [akka-stream](https://doc.akka.io/docs/akka/current/stream/index.html) for OAuth2.


## Dependencies

```scala
libraryDependencies += "com.github.zhongl" %% "akka-stream-oauth2-<core or wechat or dingtalk>" % <latest tag>
```

## Usage

A simple web application it's authencation based on `Wechat Work`.

```scala
val ignore: HttpRequest => Boolean = ???
val oauth2: OAuth2[AccessToken] = WeWork { ??? }
val routes: Route = { ??? }
val graph = GraphDSL.create() { implicit b =>
  import GraphDSL.Implicits._

  val guard = b.add(Guard.graph(oauth2, ignore))
  val merge = b.add(Merge[Future[HttpResponse]](2))
  val serve = b.add(Flow.fromFunction(Route.asyncHandler(routes)))

  // format: OFF
  guard.out0 ~> serve ~> merge
  guard.out1          ~> merge
  // format: ON

  FlowShape(guard.in, merge.out)
}
    
val parallelism: Int = ???
val flow = Flow[HttpRequest].via(graph).mapAsync(parallelism)(identity)
val f = http.bindAndHandle(flow, "0.0.0.0", 8080)
gracefulShutdown(f)
```

