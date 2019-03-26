package com.exini.dicom.data

import akka.util.ByteString
import org.scalatest.{FlatSpecLike, Matchers}

class CharacterSetsTest extends FlatSpecLike with Matchers {

  "Parsing a DICOM file" should "parse an Arab name correctly" in {
    val nameCodePoints = "قباني^لنزار".codePoints().toArray
    val nameBytes = ByteString(0xE2, 0xC8, 0xC7, 0xE6, 0xEA, 0x5E, 0xE4, 0xE6, 0xD2, 0xC7, 0xD1)
    val charsets = new CharacterSets(Seq("ISO_IR 127"))
    val name = charsets.decode(VR.PN, nameBytes)
    checkPatientName(name, nameCodePoints)
  }

  it should "parse a French name correctly" in {
    val nameCodePoints = "Buc^Jérôme".codePoints().toArray
    val nameBytes = ByteString(0x42, 0x75, 0x63, 0x5E, 0x4A, 0xE9, 0x72, 0xF4, 0x6D, 0x65)
    val charsets = new CharacterSets(Seq("ISO_IR 100"))
    val name = charsets.decode(VR.PN, nameBytes)
    checkPatientName(name, nameCodePoints)
  }

  it should "parse a German name correctly" in {
    val nameCodePoints = "Äneas^Rüdiger".codePoints().toArray
    val nameBytes = ByteString(0xC4, 0x6E, 0x65, 0x61, 0x73, 0x5E, 0x52, 0xFC, 0x64, 0x69, 0x67, 0x65, 0x72)
    val charsets = new CharacterSets(Seq("ISO_IR 100"))
    val name = charsets.decode(VR.PN, nameBytes)
    checkPatientName(name, nameCodePoints)
  }

  it should "parse a Greek name correctly" in {
    val nameCodePoints = "Διονυσιος".codePoints().toArray
    val nameBytes = ByteString(0xC4, 0xE9, 0xEF, 0xED, 0xF5, 0xF3, 0xE9, 0xEF, 0xF2)
    val charsets = new CharacterSets(Seq("ISO_IR 126"))
    val name = charsets.decode(VR.PN, nameBytes)
    checkPatientName(name, nameCodePoints)
  }

  it should "parse a Japanese name correctly (1)" in {
    val nameCodePoints = "Yamada^Tarou=山田^太郎=やまだ^たろう".codePoints().toArray
    val nameBytes = ByteString(0x59, 0x61, 0x6D, 0x61, 0x64, 0x61, 0x5E, 0x54, 0x61, 0x72, 0x6F, 0x75, 0x3D, 0x1B, 0x24, 0x42, 0x3B, 0x33, 0x45, 0x44, 0x1B, 0x28, 0x42, 0x5E, 0x1B, 0x24, 0x42, 0x42, 0x40, 0x4F, 0x3A, 0x1B, 0x28, 0x42, 0x3D, 0x1B, 0x24, 0x42, 0x24, 0x64, 0x24, 0x5E, 0x24, 0x40, 0x1B, 0x28, 0x42, 0x5E, 0x1B, 0x24, 0x42, 0x24, 0x3F, 0x24, 0x6D, 0x24, 0x26, 0x1B, 0x28, 0x42)
    val charsets = new CharacterSets(Seq("","ISO 2022 IR 87"))
    val name = charsets.decode(VR.PN, nameBytes)
    checkPatientName(name, nameCodePoints)
  }

  it should "parse a Japanese name correctly (2)" in {
    val nameCodePoints = "ﾔﾏﾀﾞ^ﾀﾛｳ=山田^太郎=やまだ^たろう".codePoints().toArray
    val nameBytes = ByteString(0xD4, 0xCF, 0xC0, 0xDE, 0x5E, 0xC0, 0xDB, 0xB3, 0x3D, 0x1B, 0x24, 0x42, 0x3B, 0x33, 0x45, 0x44, 0x1B, 0x28, 0x4A, 0x5E, 0x1B, 0x24, 0x42, 0x42, 0x40, 0x4F, 0x3A, 0x1B, 0x28, 0x4A, 0x3D, 0x1B, 0x24, 0x42, 0x24, 0x64, 0x24, 0x5E, 0x24, 0x40, 0x1B, 0x28, 0x4A, 0x5E, 0x1B, 0x24, 0x42, 0x24, 0x3F, 0x24, 0x6D, 0x24, 0x26, 0x1B, 0x28, 0x4A)
    val charsets = new CharacterSets(Seq("ISO 2022 IR 13", "ISO 2022 IR 87"))
    val name = charsets.decode(VR.PN, nameBytes)
    checkPatientName(name, nameCodePoints)
  }

