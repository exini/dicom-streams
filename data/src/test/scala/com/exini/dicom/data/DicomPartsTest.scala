package com.exini.dicom.data

import com.exini.dicom.data.TestData._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class DicomPartsTest extends AnyFlatSpecLike with Matchers {

  import DicomParts._

  HeaderPart.getClass.getSimpleName should "should return a new header with modified length for explicitVR, LE" in {
    val header        = HeaderPart(Tag.PatientName, VR.PN, 8)
    val updatedHeader = header.withUpdatedLength(5)

    updatedHeader.length shouldEqual 5
    updatedHeader.bytes.take(6) shouldEqual header.bytes.take(6)
    updatedHeader.bytes(6) shouldEqual 5
    updatedHeader.bytes(7) shouldEqual 0
  }

  it should "should return a new header with modified length for explicitVR, BE" in {
    val header        = HeaderPart(Tag.PatientName, VR.PN, 8, bigEndian = true)
    val updatedHeader = header.withUpdatedLength(5)

    updatedHeader.length shouldEqual 5
    updatedHeader.bytes.take(6) shouldEqual header.bytes.take(6)
    updatedHeader.bytes(6) shouldEqual 0
    updatedHeader.bytes(7) shouldEqual 5
  }

  it should "should return a new header with modified length for explicitVR, LE with 12-byte headers" in {
    val header        = HeaderPart(Tag.PixelData, VR.OW, 100)
    val updatedHeader = header.withUpdatedLength(200)

    updatedHeader.length shouldEqual 200
    updatedHeader.bytes.take(8) shouldEqual header.bytes.take(8)
    updatedHeader.bytes.slice(8, 12) shouldEqual intToBytesLE(200)
  }

  it should "should return a new header with modified length for explicitVR, BE with 12-byte headers" in {
    val header        = HeaderPart(Tag.PixelData, VR.OW, 100, bigEndian = true)
    val updatedHeader = header.withUpdatedLength(200)

    updatedHeader.length shouldEqual 200
    updatedHeader.bytes.take(8) shouldEqual header.bytes.take(8)
    updatedHeader.bytes.slice(8, 12) shouldEqual intToBytesBE(200)
  }

  it should "should return a new header with modified length for implicitVR, LE" in {
    val header        = HeaderPart(Tag.PatientName, VR.PN, 8, explicitVR = false)
    val updatedHeader = header.withUpdatedLength(5)

    updatedHeader.length shouldEqual 5
    updatedHeader.bytes.take(4) shouldEqual header.bytes.take(4)
    updatedHeader.bytes(4) shouldEqual 5
    updatedHeader.bytes(5) shouldEqual 0
  }

  it should "create a valid explicit VR little endian byte sequence representation when constructed without explicit bytes" in {
    val bytes = HeaderPart(Tag.PatientName, VR.PN, 8).bytes
    bytes shouldBe personNameJohnDoe().take(8)
  }

  it should "create a valid implicit VR little endian byte sequence representation when constructed without explicit bytes" in {
    val bytes = HeaderPart(Tag.PatientName, VR.PN, 8, explicitVR = false).bytes
    bytes shouldBe personNameJohnDoe(explicitVR = false).take(8)
  }

  it should "create a valid explicit VR big endian byte sequence representation when constructed without explicit bytes" in {
    val bytes = HeaderPart(Tag.PatientName, VR.PN, 8, bigEndian = true).bytes
    bytes shouldBe personNameJohnDoe(bigEndian = true).take(8)
  }

  it should "create a valid explicit VR little endian 12-byte representation when constructed without explicit bytes" in {
    val bytes = HeaderPart(Tag.PixelData, VR.OW, 100).bytes
    bytes shouldBe pixelData(100).take(12)
  }

  it should "create a valid explicit VR big endian 12-byte representation when constructed without explicit bytes" in {
    val bytes = HeaderPart(Tag.PixelData, VR.OW, 100, bigEndian = true).bytes
    bytes shouldBe pixelData(100, bigEndian = true).take(12)
  }

}
