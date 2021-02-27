# akka-stream-oauth2 [![CI](https://github.com/zhongl/akka-stream-oauth2/actions/workflows/ci.yml/badge.svg)](https://github.com/zhongl/akka-stream-oauth2/actions/workflows/ci.yml) [![Release](https://github.com/zhongl/akka-stream-oauth2/actions/workflows/release.yml/badge.svg)](https://github.com/zhongl/akka-stream-oauth2/actions/workflows/release.yml) [![Codacy Badge](https://api.codacy.com/project/badge/Grade/98652a7b28ff46d7b7a9cc73b36b362a)](https://app.codacy.com/app/zhonglunfu/akka-stream-oauth2?utm_source=github.com&utm_medium=referral&utm_content=zhongl/akka-stream-oauth2&utm_campaign=badger) [![Coverage Status](https://coveralls.io/repos/github/zhongl/akka-stream-oauth2/badge.svg?branch=master)](https://coveralls.io/github/zhongl/akka-stream-oauth2?branch=master)  [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.zhongl/akka-stream-oauth2-all_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.zhongl/akka-stream-oauth2-all_2.13)


This provides some useful graph shapes of [akka-stream](https://doc.akka.io/docs/akka/current/stream/index.html) for OAuth2.


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

