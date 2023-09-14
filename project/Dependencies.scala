import sbt._

object Dependencies {

  private lazy val typesafeReleases = "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

  private lazy val akkaVersion = "2.8.0"

  private lazy val lang: Seq[ModuleID] =
    Seq(
      "org.scala-lang.modules" %% "scala-xml"  % "2.1.0"
    )

  private lazy val logging: Seq[ModuleID] =
    Seq(
      "org.slf4j" % "slf4j-simple" % "2.0.7"
    )

  private lazy val akka: Seq[ModuleID] =
    Seq(
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j"  % akkaVersion
    )

  private lazy val test: Seq[ModuleID] =
    Seq(
      "org.scalatest"     %% "scalatest"           % "3.2.15"    % "test",
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % "test"
    )

  lazy val data: Seq[ModuleID]    = lang ++ logging ++ test
  lazy val streams: Seq[ModuleID] = data ++ akka

  lazy val resolvers: Seq[MavenRepository] = Seq(typesafeReleases)
}
