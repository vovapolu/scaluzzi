lazy val V = _root_.scalafix.sbt.BuildInfo

inThisBuild(
  List(
    organization := "com.github.vovapolu",
    homepage := Some(url("https://github.com/vovapolu/scaluzzi")),
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "vovapolu",
        "Vladimir Polushin",
        "vovapolu@gmail.com",
        url("https://vovapolu.github.io")
      )
    ),
    scalaVersion := V.scala212,
    crossScalaVersions := List(V.scala212, V.scala213),
    addCompilerPlugin(scalafixSemanticdb("4.4.20")),
    scalacOptions ++= List(
      "-Yrangepos",
      "-P:semanticdb:synthetics:on"
    )
  )
)

publish / skip := true

lazy val rules = project.settings(
  libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % V.scalafixVersion,
  moduleName := "scaluzzi"
)

lazy val input = project
  .settings(
    publish / skip := true
  )

lazy val output = project
  .settings(
    publish / skip := true
  )

lazy val tests = project
  .settings(
    publish / skip := true,
    libraryDependencies += "ch.epfl.scala" % "scalafix-testkit" % V.scalafixVersion % Test cross CrossVersion.full,
    scalafixTestkitOutputSourceDirectories :=
      (output / Compile / sourceDirectories).value,
    scalafixTestkitInputSourceDirectories :=
      (input / Compile / sourceDirectories).value,
    scalafixTestkitInputClasspath :=
      (input / Compile / fullClasspath).value
  )
  .dependsOn(input, rules)
  .enablePlugins(ScalafixTestkitPlugin)
