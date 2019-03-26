/*
 * Copyright 2018 Lars Edenbrandt
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

package com.exini.dicom.data

import java.nio.charset.{Charset, StandardCharsets}

import akka.util.ByteString
import com.exini.dicom.data.Elements.ValueElement
import com.exini.dicom.data.VR.VR

/**
  * Class for decoding DICOM character data according to one or more character sets as specified by the
  * SpecificCharacterSet element.
  *
  * @param charsetNames character set names as specified in the DICOM standard
  */
class CharacterSets(val charsetNames: Seq[String]) {

  import CharacterSets._

  private val charsetExtensionsEnabled = charsetNames.length > 1

  private val charsetObjs =
    (if (charsetNames.nonEmpty && charsetNames.head.isEmpty) // first item may be empty -> default charset
      Seq(defaultCharsetObj)
    else
      Seq.empty) ++ charsetNames.flatMap(s => charsetObjsMap.get(s))

  private val initialCharsetObj =
    if (charsetObjs.nonEmpty)
      charsetObjs.head
    else
      defaultCharsetObj

  def decode(vr: VR, b: ByteString): String =
    if (!isVrAffectedBySpecificCharacterSet(vr))
      defaultOnly.decode(b)
    else
      decode(b)

  private def decode(b: ByteString): String =
    if (charsetExtensionsEnabled)
      decodeWithExtensions(b)
    else
      new String(b.toArray, initialCharsetObj.charset)

  private def decodeWithExtensions(b: ByteString) = {
    var charsetObj = initialCharsetObj
    var off = 0
    var cur = 0
    val sb = new StringBuilder(b.length)

    while (cur < b.length)
      if (b(cur) == 0x1b) {
        // ESC
        if (off < cur) sb.append(new String(b.toArray, off, cur - off, charsetObj.charset))
        cur += 3
        var key = ((b(cur - 2) & 0xff) << 8) + (b(cur - 1) & 0xff)
        if (key == 0x2428 || key == 0x2429) {
          key = (key << 8) + (b(cur) & 0xff)
          cur += 1
        }
        charsetObj = Option(escToCharset(key)).getOrElse {
          // decode invalid ESC sequence as chars
          val byteCount = if ((key & 0xff0000) != 0) 4 else 3 // if second msb of key is set then 4 otherwise 3
          sb.append(new String(b.toArray, cur - byteCount, byteCount, charsetObj.charset))
          charsetObj
        }
        off = cur
      } else // Step -1 -> chars in G0 one byte, chars in G1 two bytes.
        cur += (if (charsetObj.charlength > 0) charsetObj.charlength else if (b(cur) < 0) 2 else 1)
    if (off < cur)
      sb.append(new String(b.toArray, off, cur - off, charsetObj.charset))
    sb.toString
  }

  override def toString: String = s"${getClass.getSimpleName} [${charsetObjs.map(_.charset.toString).mkString(",")}]"

  override def equals(that: Any): Boolean = that match {
    case thatCharSets: CharacterSets => charsetNames == thatCharSets.charsetNames
    case _ => this == that
  }

  override def hashCode(): Int = charsetNames.hashCode
}

object CharacterSets {

