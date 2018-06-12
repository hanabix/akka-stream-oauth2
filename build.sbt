import Dependencies._

def commonSettings(module: String) = Seq(
  name := s"akka-stream-oauth2-$module",
  organization := "zhongl",
  scalaVersion := "2.12.6",
  scalacOptions += "-deprecation",
  version := "0.0.1"
)

lazy val core = (project in file("core"))
  .settings(
    commonSettings("core"),
    libraryDependencies ++= common ++ akka ++ auth0
  )

lazy val wechat = (project in file("wechat"))
  .settings(
    commonSettings("wechat")
  )
  .dependsOn(core)
