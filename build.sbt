import DicomSourceGenerators._
import com.softwaremill.SbtSoftwareMillCommon.commonSmlBuildSettings
import sbt.IO
import sbt.Keys.{ organization, resolvers }

enablePlugins(BuildInfoPlugin)

lazy val rootSettings = Seq(
  libraryDependencies ++= Dependencies.all,
  name := "dicom-streams",
  organization := "com.exini",
  organizationName := "EXINI Diagnostics",
  startYear := Some(2019),
  homepage := Some(url("https://github.com/exini/dicom-streams")),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  developers := List(
    Developer(
      "karl-exini",
      "Karl Sjöstrand",
      "karl.sjostrand@exini.com",
      url("https://exini.com")
    )
  ),
  scalaVersion := "2.13.8",
  scalacOptions ++= Seq("-Vimplicits", "-Vtype-diffs"),
  resolvers ++= Dependencies.resolvers
)

lazy val buildInfoSettings = Seq(
  buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
  buildInfoPackage := "com.exini.dicom"
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

lazy val root = project
  .in(file("."))
  .settings(rootSettings)
  .settings(buildInfoSettings)
  .settings(managedSourcesSettings)
  .settings(coverageSettings)
  .settings(commonSmlBuildSettings)
