import Dependencies._

def commonSettings(module: String) = Seq(
  name := s"akka-stream-oauth2-$module",
  organization := "com.github.zhongl",
  scalaVersion := "2.12.6",
  publishArtifact := false,
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8"
  )
)

lazy val core = (project in file("core"))
  .settings(
    commonSettings("core"),
    libraryDependencies ++= common ++ akka ++ auth0
  )

lazy val wechat = (project in file("wechat"))
  .settings(
    commonSettings("wechat"),
    libraryDependencies ++= common
  )
  .dependsOn(core)
