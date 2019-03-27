package com.exini.dicom.data

import org.scalatest.{FlatSpec, Matchers}
import com.exini.dicom.data.TagPath.EmptyTagPath

class TagPathTest extends FlatSpec with Matchers {

  import TagPath.TagPathTag

  "A tag path" should "have a legible string representation" in {
    val path = TagPath.fromItem(Tag.DerivationCodeSequence, 4).thenItem(Tag.DerivationCodeSequence, 3).thenItem(Tag.DerivationCodeSequence, 2).thenTag(Tag.PatientID)
    path.toString(lookup = false) shouldBe "(0008,9215)[4].(0008,9215)[3].(0008,9215)[2].(0010,0020)"
  }

  it should "support string representations with keywords instead of tag numbers where possible" in {
    val path = TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenItem(0x11110100, 3).thenItem(Tag.DetectorInformationSequence, 3).thenTag(Tag.PatientID)
    path.toString(lookup = true) shouldBe "DerivationCodeSequence[1].(1111,0100)[3].DetectorInformationSequence[3].PatientID"
  }

  "Comparing two tag paths" should "return false when comparing a tag path to itself" in {
    val path = TagPath.fromTag(1)
    path < path shouldBe false
  }

  it should "return false when comparing two equivalent tag paths" in {
    val aPath = TagPath.fromItem(1, 1).thenItem(1, 3).thenItem(1, 1).thenTag(2)
    val bPath = TagPath.fromItem(1, 1).thenItem(1, 3).thenItem(1, 1).thenTag(2)
    aPath < bPath shouldBe false
    bPath < aPath shouldBe false
  }

  it should "sort all combinations of types correctly when tag and item numbers match" in {
    TagPath.fromTag(1) < TagPath.fromTag(1) shouldBe false
    TagPath.fromTag(1) < TagPath.fromSequence(1) shouldBe false
    TagPath.fromTag(1) < TagPath.fromSequenceEnd(1) shouldBe false
    TagPath.fromTag(1) < TagPath.fromItem(1, 1) shouldBe false
    TagPath.fromTag(1) < TagPath.fromItemEnd(1, 1) shouldBe false
    TagPath.fromSequence(1) < TagPath.fromTag(1) shouldBe false
    TagPath.fromSequence(1) < TagPath.fromSequence(1) shouldBe false
    TagPath.fromSequence(1) < TagPath.fromSequenceEnd(1) shouldBe true
    TagPath.fromSequence(1) < TagPath.fromItem(1, 1) shouldBe true
    TagPath.fromSequence(1) < TagPath.fromItemEnd(1, 1) shouldBe true
    TagPath.fromSequenceEnd(1) < TagPath.fromTag(1) shouldBe false
    TagPath.fromSequenceEnd(1) < TagPath.fromSequence(1) shouldBe false
    TagPath.fromSequenceEnd(1) < TagPath.fromSequenceEnd(1) shouldBe false
    TagPath.fromSequenceEnd(1) < TagPath.fromItem(1, 1) shouldBe false
    TagPath.fromSequenceEnd(1) < TagPath.fromItemEnd(1, 1) shouldBe false
    TagPath.fromItem(1, 1) < TagPath.fromTag(1) shouldBe false
    TagPath.fromItem(1, 1) < TagPath.fromSequence(1) shouldBe false
    TagPath.fromItem(1, 1) < TagPath.fromSequenceEnd(1) shouldBe true
    TagPath.fromItem(1, 1) < TagPath.fromItem(1, 1) shouldBe false
    TagPath.fromItem(1, 1) < TagPath.fromItemEnd(1, 1) shouldBe true
    TagPath.fromItemEnd(1, 1) < TagPath.fromTag(1) shouldBe false
    TagPath.fromItemEnd(1, 1) < TagPath.fromSequence(1) shouldBe false
    TagPath.fromItemEnd(1, 1) < TagPath.fromSequenceEnd(1) shouldBe true
    TagPath.fromItemEnd(1, 1) < TagPath.fromItem(1, 1) shouldBe false
    TagPath.fromItemEnd(1, 1) < TagPath.fromItemEnd(1, 1) shouldBe false
  }

  it should "sort them by tag number for depth 0 paths" in {
    val aPath = TagPath.fromTag(1)
    val bPath = TagPath.fromTag(2)
    aPath < bPath shouldBe true
  }

  it should "sort them by tag number for depth 1 paths" in {
    val aPath = TagPath.fromItem(1, 1).thenTag(2)
    val bPath = TagPath.fromItem(1, 1).thenTag(1)
    aPath < bPath shouldBe false
  }

