import DicomSourceGenerators._
import com.softwaremill.SbtSoftwareMillCommon.commonSmlBuildSettings
import sbt.IO
import sbt.Keys.{ organization, resolvers }

lazy val rootSettings = Seq(
  name := "dicom-streams",
  ThisBuild / organization := "com.exini",
  ThisBuild / organizationName := "EXINI Diagnostics",
  ThisBuild / startYear := Some(2019),
  ThisBuild / homepage := Some(url("https://github.com/exini/dicom-streams")),
  ThisBuild / licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  ThisBuild / developers := List(
    Developer(
      "karl-exini",
      "Karl Sjöstrand",
      "karl.sjostrand@exini.com",
      url("https://exini.com")
    )
  ),
  ThisBuild / scalaVersion := "2.13.10",
  scalacOptions ++= Seq("-Vimplicits", "-Vtype-diffs"),
  publish / skip := true,
  resolvers ++= Dependencies.resolvers
)

lazy val buildInfoSettings = Seq(
  buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
  buildInfoPackage := "com.exini.dicom.data"
)

lazy val managedSourcesSettings = Seq(
  Compile / sourceGenerators += Def.task {
    val tagFile          = (Compile / sourceManaged).value / "sbt-dicomdata" / "Tag.scala"
    val uidFile          = (Compile / sourceManaged).value / "sbt-dicomdata" / "UID.scala"
    val tagToVRFile      = (Compile / sourceManaged).value / "sbt-dicomdata" / "TagToVR.scala"
    val tagToVMFile      = (Compile / sourceManaged).value / "sbt-dicomdata" / "TagToVM.scala"
    val tagToKeywordFile = (Compile / sourceManaged).value / "sbt-dicomdata" / "TagToKeyword.scala"
    val uidToNameFile    = (Compile / sourceManaged).value / "sbt-dicomdata" / "UIDToName.scala"
    IO.write(tagFile, generateTag())
    IO.write(uidFile, generateUID())
    IO.write(tagToKeywordFile, generateTagToKeyword())
    IO.write(tagToVRFile, generateTagToVR())
    IO.write(tagToVMFile, generateTagToVM())
    IO.write(uidToNameFile, generateUIDToName())
    Seq(tagFile, uidFile, tagToKeywordFile, tagToVRFile, tagToVMFile, uidToNameFile)
  }.taskValue,
  Compile / packageSrc / mappings ++= { // include managed sources among other sources when publishing
    val base  = (Compile / sourceManaged).value
    val files = (Compile / managedSources).value
    files.map(f => (f, f.relativeTo(base).get.getPath))
  }
)

lazy val coverageSettings = Seq(
  coverageExcludedPackages := ".*\\.BuildInfo.*;.*\\.Tag.*;.*\\.UID.*;.*\\.TagToKeyword.*;.*\\.TagToVR.*;.*\\.TagToVM.*\\.UIDToName.*"
)

lazy val dataLib = project
  .in(file("data"))
  .enablePlugins(BuildInfoPlugin)
  .settings(name := "dicom-data")
  .settings(buildInfoSettings)
  .settings(managedSourcesSettings)
  .settings(libraryDependencies ++= Dependencies.data)

lazy val streamsLib = project
  .in(file("streams"))
  .settings(name := "dicom-streams")
  .settings(libraryDependencies ++= Dependencies.streams)
  .dependsOn(dataLib % "test->test;compile->compile")

lazy val root = project
  .in(file("."))
  .aggregate(dataLib, streamsLib)
  .settings(rootSettings)
  .settings(coverageSettings)
  .settings(commonSmlBuildSettings)
