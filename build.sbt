import sbt.IO
import DicomSourceGenerators._

lazy val scala212 = "2.12.10"
lazy val scala213 = "2.13.0"

name := "dicom-streams"
version := "0.12-SNAPSHOT"
organization := "com.exini"
scalaVersion := scala212
crossScalaVersions := Seq(scala212, scala213)
scalacOptions := Seq("-encoding", "UTF-8", "-Xlint", "-deprecation", "-unchecked", "-feature", "-target:jvm-1.8")
scalacOptions in(Compile, doc) ++= Seq(
  "-no-link-warnings" // Suppresses problems with Scaladoc @throws links
)

// build info settings

enablePlugins(BuildInfoPlugin)
buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)
buildInfoPackage := "com.exini.dicom"

// repos

resolvers ++= Seq(
  "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/")

// deps

libraryDependencies ++= {
  val akkaVersion = "2.5.25"
  Seq(
    "org.scala-lang.modules" %% "scala-xml" % "1.2.0",
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "org.slf4j" % "slf4j-simple" % "1.7.28",
    "org.scalatest" %% "scalatest" % "3.0.8" % "test",
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % "test"
  )
}

updateOptions := updateOptions.value.withCachedResolution(true)

// specify that there are managed sources and their destinations

sourceGenerators in Compile += Def.task {
  val tagFile = (sourceManaged in Compile).value / "sbt-dicomdata" / "Tag.scala"
  val uidFile = (sourceManaged in Compile).value / "sbt-dicomdata" / "UID.scala"
  val tagToVRFile = (sourceManaged in Compile).value / "sbt-dicomdata" / "TagToVR.scala"
  val tagToVMFile = (sourceManaged in Compile).value / "sbt-dicomdata" / "TagToVM.scala"
  val tagToKeywordFile = (sourceManaged in Compile).value / "sbt-dicomdata" / "TagToKeyword.scala"
  IO.write(tagFile, generateTag())
  IO.write(uidFile, generateUID())
  IO.write(tagToKeywordFile, generateTagToKeyword())
  IO.write(tagToVRFile, generateTagToVR())
  IO.write(tagToVMFile, generateTagToVM())
  Seq(tagFile, uidFile, tagToKeywordFile, tagToVRFile, tagToVMFile)
}.taskValue

// include managed sources among other sources when publishing

mappings in (Compile, packageSrc) ++= {
  val base  = (sourceManaged  in Compile).value
  val files = (managedSources in Compile).value
  files.map { f => (f, f.relativeTo(base).get.getPath) }
}

// for automatic license stub generation

organizationName := "EXINI Diagnostics"
startYear := Some(2019)
licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))

// coverage

coverageExcludedPackages := ".*\\.BuildInfo.*;.*\\.Tag.*;.*\\.UID.*;.*\\.TagToKeyword.*;.*\\.TagToVR.*;.*\\.TagToVM.*"

// publish
publishMavenStyle := true

publishArtifact in Test := false

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

pomIncludeRepository := { _ => false }

scmInfo := Some(
  ScmInfo(
    url("https://github.com/exini/dicom-streams"),
    "scm:git@github.com:exini/dicom-streams.git"
  )
)

homepage := Some(url("https://github.com/exini/dicom-streams"))


developers := List(
  Developer(
    id    = "karl-exini",
    name  = "Karl Sjöstrand",
    email = "karl.sjostrand@exini.com",
    url   = url("https://github.com/karl-exini")
  )
)
