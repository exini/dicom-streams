import sbt.IO
import DicomSourceGenerators._

name := "dicom-streams"
version := "0.10"
organization := "com.exini"
scalaVersion := "2.12.8"
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
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/")

// deps

libraryDependencies ++= {
  val akkaVersion = "2.5.21"
  Seq(
    "org.scala-lang.modules" %% "scala-xml" % "1.1.1",
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "org.slf4j" % "slf4j-simple" % "1.7.26",
    "org.scalatest" %% "scalatest" % "3.0.7" % "test",
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

pomExtra :=
  <url>https://github.com/slicebox/dicom-streams</url>
    <developers>
      <developer>
        <id>KarlSjostrand</id>
        <name>Karl Sjöstrand</name>
        <url>https://github.com/KarlSjostrand</url>
      </developer>
    </developers>