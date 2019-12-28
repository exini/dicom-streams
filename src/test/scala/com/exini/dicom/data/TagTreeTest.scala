package com.exini.dicom.data

import com.exini.dicom.data.TagPath.EmptyTagPath
import com.exini.dicom.data.TagTree.EmptyTagTree
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TagTreeTest extends AnyFlatSpec with Matchers {

  "A tag tree" should "have a legible string representation" in {
    val tree = TagTree.fromAnyItem(Tag.DerivationCodeSequence).thenItem(Tag.DerivationCodeSequence, 3).thenAnyItem(Tag.DerivationCodeSequence).thenTag(Tag.PatientID)
    tree.toString(lookup = false) shouldBe "(0008,9215)[*].(0008,9215)[3].(0008,9215)[*].(0010,0020)"
  }

  it should "support string representations with keywords instead of tag numbers where possible" in {
    val tree = TagTree.fromAnyItem(Tag.DerivationCodeSequence).thenItem(0x11110100, 3).thenAnyItem(Tag.DetectorInformationSequence).thenTag(Tag.PatientID)
    tree.toString(lookup = true) shouldBe "DerivationCodeSequence[*].(1111,0100)[3].DetectorInformationSequence[*].PatientID"
  }

  it should "be root when pointing to root dataset" in {
    val tree = TagTree.fromTag(Tag.PatientID)
    tree.isRoot shouldBe true
  }

  it should "not be root when pointing to a tag in a sequence" in {
    val tree = TagTree.fromAnyItem(Tag.DerivationCodeSequence).thenTag(Tag.PatientID)
    tree.isRoot shouldBe false
  }

  "Two tag trees" should "be equal if they point to the same tree" in {
    val aTree = TagTree.fromAnyItem(1).thenAnyItem(2).thenAnyItem(3).thenTag(4)
    val bTree = TagTree.fromAnyItem(1).thenAnyItem(2).thenAnyItem(3).thenTag(4)
    aTree shouldBe bTree
  }

  it should "not be equal if item indices do not match" in {
    val aTree = TagTree.fromAnyItem(1).thenItem(2, 1).thenAnyItem(3).thenTag(4)
    val bTree = TagTree.fromAnyItem(1).thenItem(2, 2).thenAnyItem(3).thenTag(4)
    aTree should not be bTree
  }

  it should "not be equal if they point to different tags" in {
    val aTree = TagTree.fromAnyItem(1).thenAnyItem(2).thenAnyItem(3).thenTag(4)
    val bTree = TagTree.fromAnyItem(1).thenAnyItem(2).thenAnyItem(3).thenTag(5)
    aTree should not be bTree
  }

  it should "not be equal if they have different depths" in {
    val aTree = TagTree.fromAnyItem(1).thenAnyItem(3).thenTag(4)
    val bTree = TagTree.fromAnyItem(1).thenAnyItem(2).thenAnyItem(3).thenTag(4)
    aTree should not be bTree
  }

  it should "not be equal if one points to all indices of a sequence and the other points to a specific index" in {
    val aTree = TagTree.fromAnyItem(1)
    val bTree = TagTree.fromItem(1, 1)
    aTree should not be bTree
  }

  it should "be equal if both are empty" in {
    EmptyTagTree == EmptyTagTree shouldBe true
  }

  it should "support equals documentation examples" in {
    TagTree.fromTag(0x00100010) shouldEqual TagTree.fromTag(0x00100010)
    TagTree.fromTag(0x00100010) should not equal TagTree.fromTag(0x00100020)
    TagTree.fromTag(0x00100010) should not equal TagTree.fromItem(0x00089215, 1).thenTag(0x00100010)
    TagTree.fromAnyItem(0x00089215).thenTag(0x00100010) should not equal TagTree.fromItem(0x00089215, 1).thenTag(0x00100010)
    TagTree.fromItem(0x00089215, 3).thenTag(0x00100010) shouldEqual TagTree.fromItem(0x00089215, 3).thenTag(0x00100010)
  }

  "The isPath test" should "support documentation examples" in {
    TagTree.fromItem(0x00089215, 3).thenTag(0x00100010).isPath shouldBe true
    TagTree.fromAnyItem(0x00089215).thenTag(0x00100010).isPath shouldBe false
    EmptyTagTree.isPath shouldBe true
  }

  "The hasPath test" should "return true for a path extending from root to leaf" in {
    TagTree.fromAnyItem(1).thenAnyItem(2).thenItem(3, 3).thenTag(4).hasPath(
      TagPath.fromItem(1, 1).thenItem(2, 2).thenItem(3, 3).thenTag(4)) shouldBe true
  }

  it should "return false for path not beginning at root" in {
    TagTree.fromItem(1, 1).thenTag(2).hasPath(TagPath.fromTag(2)) shouldBe false
  }

  it should "return false for path not ending at leaf" in {
    TagTree.fromItem(1, 1).thenTag(2).hasPath(TagPath.fromItem(1, 1)) shouldBe false
  }

  it should "work with sequence and item end nodes" in {
    TagTree.fromAnyItem(1).hasPath(TagPath.fromItemEnd(1, 1)) shouldBe true
    TagTree.fromItem(1, 1).hasPath(TagPath.fromItemEnd(1, 1)) shouldBe true
    TagTree.fromAnyItem(1).hasPath(TagPath.fromSequence(1)) shouldBe true
    TagTree.fromAnyItem(1).hasPath(TagPath.fromSequenceEnd(1)) shouldBe true
  }

  it should "support documentation examples" in {
    TagTree.fromAnyItem(0x00089215).thenTag(0x00100010).hasPath(TagPath.fromItem(0x00089215, 1).thenTag(0x00100010)) shouldBe true
    TagTree.fromAnyItem(0x00089215).thenTag(0x00100010).hasPath(TagPath.fromItem(0x00089215, 1)) shouldBe false
    EmptyTagTree.hasPath(EmptyTagPath) shouldBe true
  }

  "The hasTrunk test" should "return true for equally shaped tree and path" in {
    val tree = TagTree.fromItem(1, 1).thenItem(2, 2).thenItem(3, 3).thenTag(4)
    val path = TagPath.fromItem(1, 1).thenItem(2, 2).thenItem(3, 3).thenTag(4)
    tree.hasTrunk(path) shouldBe true
  }

  it should "return true for two empty structures" in {
    EmptyTagTree.hasTrunk(EmptyTagPath) shouldBe true
  }

  it should "return true when subject path is empty" in {
    val aTree = TagTree.fromTag(1)
    aTree.hasTrunk(EmptyTagPath) shouldBe true
  }

  it should "return false when empty tree starts with non-empty path" in {
    val path = TagPath.fromTag(1)
    EmptyTagTree.hasTrunk(path) shouldBe false
  }

  it should "return false when subject path is longer than tree" in {
    val tree = TagTree.fromItem(1, 1).thenItem(2, 2).thenTag(4)
    val path = TagPath.fromItem(1, 1).thenItem(2, 2).thenItem(3, 3).thenTag(4)
    tree.hasTrunk(path) shouldBe false
  }

  it should "return true when a tree with wildcards is compared to a path with item indices" in {
    val tree = TagTree.fromAnyItem(2).thenAnyItem(3).thenTag(4)
    val path = TagPath.fromItem(2, 4).thenItem(3, 66).thenTag(4)
    tree.hasTrunk(path) shouldBe true
  }

  it should "work with sequence start and end nodes" in {
    val tree1 = TagTree.fromItem(1, 1).thenTag(2)
    tree1.hasTrunk(TagPath.fromSequence(1)) shouldBe true
    tree1.hasTrunk(TagPath.fromSequenceEnd(1)) shouldBe true

    val tree2 = TagTree.fromAnyItem(1).thenTag(2)
    tree2.hasTrunk(TagPath.fromSequence(1)) shouldBe true
    tree2.hasTrunk(TagPath.fromSequenceEnd(1)) shouldBe true
  }

  it should "work with item start and end nodes" in {
    TagTree.fromAnyItem(1).thenTag(2).hasTrunk(TagPath.fromItemEnd(1, 1)) shouldBe true
    TagTree.fromItem(1, 1).thenTag(2).hasTrunk(TagPath.fromItemEnd(1, 1)) shouldBe true
  }

  it should "support documentation examples" in {
    TagTree.fromAnyItem(0x00089215).thenTag(0x00100010).hasTrunk(TagPath.fromItem(0x00089215, 1).thenTag(0x00100010)) shouldBe true
    TagTree.fromAnyItem(0x00089215).thenTag(0x00100010).hasTrunk(TagPath.fromItem(0x00089215, 1)) shouldBe true
    TagTree.fromAnyItem(0x00089215).thenTag(0x00100010).hasTrunk(TagPath.fromTag(0x00100010)) shouldBe false
  }

  "The isTrunkOf test" should "return true for a tag path extending out of a tag tree" in {
    TagTree.fromAnyItem(1).thenItem(2, 2).isTrunkOf(TagPath.fromItem(1, 1).thenItem(2, 2).thenTag(3)) shouldBe true
  }

  it should "return true for tree and path of equals length" in {
    TagTree.fromAnyItem(1).thenItem(2, 2).isTrunkOf(TagPath.fromItem(1, 1).thenItem(2, 2)) shouldBe true
  }

  it should "return false for a trunk of a tree" in {
    TagTree.fromAnyItem(1).thenItem(2, 2).isTrunkOf(TagPath.fromItem(1, 1)) shouldBe false
  }

  it should "work with sequence start and end nodes" in {
    TagTree.fromAnyItem(1).isTrunkOf(TagPath.fromSequence(1)) shouldBe true
    TagTree.fromAnyItem(1).isTrunkOf(TagPath.fromSequenceEnd(1)) shouldBe true
    TagTree.fromItem(1, 1).isTrunkOf(TagPath.fromSequence(1)) shouldBe false
    TagTree.fromItem(1, 1).isTrunkOf(TagPath.fromSequenceEnd(1)) shouldBe false
  }

  it should "work with item start and end nodes" in {
    TagTree.fromAnyItem(1).isTrunkOf(TagPath.fromItem(1, 1)) shouldBe true
    TagTree.fromAnyItem(1).isTrunkOf(TagPath.fromItemEnd(1, 1)) shouldBe true
    TagTree.fromItem(1, 1).isTrunkOf(TagPath.fromItem(1, 1)) shouldBe true
    TagTree.fromItem(1, 1).isTrunkOf(TagPath.fromItemEnd(1, 1)) shouldBe true
  }

  it should "support documentation examples" in {
    TagTree.fromItem(0x00089215, 1).thenTag(0x00100010).isTrunkOf(TagPath.fromItem(0x00089215, 1).thenTag(0x00100010)) shouldBe true
    TagTree.fromAnyItem(0x00089215).thenTag(0x00100010).isTrunkOf(TagPath.fromItem(0x00089215, 1).thenTag(0x00100010)) shouldBe true
    TagTree.fromAnyItem(0x00089215).isTrunkOf(TagPath.fromItem(0x00089215, 1).thenTag(0x00100010)) shouldBe true
    TagTree.fromItem(0x00089215, 3).isTrunkOf(TagPath.fromItem(0x00089215, 1).thenTag(0x00100010)) shouldBe false
    TagTree.fromTag(0x00100010).isTrunkOf(TagPath.fromItem(0x00089215, 1).thenTag(0x00100010)) shouldBe false
  }

  "The hasTwig test" should "return true when a longer tree ends with a shorter path" in {
    val tree = TagTree.fromItem(1, 3).thenTag(2)
    val path = TagPath.fromTag(2)
    tree.hasTwig(path) shouldBe true
  }

  it should "return true for empty tree and path" in {
    EmptyTagTree.hasTwig(EmptyTagPath) shouldBe true
  }

  it should "return false when checking if non-empty tree ends with empty path" in {
    val aTree = TagTree.fromTag(1)
    aTree.hasTwig(EmptyTagPath) shouldBe false
  }

  it should "return false when empty tree starts with non-empty path" in {
    val path = TagPath.fromTag(1)
    EmptyTagTree.hasTwig(path) shouldBe false
  }

  it should "return false when a shorter tree is compared to a longer path" in {
    val path = TagPath.fromItem(1, 3).thenTag(2)
    val tree = TagTree.fromTag(2)
    tree.hasTwig(path) shouldBe false
  }

  it should "return false when tag numbers do not match" in {
    val tree = TagTree.fromItem(1, 3).thenTag(2)
    val path = TagPath.fromTag(4)
    tree.hasTwig(path) shouldBe false
  }

  it should "work also with deep structures" in {
    val tree = TagTree.fromItem(1, 3).thenItem(2, 4).thenItem(3, 5).thenTag(6)
    val path = TagPath.fromItem(2, 4).thenItem(3, 5).thenTag(6)
    tree.hasTwig(path) shouldBe true
  }

  it should "work with sequence and item end nodes" in {
    TagTree.fromAnyItem(1).hasTwig(TagPath.fromItemEnd(1, 1)) shouldBe true
    TagTree.fromItem(1, 1).hasTwig(TagPath.fromItemEnd(1, 1)) shouldBe true
    TagTree.fromAnyItem(1).hasTwig(TagPath.fromSequence(1)) shouldBe true
    TagTree.fromAnyItem(1).hasTwig(TagPath.fromSequenceEnd(1)) shouldBe true
  }

  it should "support documentation examples" in {
    TagTree.fromAnyItem(0x00089215).thenTag(0x00100010).hasTwig(TagPath.fromItem(0x00089215, 1).thenTag(0x00100010)) shouldBe true
    TagTree.fromAnyItem(0x00089215).thenTag(0x00100010).hasTwig(TagPath.fromTag(0x00100010)) shouldBe true
    TagTree.fromAnyItem(0x00089215).thenTag(0x00100010).hasTwig(TagPath.fromItem(0x00089215, 1)) shouldBe false
  }

  "Creating a tag path from a tag tree" should "handle empty tag paths" in {
    TagTree.fromPath(EmptyTagPath) shouldBe EmptyTagTree
  }

  it should "handle simple paths" in {
    TagTree.fromPath(TagPath.fromTag(1)) shouldBe TagTree.fromTag(1)
  }

  it should "handle deep paths" in {
    TagTree.fromPath(TagPath.fromItem(1, 1).thenItem(2, 2).thenTag(3)) shouldBe TagTree.fromItem(1, 1).thenItem(2, 2).thenTag(3)
  }

  it should "handle sequence and item start and end nodes" in {
    TagTree.fromPath(TagPath.fromSequence(1)) shouldBe TagTree.fromAnyItem(1)
    TagTree.fromPath(TagPath.fromSequenceEnd(1)) shouldBe TagTree.fromAnyItem(1)
    TagTree.fromPath(TagPath.fromItemEnd(1, 1)) shouldBe TagTree.fromItem(1, 1)
  }

  "Parsing a tag tree" should "work for well-formed depth 0 tag trees" in {
    TagTree.parse("(0010,0010)") shouldBe TagTree.fromTag(Tag.PatientName)
  }

  it should "work for deep tag trees" in {
    TagTree.parse("(0008,9215)[*].(0008,9215)[666].(0010,0010)") shouldBe TagTree.fromAnyItem(Tag.DerivationCodeSequence).thenItem(Tag.DerivationCodeSequence, 666).thenTag(Tag.PatientName)
  }

  it should "throw an exception for malformed strings" in {
    intercept[IllegalArgumentException] {
      TagTree.parse("abc")
    }
  }

  it should "throw an exception for empty strings" in {
    intercept[IllegalArgumentException] {
      TagTree.parse("")
    }
  }

  it should "accept both tag numbers and keywords" in {
    val ref = TagTree.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.PatientName)
    TagTree.parse("(0008,9215)[1].(0010,0010)") shouldBe ref
    TagTree.parse("DerivationCodeSequence[1].(0010,0010)") shouldBe ref
    TagTree.parse("(0008,9215)[1].PatientName") shouldBe ref
    TagTree.parse("DerivationCodeSequence[1].PatientName") shouldBe ref
  }

  "The drop operation" should "remove elements from the left" in {
    val path = TagTree.fromItem(1, 1).thenItem(2, 1).thenItem(3, 3).thenTag(4)
    path.drop(-100) shouldBe path
    path.drop(0) shouldBe path
    path.drop(1) shouldBe TagTree.fromItem(2, 1).thenItem(3, 3).thenTag(4)
    path.drop(2) shouldBe TagTree.fromItem(3, 3).thenTag(4)
    path.drop(3) shouldBe TagTree.fromTag(4)
    path.drop(4) shouldBe EmptyTagTree
    path.drop(100) shouldBe EmptyTagTree
  }

}
