import sbt.Keys.resolvers
import sbt._

object Dependencies {

  private lazy val typesafeReleases = "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

  private lazy val akkaVersion = "2.6.18"

  private lazy val lang: Seq[ModuleID] =
    Seq(
      "org.scala-lang.modules" %% "scala-xml"           % "2.0.1",
      "com.beachape"           %% "enumeratum"          % "1.7.0",
    )

  private lazy val akka: Seq[ModuleID] =
    Seq(
    "com.typesafe.akka"      %% "akka-stream"         % akkaVersion,
    "com.typesafe.akka"      %% "akka-slf4j"          % akkaVersion,
  )

  private lazy val logging: Seq[ModuleID] =
    Seq(
      "org.slf4j"               % "slf4j-simple"        % "1.7.35",
    )

  private lazy val test: Seq[ModuleID] =
    Seq(
      "org.scalatest"          %% "scalatest"           % "3.2.2"     % "test",
      "com.typesafe.akka"      %% "akka-stream-testkit" % akkaVersion % "test",
    )

  lazy val all: Seq[ModuleID] = lang ++ akka ++ logging ++ test

  lazy val resolvers: Seq[MavenRepository] = Seq(typesafeReleases)
}