  private val charsetObjsMap = Map(
    // Single-Byte Character Sets Without Code Extensions
    "ISO_IR 100" -> CharsetObj("ISO-8859-1"),
    "ISO_IR 101" -> CharsetObj("ISO-8859-2"),
    "ISO_IR 109" -> CharsetObj("ISO-8859-3"),
    "ISO_IR 110" -> CharsetObj("ISO-8859-4"),
    "ISO_IR 144" -> CharsetObj("ISO-8859-5"),
    "ISO_IR 127" -> CharsetObj("ISO-8859-6"),
    "ISO_IR 126" -> CharsetObj("ISO-8859-7"),
    "ISO_IR 138" -> CharsetObj("ISO-8859-8"),
    "ISO_IR 148" -> CharsetObj("ISO-8859-9"),
    "ISO_IR 13" -> CharsetObj("JIS_X0201"),
    "ISO_IR 166" -> CharsetObj("TIS-620"),
    // Single-Byte Character Sets with Code Extensions
    "ISO 2022 IR 6" -> CharsetObj("ISO-8859-1", 1, Some(ByteString(0x28, 0x42))),
    "ISO 2022 IR 100" -> CharsetObj("ISO-8859-1", 1, Some(ByteString(0x2d, 0x41))),
    "ISO 2022 IR 101" -> CharsetObj("ISO-8859-2", 1, Some(ByteString(0x2d, 0x42))),
    "ISO 2022 IR 109" -> CharsetObj("ISO-8859-3", 1, Some(ByteString(0x2d, 0x43))),
    "ISO 2022 IR 110" -> CharsetObj("ISO-8859-4", 1, Some(ByteString(0x2d, 0x44))),
    "ISO 2022 IR 144" -> CharsetObj("ISO-8859-5", 1, Some(ByteString(0x2d, 0x4c))),
    "ISO 2022 IR 127" -> CharsetObj("ISO-8859-6", 1, Some(ByteString(0x2d, 0x47))),
    "ISO 2022 IR 126" -> CharsetObj("ISO-8859-7", 1, Some(ByteString(0x2d, 0x46))),
    "ISO 2022 IR 138" -> CharsetObj("ISO-8859-8", 1, Some(ByteString(0x28, 0x48))),
    "ISO 2022 IR 148" -> CharsetObj("ISO-8859-9", 1, Some(ByteString(0x28, 0x4d))),
    "ISO 2022 IR 13" -> CharsetObj("JIS_X0201", 1, Some(ByteString(0x29, 0x49))),
    "ISO 2022 IR 166" -> CharsetObj("TIS-620", 1, Some(ByteString(0x2d, 0x54))),
    // Multi-Byte Character Sets with Code Extensions
    "ISO 2022 IR 87" -> CharsetObj("X-JIS0208", 2, Some(ByteString(0x24, 0x42))),
    "ISO 2022 IR 159" -> CharsetObj("JIS_X0212-1990", 2, Some(ByteString(0x24, 0x28, 0x44))),
    "ISO 2022 IR 149" -> CharsetObj("EUC-KR", -1, Some(ByteString(0x24, 0x29, 0x43))),
    "ISO 2022 IR 58" -> CharsetObj("GB2312", -1, Some(ByteString(0x24, 0x29, 0x41))),
    // Multi-Byte Character Sets Without Code Extensions
    "ISO_IR 192" -> CharsetObj("UTF-8", -1, None),
    "GB18030" -> CharsetObj("GB18030", -1, None),
    "GBK" -> CharsetObj("GBK", -1, None)
  )

  private val escToCharset: Map[Int, CharsetObj] = {
    val map = charsetObjsMap.values
      .filter(_.hasEscapeSeq)
      .map(co => co.escapeSequence.get.foldLeft(0)((i, b) => (i << 8) + (b & 0xff)) -> co)
      .toMap

    // ISO 2022 IR 13 has two escape sequences
    map + (0x284a -> map(0x2949))
  }

  val utf8Charset: Charset = StandardCharsets.UTF_8
  val defaultCharset: Charset = StandardCharsets.ISO_8859_1
  val defaultCharsetObj = new CharsetObj(defaultCharset, 1, None)
  val defaultOnly = new CharacterSets(Array[String](""))

  def apply(specificCharacterSetValue: ValueElement): CharacterSets = {
    val s = specificCharacterSetValue.value.toStrings(specificCharacterSetValue.vr, specificCharacterSetValue.bigEndian, defaultOnly)
    if (s.isEmpty || s.length == 1 && s.head.isEmpty) defaultOnly else new CharacterSets(s)
  }

  def apply(specificCharacterSetBytes: ByteString): CharacterSets =
    apply(ValueElement(Tag.SpecificCharacterSet, VR.CS, Value(specificCharacterSetBytes), bigEndian = false, explicitVR = true))

  def isVrAffectedBySpecificCharacterSet(vr: VR): Boolean =
    vr match {
      case VR.LO => true
      case VR.LT => true
      case VR.PN => true
      case VR.SH => true
      case VR.ST => true
      case VR.UT => true
      case _ => false
    }

  def encode(s: String) = ByteString(s.getBytes(utf8Charset))
}

case class CharsetObj(charset: Charset, charlength: Int, escapeSequence: Option[ByteString]) {
  def hasEscapeSeq: Boolean = escapeSequence.isDefined
}

object CharsetObj {
  def apply(charsetName: String, charlength: Int, escapeSequence: Option[ByteString]) =
    new CharsetObj(Charset.forName(charsetName), charlength, escapeSequence)

  def apply(charsetName: String) =
    new CharsetObj(Charset.forName(charsetName), 1, None)

}