  it should "sort by earliest difference in deep paths" in {
    val aPath = TagPath.fromItem(1, 1).thenItem(1, 2).thenTag(2)
    val bPath = TagPath.fromItem(1, 1).thenItem(2, 2).thenTag(1)
    aPath < bPath shouldBe true
  }

  it should "sort longer lists after shorter lists when otherwise equivalent" in {
    val aPath = TagPath.fromItem(1, 1).thenItem(2, 2).thenItem(3, 3).thenTag(4)
    val bPath = TagPath.fromItem(1, 1).thenItem(2, 2).thenItem(3, 3)
    aPath < bPath shouldBe false
  }

  it should "sort by item number in otherwise equivalent paths" in {
    val aPath = TagPath.fromItem(1, 1).thenItem(1, 2).thenItem(1, 2).thenTag(2)
    val bPath = TagPath.fromItem(1, 1).thenItem(1, 3).thenItem(1, 2).thenTag(2)
    aPath < bPath shouldBe true
  }

  it should "not provide a particular ordering of two empty tag paths" in {
    EmptyTagPath < EmptyTagPath shouldBe false
  }

  it should "sort empty paths as less than any other path" in {
    EmptyTagPath < TagPath.fromTag(0) shouldBe true
    EmptyTagPath < TagPath.fromItem(0, 1) shouldBe true
  }

  it should "sort non-empty tag paths after empty paths" in {
    TagPath.fromTag(0) < EmptyTagPath shouldBe false
    TagPath.fromItem(0, 1) < EmptyTagPath shouldBe false

  }

  it should "establish a total ordering among tag paths encountered when parsing a file" in {
    val paths = List(
      EmptyTagPath, // preamble
      TagPath.fromTag(Tag.FileMetaInformationGroupLength), // FMI group length header
      TagPath.fromTag(Tag.TransferSyntaxUID), // Transfer syntax header
      TagPath.fromTag(Tag.StudyDate), // Patient name header
      TagPath.fromSequence(Tag.DerivationCodeSequence), // sequence start
      TagPath.fromItem(Tag.DerivationCodeSequence, 1), // item start
      TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.StudyDate), // study date header
      TagPath.fromItemEnd(Tag.DerivationCodeSequence, 1), // item end
      TagPath.fromItem(Tag.DerivationCodeSequence, 2), // item start
      TagPath.fromItem(Tag.DerivationCodeSequence, 2).thenSequence(Tag.EnergyWindowRangeSequence), // sequence start
      TagPath.fromItem(Tag.DerivationCodeSequence, 2).thenItem(Tag.EnergyWindowRangeSequence, 1), // item start
      TagPath.fromItem(Tag.DerivationCodeSequence, 2).thenItem(Tag.EnergyWindowRangeSequence, 1).thenTag(Tag.StudyDate), // Study date header
      TagPath.fromItem(Tag.DerivationCodeSequence, 2).thenItemEnd(Tag.EnergyWindowRangeSequence, 1), //  item end (inserted)
      TagPath.fromItem(Tag.DerivationCodeSequence, 2).thenSequenceEnd(Tag.EnergyWindowRangeSequence), // sequence end (inserted)
      TagPath.fromItemEnd(Tag.DerivationCodeSequence, 2), // item end
      TagPath.fromSequenceEnd(Tag.DerivationCodeSequence), // sequence end
      TagPath.fromTag(Tag.PatientName), // Patient name header
      TagPath.fromTag(Tag.PixelData), // fragments start
      TagPath.fromTag(Tag.PixelData), // item start
      TagPath.fromTag(Tag.PixelData), // fragment data
      TagPath.fromTag(Tag.PixelData)) // fragments end

