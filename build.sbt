ThisBuild / version          := "0.1.0"
ThisBuild / organization     := "com.github.terdong"
ThisBuild / scalaVersion     := "3.8.4"

lazy val root = project.in(file("."))
  .aggregate(tokyo.jvm, tokyo.js)
  .settings(
    name := "tokyo-root",
    publish / skip := true
  )

lazy val tokyo = crossProject(JSPlatform, JVMPlatform)
  .in(file("."))
  .settings(
    name := "tokyo",
    libraryDependencies ++= Seq(
      "io.getkyo"          %%% "kyo-core" % "1.0.0-RC2",
      "org.scalameta"      %%% "munit"    % "1.3.2" % Test
    )
  )
