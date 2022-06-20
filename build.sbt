import Dependencies._

inThisBuild(
  Seq(
    scalaVersion       := "2.13.8",
    scalafmtOnCompile  := true,
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding",
      "UTF-8"
    ),
    crossScalaVersions := Seq(scalaVersion.value, "2.12.16"),
    organization       := "com.github.zhongl",
    homepage           := Some(url("https://github.com/hanabix/akka-stream-oauth2")),
    licenses           := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers         := List(
      Developer(
        "zhongl",
        "Lunfu Zhong",
        "zhong.lunfu@gmail.com",
        url("https://github.com/zhongl")
      )
    )
  )
)

def commonSettings(module: String) = Seq(
  name := module
)

lazy val root = (project in file("."))
  .settings(
    commonSettings("akka-stream-oauth2"),
    publish / skip := true
  )
  .aggregate(core, dingtalk, wechat)

lazy val core = (project in file("core"))
  .settings(
    commonSettings("akka-stream-oauth2-core"),
    libraryDependencies ++= common ++ akka ++ auth0
  )

lazy val wechat = (project in file("wechat"))
  .settings(
    commonSettings("akka-stream-oauth2-wechat"),
    libraryDependencies ++= common
  )
  .dependsOn(core)

lazy val dingtalk = (project in file("dingtalk"))
  .settings(
    commonSettings("akka-stream-oauth2-dingtalk"),
    libraryDependencies ++= common
  )
  .dependsOn(core)
