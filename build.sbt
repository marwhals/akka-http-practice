ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.12"

lazy val root = (project in file("."))
  .settings(
    name := "akka-http-practice"
  )

val akkaVersion       = "2.6.20"
val akkaHttpVersion   = "10.2.10"
val scalaTestVersion  = "3.2.18"

libraryDependencies ++= Seq(
  // Akka core & streams
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,

  // Akka HTTP
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,

  // JWT
  "com.pauldijou" %% "jwt-spray-json" % "5.0.0",

  // Testing
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "org.scalatest"     %% "scalatest" % scalaTestVersion % Test
)
