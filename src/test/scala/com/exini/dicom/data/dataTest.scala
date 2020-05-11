package com.exini.dicom.data

import java.nio.ByteBuffer

import akka.util.ByteString
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class dataTest extends AnyFlatSpec with Matchers {

  "Converting an numbers to hex strings" should "convert the int 1194684 to 123ABC" in {
    intToHexString(1194684) shouldBe "00123ABC"
  }

  it should "convert the tag 0x00100020 to (0010, 0020)" in {
    tagToString(0x00100020) shouldBe "(0010,0020)"
  }

  it should "convert the byte 0x2A to 2A" in {
    byteToHexString(0x2a.toByte) shouldBe "2A"
  }

  it should "convert the short 0xFEDC to FEDC" in {
    shortToHexString(0xfedc.toShort) shouldBe "FEDC"
  }

  it should "convert the long 0x1234567887654321 to 1234567887654321" in {
    longToHexString(0x1234567887654321L) shouldBe "1234567887654321"
  }

  "Creating a UID" should "create a random UID" in {
    val uid = createUID()
    uid.startsWith("2.25") shouldBe true
    uid.matches("""([0-9]+\.)+[0-9]+""") shouldBe true
    uid should not be createUID()
  }

  it should "create a random UID with specified root" in {
    val uid = createUID("6.66.666")
    uid.startsWith("6.66.666") shouldBe true
  }

  it should "create a name based UID" in {
    val uid1 = createNameBasedUID(ByteString("name"))
    val uid2 = createNameBasedUID(ByteString("name"))
    uid1.startsWith("2.25") shouldBe true
    uid1 shouldBe uid2
  }

  it should "create a name based UID with specified root" in {
    val uid1 = createNameBasedUID(ByteString("name"), "6.66.666")
    val uid2 = createNameBasedUID(ByteString("name"), "6.66.666")
    uid1.startsWith("6.66.666") shouldBe true
    uid1 shouldBe uid2
  }

  "Mapping bytes to numbers" should "transform a ByteString of size 2 to the correct short representation" in {
    val s1 = bytesToShort(ByteString(0xa, 0x5), bigEndian = true)
    s1 shouldBe a[java.lang.Short] // runtime type is java native short
    s1 shouldBe 0xa05
    val s2 = bytesToShort(ByteString(0xa, 0x5))
    s2 shouldBe a[java.lang.Short]
    s2 shouldBe 0x50a
  }

  it should "transform a ByteString of size 4 to the correct integer representation" in {
    val s1 = bytesToInt(ByteString(0xa, 0xb, 0xc, 0xd), bigEndian = true)
    s1 shouldBe a[java.lang.Integer] // runtime type is java native short
    s1 shouldBe 0x0a0b0c0d
    val s2 = bytesToInt(ByteString(0xa, 0xb, 0xc, 0xd))
    s2 shouldBe a[java.lang.Integer]
    s2 shouldBe 0x0d0c0b0a
  }

  it should "transform a ByteString of size 8 to the correct long representation" in {
    val s1 = bytesToLong(ByteString(1, 2, 3, 4, 5, 6, 7, 8), bigEndian = true)
    s1 shouldBe a[java.lang.Long]
    s1 shouldBe 0x0102030405060708L
    val s2 = bytesToLong(ByteString(1, 2, 3, 4, 5, 6, 7, 8))
    s2 shouldBe a[java.lang.Long]
    s2 shouldBe 0x0807060504030201L
  }

  it should "transform a ByteString of size 8 to the correct double representation" in {
    val beBytes =
      ByteString(
        ByteBuffer.allocate(8).putLong(java.lang.Double.doubleToLongBits(math.Pi)).array()
      ) // java is big-endian
    val s1 = bytesToDouble(beBytes, bigEndian = true)
    s1 shouldBe a[java.lang.Double]
    s1 shouldBe math.Pi
    val s2 = bytesToDouble(beBytes.reverse)
    s2 shouldBe a[java.lang.Double]
    s2 shouldBe math.Pi
  }

  it should "transform a ByteString of size 4 to the correct float representation" in {
    val beBytes =
      ByteString(
        ByteBuffer.allocate(4).putInt(java.lang.Float.floatToIntBits(math.Pi.toFloat)).array()
      ) // java is big-endian
    val s1 = bytesToFloat(beBytes, bigEndian = true)
    s1 shouldBe a[java.lang.Float]
    s1 shouldBe math.Pi.toFloat
    val s2 = bytesToFloat(beBytes.reverse)
    s2 shouldBe a[java.lang.Float]
    s2 shouldBe math.Pi.toFloat
  }

  it should "transform a ByteString of size 2 to the correct unsigned short representation (as an Int)" in {
    val s1 = bytesToUShort(ByteString(0xff, 0xfe), bigEndian = true)
    s1 shouldBe a[java.lang.Integer]
    s1 shouldBe 0xfffe
    val s2 = bytesToUShort(ByteString(0xff, 0xfe))
    s2 shouldBe a[java.lang.Integer]
    s2 shouldBe 0xfeff
  }

  it should "parse a tag number given as two consecutive short numbers (big or little endian)" in {
    val s1 = ByteString(0xa, 0xb)
    val s2 = ByteString(1, 2)
    val t1 = bytesToTag(s1.concat(s2), bigEndian = true)
    t1 shouldBe a[java.lang.Integer]
    t1 shouldBe 0x0a0b0102
    val t2 = bytesToTag(s1.concat(s2))
    t2 shouldBe a[java.lang.Integer]
    t2 shouldBe 0x0b0a0201
  }

  it should "parse two bytes in big-endian order as a VR code" in {
    val vr = ByteString(0xab, 0xcd)
    bytesToVR(vr) shouldBe 0xabcd
  }

  "Mapping numbers to bytes" should "decode a short into bytes" in {
    shortToBytes(0xabcd.toShort, bigEndian = true) shouldBe ByteString(0xab, 0xcd)
    shortToBytes(0xabcd.toShort) shouldBe ByteString(0xcd, 0xab)
  }

  it should "decode an integer into bytes" in {
    intToBytes(0x01020304, bigEndian = true) shouldBe ByteString(1, 2, 3, 4)
    intToBytes(0x01020304) shouldBe ByteString(4, 3, 2, 1)
  }

  it should "handle unsigned int values" in {
    val length = Int.MaxValue.toLong + 1
    length.toInt shouldBe Int.MinValue
    intToUnsignedLong(length.toInt) shouldBe length
  }

}
