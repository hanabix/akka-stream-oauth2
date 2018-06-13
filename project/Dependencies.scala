import sbt._

object Dependencies {

  object Version {
    val akka = "2.5.12"
    val http = "10.1.2"
  }

  val common = Seq(
    "org.scalatest" %% "scalatest" % "3.0.4" % Test,
    "org.scalamock" %% "scalamock" % "4.1.0" % Test
  )

  val akka = Seq(
    "com.typesafe.akka" %% "akka-stream"          % Version.akka,
    "com.typesafe.akka" %% "akka-http"            % Version.http,
    "com.typesafe.akka" %% "akka-http-spray-json" % Version.http,
    "com.typesafe.akka" %% "akka-http-testkit"    % Version.http % Test
  )

  val auth0 = Seq(
    "com.auth0" % "java-jwt" % "3.3.0"
  )
}
