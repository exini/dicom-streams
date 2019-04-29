/*
 * Copyright 2019 EXINI Diagnostics
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import scala.xml.{Elem, NodeSeq, XML}

object DicomSourceGenerators {

  val part06: Elem = XML.loadFile("project/part06.xml")
  val part07: Elem = XML.loadFile("project/part07.xml")

  val chapters: NodeSeq = part06 \ "chapter"

  case class DocElement(tagString: String, name: String, keyword: String, vr: String, vm: String, retired: Boolean)

  case class UID(uidValue: String, uidName: String, uidType: String, retired: Boolean)

  val nonAlphaNumeric = "[^a-zA-Z0-9_]"
  val nonHex = "[^a-fA-F0-9x]"
  val nonUID = "[^0-9.]"

  val (commandElements, metaElements, directoryElements, dataElements): (Seq[DocElement], Seq[DocElement], Seq[DocElement], Seq[DocElement]) = {
    val meta = chapters
      .find(_ \@ "label" == "7")
      .map(_ \\ "tbody" \ "tr")
    val directory = chapters
      .find(_ \@ "label" == "8")
      .map(_ \\ "tbody" \ "tr")
    val data = chapters
      .find(_ \@ "label" == "6")
      .map(_ \\ "tbody" \ "tr")
    val commands = (part07 \ "chapter" \ "section" \ "table")
      .find(_ \@ "label" == "E.1-1")
      .map(_ \ "tbody" \ "tr")

    def toElements(nodes: NodeSeq): Seq[DocElement] =
      nodes.map { node =>
        val cells = node \ "td"
        DocElement(
          cells.head.text.trim,
          cells(1).text.trim,
          cells(2).text.trim.replaceAll(nonAlphaNumeric, ""),
          cells(3).text.trim,
          cells(4).text.trim,
          cells(5).text.trim.toLowerCase.startsWith("ret"))
      }

    (
      toElements(commands.getOrElse(Seq.empty)),
      toElements(meta.getOrElse(Seq.empty)),
      toElements(directory.getOrElse(Seq.empty)),
      toElements(data.getOrElse(Seq.empty))
    )
  }

  val uids: Seq[UID] = {
    chapters
      .find(_ \@ "label" == "A")
      .map(_ \\ "tbody" \ "tr")
      .map(rows => rows.map { row =>
        val cells = row \ "td"
        val uid = cells.head.text.trim.replaceAll(nonUID, "")
        val name = cells(1).text.trim
        UID(
          uid,
          name,
          cells(2).text.trim,
          name.endsWith("(Retired)"))
      })
      .getOrElse(Seq.empty)
  }

  def generateTag(): String =
    s"""package com.exini.dicom.data
       |
       |object Tag {
       |
       |  // command elements
       |${commandElements.filter(_.keyword.nonEmpty).map(a => s"""  final val ${a.keyword} = 0x${a.tagString.replaceAll("x", "0").replaceAll(nonHex, "")}${if (a.retired) " // retired" else ""}""").mkString("\r\n")}
       |
       |  // file meta elements
       |${metaElements.filter(_.keyword.nonEmpty).map(a => s"""  final val ${a.keyword} = 0x${a.tagString.replaceAll("x", "0").replaceAll(nonHex, "")}${if (a.retired) " // retired" else ""}""").mkString("\r\n")}
       |
       |  // directory elements
       |${directoryElements.filter(_.keyword.nonEmpty).map(a => s"""  final val ${a.keyword} = 0x${a.tagString.replaceAll("x", "0").replaceAll(nonHex, "")}${if (a.retired) " // retired" else ""}""").mkString("\r\n")}
       |
       |  // data elements
       |${dataElements.filter(_.keyword.nonEmpty).map(a => s"""  final val ${a.keyword} = 0x${a.tagString.replaceAll("x", "0").replaceAll(nonHex, "")}${if (a.retired) " // retired" else ""}""").mkString("\r\n")}
       |
       |}""".stripMargin

  def generateUID(): String = {
    val pairs = uids
      .map { uid =>
        val name = uid.uidName
        val hasDescription = name.indexOf(':') > 0
        val truncName = if (hasDescription) name.substring(0, name.indexOf(':')) else name
        val cleanedName = truncName
          .replaceAll(nonAlphaNumeric, "")
          .replaceAll("^12", "Twelve")
        (cleanedName, uid.uidValue, uid.retired)
      }
      .filter(_._1.nonEmpty)
      .filter(_._1 != "Retired")
      .distinct

    s"""package com.exini.dicom.data
       |
       |object UID {
       |
       |${pairs.map(p => s"""  final val ${p._1} = "${p._2}"${if (p._3) " // retired" else ""}""").mkString("\r\n")}
       |
       |}""".stripMargin
  }

  case class TagMapping(tag: String, keyword: String, vr: String, vm: String)
  
  private def generateTagMappings(): (Int, String, Seq[TagMapping], Seq[TagMapping]) = {
    val split = 2153

    def toMultiplicity(vr: String, vm: String): String = {
      val vm1Vrs = List("SQ", "OF", "OD", "OW", "OB", "OL", "UR", "UN", "LT", "ST", "UT")

      if (vm1Vrs contains vr)
        "Multiplicity.single"
      else {
        val pattern = "([0-9]+)-?([0-9]+n|[0-9]+|n)?".r
        val pattern(min, max) = vm
        if (max == null)
          if (min == "1") "Multiplicity.single" else s"Multiplicity.fixed($min)"
        else if (max endsWith "n")
          if (min == "1") "Multiplicity.oneToMany" else s"Multiplicity.unbounded($min)"
        else
          s"Multiplicity.bounded($min, $max)"
      }
    }
      
    val tagMappings = (commandElements ++ metaElements ++ directoryElements ++ dataElements)
      .filter(_.keyword.nonEmpty)
      .filterNot(_.tagString.startsWith("(0028,04x"))
      .map { a =>
        val tag = s"0x${a.tagString.replaceAll("x", "0").replaceAll(nonHex, "")}"
        val vr = a.vr match {
          case s if s.contains("OW") => "OW"
          case s if s.contains("SS") => "SS"
          case s => s
        }
        val vm = toMultiplicity(vr, a.vm)
        val keyword = a.keyword
        TagMapping(tag, keyword, vr, vm)
      }
      .filter(_.vr.length == 2)
      .sortBy(_.tag)
    
    val splitValue = tagMappings(split + 1).tag
    val (tagMappingsLow, tagMappingsHigh) = tagMappings.splitAt(split)

    (split, splitValue, tagMappingsLow, tagMappingsHigh)
  }

  def generateTagToKeyword(): String = {
    val (_, splitValue, tagMappingsLow, tagMappingsHigh) = generateTagMappings()
    
    s"""package com.exini.dicom.data
       |
       |import scala.annotation.switch
       |
       |private[data] object TagToKeyword {
       |
       |  def keywordOf(tag: Int): String = tag match {
       |    case t if (t & 0x0000FFFF) == 0 && (t & 0xFFFD0000) != 0 => "GroupLength"
       |    case t if (tag & 0x00010000) != 0 =>
       |      if ((tag & 0x0000FF00) == 0 && (tag & 0x000000F0) != 0) "PrivateCreatorID" else ""
       |    case t if (tag & 0xFFFFFF00) == Tag.SourceImageIDs => "SourceImageIDs"
       |    case t =>
       |      val t2: Int =
       |        if ((tag & 0xFFE00000) == 0x50000000 || (tag & 0xFFE00000) == 0x60000000)
       |          tag & 0xFFE0FFFF
       |        else if ((tag & 0xFF000000) == 0x7F000000 && (tag & 0xFFFF0000) != 0x7FE00000)
       |          tag & 0xFF00FFFF
       |        else
       |          tag
       |      if (t2 < $splitValue) keywordOfLow(t2) else keywordOfHigh(t2)
       |  }
       |
       |  private def keywordOfLow(tag: Int) = (tag: @switch) match {
       |${tagMappingsLow.map(p => s"""    case ${p.tag} => "${p.keyword}"""").mkString("\r\n")}
       |    case _ => ""
       |  }
       |
       |  private def keywordOfHigh(tag: Int) = (tag: @switch) match {
       |${tagMappingsHigh.map(p => s"""    case ${p.tag} => "${p.keyword}"""").mkString("\r\n")}
       |    case _ => ""
       |  }
       |
       |}""".stripMargin
  }

  def generateTagToVR(): String = {
    val (_, splitValue, tagMappingsLow, tagMappingsHigh) = generateTagMappings()
    
    s"""package com.exini.dicom.data
       |
       |import VR._
       |import scala.annotation.switch
       |
       |private[data] object TagToVR {
       |
       |  def vrOf(tag: Int): VR = tag match {
       |    case t if (t & 0x0000FFFF) == 0 => VR.UL // group length
       |    case t if (t & 0x00010000) != 0 => // private creator ID
       |      if ((tag & 0x0000FF00) == 0 && (tag & 0x000000F0) != 0)
       |        VR.LO // private creator data element
       |      else
       |        VR.UN // private tag
       |    case t if (t & 0xFFFFFF00) == Tag.SourceImageIDs => VR.CS
       |    case t =>
       |      val t2 = adjustTag(t)
       |      if (t2 < $splitValue) vrOfLow(t2) else vrOfHigh(t2)
       |  }
       |
       |  private def vrOfLow(tag: Int) = (tag: @switch) match {
       |    ${tagMappingsLow.map(p => s"""case ${p.tag} => ${p.vr}""").mkString("\r\n    ")}
       |    case _ => UN
       |  }
       |
       |  private def vrOfHigh(tag: Int) = (tag: @switch) match {
       |    ${tagMappingsHigh.map(p => s"""case ${p.tag} => ${p.vr}""").mkString("\r\n    ")}
       |    case _ => UN
       |  }
       |
       |  private def adjustTag(tag: Int): Int = {
       |    if ((tag & 0xFFE00000) == 0x50000000 || (tag & 0xFFE00000) == 0x60000000)
       |      tag & 0xFFE0FFFF
       |    else if ((tag & 0xFF000000) == 0x7F000000 && (tag & 0xFFFF0000) != 0x7FE00000)
       |      tag & 0xFF00FFFF
       |    else
       |      tag
       |  }
       |
       |}""".stripMargin
  }
  
  def generateTagToVM(): String = {
    val (_, splitValue, tagMappingsLow, tagMappingsHigh) = generateTagMappings()
    
    s"""package com.exini.dicom.data
       |
       |import scala.annotation.switch
       |
       |private[data] object TagToVM {
       |
       |  def vmOf(tag: Int): Multiplicity = tag match {
       |    case t if (t & 0x0000FFFF) == 0 => Multiplicity.single // group length
       |    case t if (t & 0x00010000) != 0 => // private creator ID
       |      if ((tag & 0x0000FF00) == 0 && (tag & 0x000000F0) != 0)
       |        Multiplicity.single // private creator data element
       |      else
       |        Multiplicity.oneToMany // private tag
       |    case t if (t & 0xFFFFFF00) == Tag.SourceImageIDs => Multiplicity.oneToMany
       |    case t =>
       |      val t2 = adjustTag(t)
       |      Lookup.vrOf(t2) match {
       |        case VR.SQ | VR.OF | VR.OD | VR.OW | VR.OB | VR.OL | VR.UR | VR.UN | VR.LT | VR.ST | VR.UT => Multiplicity.single
       |        case _ => if (t2 < $splitValue) vmOfLow(t2) else vmOfHigh(t2)
       |      }
       |  }
       |
       |  private def vmOfLow(tag: Int) = (tag: @switch) match {
       |    ${tagMappingsLow.map(p => s"""case ${p.tag} => ${p.vm}""").mkString("\r\n    ")}
       |    case _ => Multiplicity.oneToMany
       |  }
       |
       |  private def vmOfHigh(tag: Int) = (tag: @switch) match {
       |    ${tagMappingsHigh.map(p => s"""case ${p.tag} => ${p.vm}""").mkString("\r\n    ")}
       |    case _ => Multiplicity.oneToMany
       |  }
       |
       |  private def adjustTag(tag: Int): Int = {
       |    if ((tag & 0xFFE00000) == 0x50000000 || (tag & 0xFFE00000) == 0x60000000)
       |      tag & 0xFFE0FFFF
       |    else if ((tag & 0xFF000000) == 0x7F000000 && (tag & 0xFFFF0000) != 0x7FE00000)
       |      tag & 0xFF00FFFF
       |    else
       |      tag
       |  }
       |
       |}""".stripMargin
  }
}