  it should "parse a Hebrew name correctly" in {
    val nameCodePoints = "שרון^דבורה".codePoints().toArray
    val nameBytes = ByteString(0xF9, 0xF8, 0xE5, 0xEF, 0x5E, 0xE3, 0xE1, 0xE5, 0xF8, 0xE4)
    val charsets = new CharacterSets(Seq("ISO_IR 138"))
    val name = charsets.decode(VR.PN, nameBytes)
    checkPatientName(name, nameCodePoints)
  }

  it should "parse a Korean name correctly" in {
    val nameCodePoints = "Hong^Gildong=洪^吉洞=홍^길동".codePoints().toArray
    val nameBytes = ByteString(0x48, 0x6F, 0x6E, 0x67, 0x5E, 0x47, 0x69, 0x6C, 0x64, 0x6F, 0x6E, 0x67, 0x3D, 0x1B, 0x24, 0x29, 0x43, 0xFB, 0xF3, 0x5E, 0x1B, 0x24, 0x29, 0x43, 0xD1, 0xCE, 0xD4, 0xD7, 0x3D, 0x1B, 0x24, 0x29, 0x43, 0xC8, 0xAB, 0x5E, 0x1B, 0x24, 0x29, 0x43, 0xB1, 0xE6, 0xB5, 0xBF)
    val charsets = new CharacterSets(Seq("", "ISO 2022 IR 149"))
    val name = charsets.decode(VR.PN, nameBytes)
    checkPatientName(name, nameCodePoints)
  }

  it should "parse a Russian name correctly" in {
    val nameCodePoints = "Люкceмбypг".codePoints().toArray
    val nameBytes = ByteString(0xBB, 0xEE, 0xDA, 0x63, 0x65, 0xDC, 0xD1, 0x79, 0x70, 0xD3)
    val charsets = new CharacterSets(Seq("ISO_IR 144"))
    val name = charsets.decode(VR.PN, nameBytes)
    checkPatientName(name, nameCodePoints)
  }

  it should "parse a Chinese name correctly (1)" in {
    val nameCodePoints = "Wang^XiaoDong=王^小東=".codePoints().toArray
    val nameBytes = ByteString(0x57, 0x61, 0x6E, 0x67, 0x5E, 0x58, 0x69, 0x61, 0x6F, 0x44, 0x6F, 0x6E, 0x67, 0x3D, 0xE7, 0x8E, 0x8B, 0x5E, 0xE5, 0xB0, 0x8F, 0xE6, 0x9D, 0xB1, 0x3D)
    val charsets = new CharacterSets(Seq("ISO_IR 192"))
    val name = charsets.decode(VR.PN, nameBytes)
    checkPatientName(name, nameCodePoints)
  }

  it should "parse a Chinese name correctly (2)" in {
    val nameCodePoints = "Wang^XiaoDong=王^小东=".codePoints().toArray
    val nameBytes = ByteString(0x57, 0x61, 0x6E, 0x67, 0x5E, 0x58, 0x69, 0x61, 0x6F, 0x44, 0x6F, 0x6E, 0x67, 0x3D, 0xCD, 0xF5, 0x5E, 0xD0, 0xA1, 0xB6, 0xAB, 0x3D)
    val charsets = new CharacterSets(Seq("GB18030"))
    val name = charsets.decode(VR.PN, nameBytes)
    checkPatientName(name, nameCodePoints)
  }

  "CharacterSets" should "be comparable" in {
    val cs1 = new CharacterSets(Seq("ISO 2022 IR 13", "ISO 2022 IR 87"))
    val cs2 = new CharacterSets(Seq("ISO 2022 IR 13", "ISO 2022 IR 87"))
    cs1 shouldBe cs2
    cs1.hashCode shouldBe cs2.hashCode
  }

  private def checkPatientName(name: String, expectedCodePoints: Array[Int]): Unit = {
    val codePoints = new Array[Int](name.codePointCount(0, name.length))
    val length = name.length
    var i = 0
    var offset = 0
    while (offset < length) {
      val codePoint = name.codePointAt(offset)
      codePoints(i) = codePoint
      i += 1
      offset += Character.charCount(codePoint)
    }
    codePoints shouldBe expectedCodePoints
  }

}
