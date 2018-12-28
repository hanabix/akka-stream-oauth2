import Dependencies._

def commonSettings(module: String) = Seq(
  name := module,
  organization := "com.github.zhongl.akka-stream-oauth2",
  scalaVersion := "2.12.8",
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
