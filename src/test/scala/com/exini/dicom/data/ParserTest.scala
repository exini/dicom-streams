package com.exini.dicom.data

import akka.util.ByteString
import com.exini.dicom.data.Compression.compress
import com.exini.dicom.data.Parsing.AttributeInfo
import org.scalatest.BeforeAndAfterAll
import org.scalatest.OptionValues._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class ParserTest extends AnyFlatSpecLike with Matchers with BeforeAndAfterAll {

  import com.exini.dicom.data.TestData._

  private def parse(bytes: ByteString): Elements = {
    val parser = new Parser()
    parser.parse(bytes)
    parser.result()
  }

  "The parser" should "produce a preamble, FMI tags and dataset tags for a complete DICOM file" in {
    val bytes = preamble ++ fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ personNameJohnDoe()
    val elements = parse(bytes)
    elements.toBytes() shouldBe bytes
  }

  it should "read DICOM data in chunks" in {
    val bytes = preamble ++ fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ personNameJohnDoe()
    val chunks = bytes.grouped(7).toList
    val parser = new Parser()
    for (chunk <- chunks.init)
      parser.parse(chunk, last = false)
    parser.parse(chunks.last)
    val elements = parser.result()
    elements.toBytes() shouldBe bytes
  }

  it should "read files without preamble but with FMI" in {
    val bytes = fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ personNameJohnDoe()
    val elements = parse(bytes)
    elements.toBytes(withPreamble = false) shouldBe bytes
  }

  it should "read a file with only FMI" in {
    val bytes = preamble ++ fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID()
    val elements = parse(bytes)
    elements.toBytes() shouldBe bytes
  }

  it should "read a file with neither FMI nor preamble" in {
    val bytes = personNameJohnDoe()
    val elements = parse(bytes)
    elements.toBytes(withPreamble = false) shouldBe bytes
  }

  it should "handle zero-length values" in {
    val bytes = ByteString(8, 0, 32, 0, 68, 65, 0, 0, 16, 0, 16, 0, 80, 78, 0, 0)
    val elements = parse(bytes)
    elements.getBytes(Tag.StudyDate) shouldBe Some(ByteString.empty)
    elements.getBytes(Tag.PatientName) shouldBe Some(ByteString.empty)
    elements.toBytes(withPreamble = false) shouldBe bytes
  }

  it should "output a warning when non-meta information is included in the header" in {
    val bytes = fmiGroupLength(transferSyntaxUID(), studyDate()) ++ transferSyntaxUID() ++ studyDate()
    val elements = parse(bytes)
    elements.toBytes(withPreamble = false) shouldBe bytes
  }

  it should "treat a preamble alone as a valid DICOM file" in {
    val bytes = preamble
    val elements = parse(bytes)
    elements.size shouldBe 0
    elements.toBytes() shouldBe bytes
  }

  it should "fail reading a truncated DICOM file" in {
    val bytes = preamble ++ fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ personNameJohnDoe()
    assertThrows[ParseException] {
      parse(bytes.dropRight(3))
    }
  }

  it should "inflate deflated dataset" in {
    val bytes = fmiGroupLength(transferSyntaxUID(UID.DeflatedExplicitVRLittleEndian)) ++
      transferSyntaxUID(UID.DeflatedExplicitVRLittleEndian) ++ compress(personNameJohnDoe() ++ studyDate())
    val elements = parse(bytes)
    elements.toBytes(withPreamble = false) shouldBe bytes
  }

  it should "inflate deflated dataset served in chunks" in {
    val bytes = fmiGroupLength(transferSyntaxUID(UID.DeflatedExplicitVRLittleEndian)) ++
      transferSyntaxUID(UID.DeflatedExplicitVRLittleEndian) ++ compress(personNameJohnDoe() ++ studyDate())
    val chunks = bytes.grouped(7).toList
    val parser = new Parser()
    for (chunk <- chunks.init)
      parser.parse(chunk, last = false)
    parser.parse(chunks.last)
    val elements = parser.result()
    elements.toBytes(withPreamble = false) shouldBe bytes
  }

  it should "inflate gzip deflated datasets (with warning id)" in {
    val bytes = fmiGroupLength(transferSyntaxUID(UID.DeflatedExplicitVRLittleEndian)) ++
      transferSyntaxUID(UID.DeflatedExplicitVRLittleEndian) ++ compress(personNameJohnDoe() ++ studyDate(), gzip = true)
    parse(bytes)
    succeed // make sure no exception is thrown and check console for warning message
  }

  it should "read DICOM data with fragments" in {
    val bytes = pixeDataFragments() ++ item(4) ++ ByteString(1, 2, 3, 4) ++ item(4) ++
      ByteString(5, 6, 7, 8) ++ sequenceDelimitation()
    val elements = parse(bytes)
    elements.toBytes(withPreamble = false) shouldBe bytes
  }

  it should "issue a warning when a fragments delimitation tag has nonzero length" in {
    val bytes = pixeDataFragments() ++ item(4) ++ ByteString(1, 2, 3, 4) ++ item(4) ++
      ByteString(5, 6, 7, 8) ++ sequenceEndNonZeroLength()
    parse(bytes)
    succeed
  }

  it should "parse a tag which is not an item, item data nor fragments delimitation inside fragments as unknown" in {
    val bytes = pixeDataFragments() ++ item(4) ++ ByteString(1, 2, 3, 4) ++ studyDate() ++ item(4) ++
      ByteString(5, 6, 7, 8) ++ sequenceDelimitation()
    parse(bytes)
    succeed
  }

  it should "read DICOM data containing a sequence" in {
    val bytes = sequence(Tag.DerivationCodeSequence) ++ item() ++ personNameJohnDoe() ++ studyDate() ++
      itemDelimitation() ++ sequenceDelimitation()
    val elements = parse(bytes)
    elements.toBytes(withPreamble = false) shouldBe bytes
  }

  it should "read DICOM data containing a sequence in a sequence" in {
    val bytes = sequence(Tag.DerivationCodeSequence) ++ item() ++ sequence(Tag.DerivationCodeSequence) ++ item() ++
      personNameJohnDoe() ++ itemDelimitation() ++ sequenceDelimitation() ++ studyDate() ++ itemDelimitation() ++
      sequenceDelimitation()
    val elements = parse(bytes)
    elements.toBytes(withPreamble = false) shouldBe bytes
  }

  it should "not accept a non-DICOM file" in {
    val bytes = ByteString(1, 2, 3, 4, 5, 6, 7, 8, 9)
    assertThrows[ParseException] {
      parse(bytes)
    }
  }

  it should "read DICOM files with explicit VR big-endian transfer syntax" in {
    val bytes = preamble ++ fmiGroupLength(transferSyntaxUID(UID.ExplicitVRBigEndianRetired)) ++
      transferSyntaxUID(UID.ExplicitVRBigEndianRetired) ++ personNameJohnDoe(bigEndian = true)
    val elements = parse(bytes)
    elements.toBytes() shouldBe bytes
  }

  it should "read DICOM files with implicit VR little endian transfer syntax" in {
    val bytes = preamble ++ fmiGroupLength(transferSyntaxUID(UID.ImplicitVRLittleEndian)) ++
      transferSyntaxUID(UID.ImplicitVRLittleEndian) ++ personNameJohnDoe(explicitVR = false)
    val elements = parse(bytes)
    elements.toBytes() shouldBe bytes
  }

  it should "accept meta information encoded with implicit VR" in {
    val bytes = preamble ++ transferSyntaxUID(UID.ExplicitVRLittleEndian, explicitVR = false) ++ personNameJohnDoe()
    val elements = parse(bytes)
    elements.toBytes() shouldBe bytes
  }

  it should "handle sequences and items of determinate length" in {
    val bytes = studyDate() ++ sequence(Tag.DerivationCodeSequence, 8 + 16 + 16) ++ item(16 + 16) ++
      studyDate() ++ personNameJohnDoe() ++ personNameJohnDoe()
    val elements = parse(bytes)
    elements.toBytes(withPreamble = false) shouldBe bytes
  }

  it should "handle fragments with empty basic offset table (first item)" in {
    val bytes = pixeDataFragments() ++ item(0) ++ item(4) ++ ByteString(1, 2, 3, 4) ++
      sequenceDelimitation()
    val elements = parse(bytes)
    elements.toBytes(withPreamble = false) shouldBe bytes
  }

  it should "parse sequences with VR UN as a block of bytes" in {
    val unSequence = tagToBytes(Tag.CTExposureSequence) ++ ByteString("UN") ++ ByteString(0, 0) ++ intToBytes(24)
    val bytes = personNameJohnDoe() ++ unSequence ++ item(16) ++ studyDate()
    val elements = parse(bytes)
    elements.toBytes(withPreamble = false) shouldBe bytes
  }

  it should "parse sequences with VR UN, and where the nested data set(s) have implicit VR, as a block of bytes" in {
    val unSequence = tagToBytes(Tag.CTExposureSequence) ++ ByteString("UN") ++ ByteString(0, 0) ++ intToBytes(24)
    val bytes = personNameJohnDoe() ++ unSequence ++ item(16) ++ studyDate(explicitVR = false)
    val elements = parse(bytes)
    elements.toBytes(withPreamble = false) shouldBe bytes
  }

  it should "stop parsing early based on the input stop condition" in {
    val bytes = studyDate() ++ sequence(Tag.DerivationCodeSequence) ++ item() ++ personNameJohnDoe() ++
      itemDelimitation() ++ sequenceDelimitation() ++ personNameJohnDoe()

    val stop = (info: AttributeInfo, depth: Int) => depth == 0 && info.tag >= Tag.PatientName

    val parser = new Parser(Some(stop))

    parser.parse(bytes)
    val elements = parser.result()
    elements.contains(Tag.PatientName) shouldBe false
  }

  it should "handle sequences of indefinite length with VR UN with contents in implicit VR" in {
    val bytes = personNameJohnDoe() ++ cp264Sequence ++ item(60) ++
      element(Tag.CodeValue, "113691", explicitVR = false) ++
      element(Tag.CodingSchemeDesignator, "DCM", explicitVR = false) ++
      element(Tag.CodeMeaning, "IEC Body Dosimetry Phantom", explicitVR = false) ++
      sequenceDelimitation() ++ pixelData(10)
    val elements = parse(bytes)
    elements should have size 3
    elements.getSequence(Tag.CTDIPhantomTypeCodeSequence).value should have size 1
    elements.getSequence(Tag.CTDIPhantomTypeCodeSequence).value.item(1).value.elements should have size 3
  }
}
