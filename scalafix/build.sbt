
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
    addCompilerPlugin(scalafixSemanticdb("4.3.10")),
    scalacOptions ++= List(
      "-Yrangepos",
      "-P:semanticdb:synthetics:on"
    )
  )
)

skip in publish := true

lazy val rules = project.settings(
  libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % V.scalafix,
  moduleName := "scaluzzi"
)

lazy val input = project
  .settings(
    skip in publish := true
  )

lazy val output = project
  .settings(
    skip in publish := true
  )

lazy val tests = project
  .settings(
    skip in publish := true,
    libraryDependencies += "ch.epfl.scala" % "scalafix-testkit" % V.scalafix % Test cross CrossVersion.full,
    scalafixTestkitOutputSourceDirectories :=
      sourceDirectories.in(output, Compile).value,
    scalafixTestkitInputSourceDirectories :=
      sourceDirectories.in(input, Compile).value,
    scalafixTestkitInputClasspath :=
      fullClasspath.in(input, Compile).value
  )
  .dependsOn(input, rules)
  .enablePlugins(ScalafixTestkitPlugin)
