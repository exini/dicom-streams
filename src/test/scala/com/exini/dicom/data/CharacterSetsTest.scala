package com.exini.dicom.data

import akka.util.ByteString
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class CharacterSetsTest extends AnyFlatSpecLike with Matchers {

  "Parsing a DICOM file" should "parse an Arab name correctly" in {
    val nameCodePoints = "قباني^لنزار".codePoints().toArray
    val nameBytes      = ByteString(0xe2, 0xc8, 0xc7, 0xe6, 0xea, 0x5e, 0xe4, 0xe6, 0xd2, 0xc7, 0xd1)
    val charsets       = new CharacterSets(Seq("ISO_IR 127"))
    val name           = charsets.decode(VR.PN, nameBytes)
    checkPersonName(name, nameCodePoints)
  }

  it should "parse a French name correctly" in {
    val nameCodePoints = "Buc^Jérôme".codePoints().toArray
    val nameBytes      = ByteString(0x42, 0x75, 0x63, 0x5e, 0x4a, 0xe9, 0x72, 0xf4, 0x6d, 0x65)
    val charsets       = new CharacterSets(Seq("ISO_IR 100"))
    val name           = charsets.decode(VR.PN, nameBytes)
    checkPersonName(name, nameCodePoints)
  }

  it should "parse a German name correctly" in {
    val nameCodePoints = "Äneas^Rüdiger".codePoints().toArray
    val nameBytes      = ByteString(0xc4, 0x6e, 0x65, 0x61, 0x73, 0x5e, 0x52, 0xfc, 0x64, 0x69, 0x67, 0x65, 0x72)
    val charsets       = new CharacterSets(Seq("ISO_IR 100"))
    val name           = charsets.decode(VR.PN, nameBytes)
    checkPersonName(name, nameCodePoints)
  }

  it should "parse a Greek name correctly" in {
    val nameCodePoints = "Διονυσιος".codePoints().toArray
    val nameBytes      = ByteString(0xc4, 0xe9, 0xef, 0xed, 0xf5, 0xf3, 0xe9, 0xef, 0xf2)
    val charsets       = new CharacterSets(Seq("ISO_IR 126"))
    val name           = charsets.decode(VR.PN, nameBytes)
    checkPersonName(name, nameCodePoints)
  }

  it should "parse a Japanese name correctly (1)" in {
    val nameCodePoints = "Yamada^Tarou=山田^太郎=やまだ^たろう".codePoints().toArray
    val nameBytes = ByteString(0x59, 0x61, 0x6d, 0x61, 0x64, 0x61, 0x5e, 0x54, 0x61, 0x72, 0x6f, 0x75, 0x3d, 0x1b, 0x24,
      0x42, 0x3b, 0x33, 0x45, 0x44, 0x1b, 0x28, 0x42, 0x5e, 0x1b, 0x24, 0x42, 0x42, 0x40, 0x4f, 0x3a, 0x1b, 0x28, 0x42,
      0x3d, 0x1b, 0x24, 0x42, 0x24, 0x64, 0x24, 0x5e, 0x24, 0x40, 0x1b, 0x28, 0x42, 0x5e, 0x1b, 0x24, 0x42, 0x24, 0x3f,
      0x24, 0x6d, 0x24, 0x26, 0x1b, 0x28, 0x42)
    val charsets = new CharacterSets(Seq("", "ISO 2022 IR 87"))
    val name     = charsets.decode(VR.PN, nameBytes)
    checkPersonName(name, nameCodePoints)
  }

  it should "parse a Japanese name correctly (2)" in {
    val nameCodePoints = "ﾔﾏﾀﾞ^ﾀﾛｳ=山田^太郎=やまだ^たろう".codePoints().toArray
    val nameBytes = ByteString(0xd4, 0xcf, 0xc0, 0xde, 0x5e, 0xc0, 0xdb, 0xb3, 0x3d, 0x1b, 0x24, 0x42, 0x3b, 0x33, 0x45,
      0x44, 0x1b, 0x28, 0x4a, 0x5e, 0x1b, 0x24, 0x42, 0x42, 0x40, 0x4f, 0x3a, 0x1b, 0x28, 0x4a, 0x3d, 0x1b, 0x24, 0x42,
      0x24, 0x64, 0x24, 0x5e, 0x24, 0x40, 0x1b, 0x28, 0x4a, 0x5e, 0x1b, 0x24, 0x42, 0x24, 0x3f, 0x24, 0x6d, 0x24, 0x26,
      0x1b, 0x28, 0x4a)
    val charsets = new CharacterSets(Seq("ISO 2022 IR 13", "ISO 2022 IR 87"))
    val name     = charsets.decode(VR.PN, nameBytes)
    checkPersonName(name, nameCodePoints)
  }

  it should "parse a Hebrew name correctly" in {
    val nameCodePoints = "שרון^דבורה".codePoints().toArray
    val nameBytes      = ByteString(0xf9, 0xf8, 0xe5, 0xef, 0x5e, 0xe3, 0xe1, 0xe5, 0xf8, 0xe4)
    val charsets       = new CharacterSets(Seq("ISO_IR 138"))
    val name           = charsets.decode(VR.PN, nameBytes)
    checkPersonName(name, nameCodePoints)
  }

  it should "parse a Korean name correctly" in {
    val nameCodePoints = "Hong^Gildong=洪^吉洞=홍^길동".codePoints().toArray
    val nameBytes = ByteString(0x48, 0x6f, 0x6e, 0x67, 0x5e, 0x47, 0x69, 0x6c, 0x64, 0x6f, 0x6e, 0x67, 0x3d, 0x1b, 0x24,
      0x29, 0x43, 0xfb, 0xf3, 0x5e, 0x1b, 0x24, 0x29, 0x43, 0xd1, 0xce, 0xd4, 0xd7, 0x3d, 0x1b, 0x24, 0x29, 0x43, 0xc8,
      0xab, 0x5e, 0x1b, 0x24, 0x29, 0x43, 0xb1, 0xe6, 0xb5, 0xbf)
    val charsets = new CharacterSets(Seq("", "ISO 2022 IR 149"))
    val name     = charsets.decode(VR.PN, nameBytes)
    checkPersonName(name, nameCodePoints)
  }

  it should "parse a Russian name correctly" in {
    val nameCodePoints = "Люкceмбypг".codePoints().toArray
    val nameBytes      = ByteString(0xbb, 0xee, 0xda, 0x63, 0x65, 0xdc, 0xd1, 0x79, 0x70, 0xd3)
    val charsets       = new CharacterSets(Seq("ISO_IR 144"))
    val name           = charsets.decode(VR.PN, nameBytes)
    checkPersonName(name, nameCodePoints)
  }

  it should "parse a Chinese name correctly (1)" in {
    val nameCodePoints = "Wang^XiaoDong=王^小東=".codePoints().toArray
    val nameBytes = ByteString(0x57, 0x61, 0x6e, 0x67, 0x5e, 0x58, 0x69, 0x61, 0x6f, 0x44, 0x6f, 0x6e, 0x67, 0x3d, 0xe7,
      0x8e, 0x8b, 0x5e, 0xe5, 0xb0, 0x8f, 0xe6, 0x9d, 0xb1, 0x3d)
    val charsets = new CharacterSets(Seq("ISO_IR 192"))
    val name     = charsets.decode(VR.PN, nameBytes)
    checkPersonName(name, nameCodePoints)
  }

  it should "parse a Chinese name correctly (2)" in {
    val nameCodePoints = "Wang^XiaoDong=王^小东=".codePoints().toArray
    val nameBytes = ByteString(0x57, 0x61, 0x6e, 0x67, 0x5e, 0x58, 0x69, 0x61, 0x6f, 0x44, 0x6f, 0x6e, 0x67, 0x3d, 0xcd,
      0xf5, 0x5e, 0xd0, 0xa1, 0xb6, 0xab, 0x3d)
    val charsets = new CharacterSets(Seq("GB18030"))
    val name     = charsets.decode(VR.PN, nameBytes)
    checkPersonName(name, nameCodePoints)
  }

  "CharacterSets" should "be comparable" in {
    val cs1 = new CharacterSets(Seq("ISO 2022 IR 13", "ISO 2022 IR 87"))
    val cs2 = new CharacterSets(Seq("ISO 2022 IR 13", "ISO 2022 IR 87"))
    cs1 shouldBe cs2
    cs1.hashCode shouldBe cs2.hashCode
  }

  private def checkPersonName(name: String, expectedCodePoints: Array[Int]): Unit = {
    val codePoints = new Array[Int](name.codePointCount(0, name.length))
    val length     = name.length
    var i          = 0
    var offset     = 0
    while (offset < length) {
      val codePoint = name.codePointAt(offset)
      codePoints(i) = codePoint
      i += 1
      offset += Character.charCount(codePoint)
    }
    codePoints shouldBe expectedCodePoints
  }

}