    paths.reverse should not be paths
    paths.reverse.sortWith(_ < _) shouldBe paths
  }

  "Two tag paths" should "be equal if they point to the same path" in {
    val aPath = TagPath.fromItem(1, 1).thenItem(2, 2).thenItem(3, 3).thenTag(4)
    val bPath = TagPath.fromItem(1, 1).thenItem(2, 2).thenItem(3, 3).thenTag(4)
    aPath shouldBe bPath
  }

  it should "not be equal if item indices do not match" in {
    val aPath = TagPath.fromItem(1, 1).thenItem(2, 1).thenItem(3, 3).thenTag(4)
    val bPath = TagPath.fromItem(1, 1).thenItem(2, 2).thenItem(3, 3).thenTag(4)
    aPath should not be bPath
  }

  it should "not be equal if they point to different tags" in {
    val aPath = TagPath.fromItem(1, 1).thenItem(2, 2).thenItem(3, 3).thenTag(4)
    val bPath = TagPath.fromItem(1, 1).thenItem(2, 2).thenItem(3, 3).thenTag(5)
    aPath should not be bPath
  }

  it should "not be equal if they have different depths" in {
    val aPath = TagPath.fromItem(1, 1).thenItem(3, 3).thenTag(4)
    val bPath = TagPath.fromItem(1, 1).thenItem(2, 2).thenItem(3, 3).thenTag(4)
    aPath should not be bPath
  }

  it should "be equal if both are empty" in {
    EmptyTagPath == EmptyTagPath shouldBe true
  }

  it should "not be equal if they point to same tags but are of different types" in {
    TagPath.fromTag(1) should not be TagPath.fromSequence(1)
  }

  it should "support sequence and item end nodes" in {
    TagPath.fromSequenceEnd(1) shouldEqual TagPath.fromSequenceEnd(1)
    TagPath.fromSequenceEnd(1) should not equal TagPath.fromSequence(1)
    TagPath.fromItemEnd(1, 1) shouldEqual TagPath.fromItemEnd(1, 1)
    TagPath.fromItemEnd(1, 1) should not equal TagPath.fromItem(1, 1)
  }

  it should "should support equals documentation examples" in {
    TagPath.fromTag(0x00100010) shouldBe TagPath.fromTag(0x00100010)
    TagPath.fromTag(0x00100010) should not be TagPath.fromTag(0x00100020)
    TagPath.fromTag(0x00100010) should not be TagPath.fromItem(0x00089215, 1).thenTag(0x00100010)
    TagPath.fromItem(0x00089215, 3).thenTag(0x00100010) shouldBe TagPath.fromItem(0x00089215, 3).thenTag(0x00100010)
  }

  "The startsWith test" should "return true for equal paths" in {
    val aPath = TagPath.fromItem(1, 1).thenItem(2, 2).thenItem(3, 3).thenTag(4)
    val bPath = TagPath.fromItem(1, 1).thenItem(2, 2).thenItem(3, 3).thenTag(4)
    aPath.startsWith(bPath) shouldBe true
  }

  it should "return true for two empty paths" in {
    EmptyTagPath.startsWith(EmptyTagPath) shouldBe true
  }

  it should "return true when any path starts with empty path" in {
    val aPath = TagPath.fromTag(1)
    aPath.startsWith(EmptyTagPath) shouldBe true
  }

  it should "return false when empty path starts with non-empty path" in {
    val aPath = TagPath.fromTag(1)
    EmptyTagPath.startsWith(aPath) shouldBe false
  }

  it should "return false when subject path is longer than path" in {
    val aPath = TagPath.fromItem(1, 1).thenItem(2, 2).thenTag(4)
    val bPath = TagPath.fromItem(1, 1).thenItem(2, 2).thenItem(3, 3).thenTag(4)
    aPath.startsWith(bPath) shouldBe false
  }

  it should "return true when paths involving item indices are equal" in {
    val aPath = TagPath.fromItem(1, 4).thenItem(2, 2).thenItem(3, 2).thenTag(4)
    val bPath = TagPath.fromItem(1, 4).thenItem(2, 2).thenItem(3, 2).thenTag(4)
    aPath.startsWith(bPath) shouldBe true
  }

  it should "return true when subject path is subset of path" in {
    val aPath = TagPath.fromItem(1, 1).thenItem(2, 2).thenItem(3, 3).thenTag(4)
    val bPath = TagPath.fromItem(1, 1).thenItem(2, 2).thenItem(3, 3)
    aPath.startsWith(bPath) shouldBe true
  }

  it should "support sequence and item end nodes" in {
    TagPath.fromSequenceEnd(1 ).startsWith(TagPath.fromSequenceEnd(1)) shouldBe true
    TagPath.fromSequenceEnd(1 ).startsWith(TagPath.fromSequence(1)) shouldBe false
    TagPath.fromItemEnd(1, 1).startsWith(TagPath.fromItemEnd(1, 1)) shouldBe true
    TagPath.fromItemEnd(1, 1).startsWith(TagPath.fromItem(1, 1)) shouldBe false
  }

  it should "support startsWith documentation examples" in {
    TagPath.fromTag(0x00100010).startsWith(TagPath.fromTag(0x00100010)) shouldBe true
    TagPath.fromItem(0x00089215, 2).thenTag(0x00100010).startsWith(TagPath.fromItem(0x00089215, 2)) shouldBe true
    TagPath.fromItem(0x00089215, 2).thenTag(0x00100010).startsWith(EmptyTagPath) shouldBe true
    TagPath.fromItem(0x00089215, 2).thenTag(0x00100010).startsWith(TagPath.fromItem(0x00089215, 1)) shouldBe false
    TagPath.fromItem(0x00089215, 2).startsWith(TagPath.fromItem(0x00089215, 2).thenTag(0x00100010)) shouldBe false
  }

  "The endsWith test" should "return true when a longer tag ends with a shorter" in {
    val aPath = TagPath.fromItem(1, 3).thenTag(2)
    val bPath = TagPath.fromTag(2)
    aPath.endsWith(bPath) shouldBe true
  }

  it should "return true for two empty paths" in {
    EmptyTagPath.endsWith(EmptyTagPath) shouldBe true
  }

  it should "return true when checking if non-empty path ends with empty path" in {
    val aPath = TagPath.fromTag(1)
    aPath.endsWith(EmptyTagPath) shouldBe true
  }

  it should "return false when empty path starts with non-empty path" in {
    val aPath = TagPath.fromTag(1)
    EmptyTagPath.endsWith(aPath) shouldBe false
  }

  it should "return false when a shorter tag is compared to a longer" in {
    val aPath = TagPath.fromItem(1, 3).thenTag(2)
    val bPath = TagPath.fromTag(2)
    bPath.endsWith(aPath) shouldBe false
  }

  it should "return false when tag numbers do not match" in {
    val aPath = TagPath.fromItem(1, 3).thenTag(2)
    val bPath = TagPath.fromTag(4)
    aPath.endsWith(bPath) shouldBe false
  }

  it should "work also with deep sequences" in {
    val aPath = TagPath.fromItem(1, 3).thenItem(2, 4).thenItem(3, 5).thenTag(6)
    val bPath = TagPath.fromItem(2, 4).thenItem(3, 5).thenTag(6)
    aPath.endsWith(bPath) shouldBe true
  }

  it should "support sequence and item end nodes" in {
    TagPath.fromItem(1,1).thenSequenceEnd(2).endsWith(TagPath.fromSequenceEnd(2)) shouldBe true
    TagPath.fromItem(1,1).thenSequenceEnd(2).endsWith(TagPath.fromSequence(2)) shouldBe false
    TagPath.fromItem(1,1).thenItemEnd(2,2).endsWith(TagPath.fromItemEnd(2, 2)) shouldBe true
    TagPath.fromItem(1,1).thenItemEnd(2,2).endsWith(TagPath.fromItem(2, 12)) shouldBe false
  }

  it should "support endsWith documentation examples" in {
    TagPath.fromTag(0x00100010).endsWith(TagPath.fromTag(0x00100010)) shouldBe true
    TagPath.fromItem(0x00089215, 2).thenTag(0x00100010).endsWith(TagPath.fromTag(0x00100010)) shouldBe true
    TagPath.fromItem(0x00089215, 2).thenTag(0x00100010).endsWith(EmptyTagPath) shouldBe true
    TagPath.fromTag(0x00100010).endsWith(TagPath.fromItem(0x00089215, 2).thenTag(0x00100010)) shouldBe false
  }

  "Parsing a tag path" should "work for well-formed depth 0 tag paths" in {
    TagPathTag.parse("(0010,0010)") shouldBe TagPath.fromTag(Tag.PatientName)
  }

  it should "work for deep tag paths" in {
    TagPathTag.parse("(0008,9215)[1].(0008,9215)[666].(0010,0010)") shouldBe TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenItem(Tag.DerivationCodeSequence, 666).thenTag(Tag.PatientName)
  }

  it should "throw an exception for malformed strings" in {
    intercept[IllegalArgumentException] {
      TagPathTag.parse("abc")
    }
  }

  it should "throw an exception for empty strings" in {
    intercept[IllegalArgumentException] {
      TagPathTag.parse("")
    }
  }

  it should "accept both tag numbers and keywords" in {
    val ref = TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.PatientName)
    TagPathTag.parse("(0008,9215)[1].(0010,0010)") shouldBe ref
    TagPathTag.parse("DerivationCodeSequence[1].(0010,0010)") shouldBe ref
    TagPathTag.parse("(0008,9215)[1].PatientName") shouldBe ref
    TagPathTag.parse("DerivationCodeSequence[1].PatientName") shouldBe ref
  }

  "The drop operation" should "remove elements from the left" in {
    val path = TagPath.fromItem(1, 1).thenItem(2, 1).thenItem(3, 3).thenTag(4)
    path.drop(-100) shouldBe path
    path.drop(0) shouldBe path
    path.drop(1) shouldBe TagPath.fromItem(2, 1).thenItem(3, 3).thenTag(4)
    path.drop(2) shouldBe TagPath.fromItem(3, 3).thenTag(4)
    path.drop(3) shouldBe TagPath.fromTag(4)
    path.drop(4) shouldBe EmptyTagPath
    path.drop(100) shouldBe EmptyTagPath
  }

  it should "support sequence and item end nodes" in {
    TagPath.fromItemEnd(1,1).drop(1) shouldBe EmptyTagPath
    TagPath.fromSequenceEnd(1).drop(1) shouldBe EmptyTagPath
  }
}
