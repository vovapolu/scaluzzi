lazy val V = _root_.scalafix.sbt.BuildInfo
inThisBuild(
  List(
    scalaVersion := V.scala212,
    addCompilerPlugin(scalafixSemanticdb),
    scalacOptions ++= List(
      "-Yrangepos"
    )
  )
)

lazy val rules = project.settings(
  libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % V.scalafix
)

lazy val input = project

lazy val output = project

lazy val tests = project
  .settings(
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
