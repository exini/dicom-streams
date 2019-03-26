package com.exini.dicom.data

import org.scalatest.{FlatSpec, Matchers}

class TagPathLikeTest extends FlatSpec with Matchers {

  import TagPath._

  "The tag path depth" should "be 1 when pointing to a tag in the root dataset" in {
    val path = TagPath.fromTag(Tag.PatientID)
    path.depth shouldBe 1
  }

  it should "be 0 for empty tag paths" in {
    EmptyTagPath.depth shouldBe 0
  }

  it should "be 4 when pointing to a tag in three levels of sequences" in {
    val path = TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenItem(Tag.DerivationCodeSequence, 3).thenItem(Tag.DerivationCodeSequence, 5).thenTag(Tag.PatientID)
    path.depth shouldBe 4
  }

  "A tag path" should "be root when pointing to root dataset" in {
    val path = TagPath.fromTag(Tag.PatientID)
    path.isRoot shouldBe true
  }

  it should "not be root when pointing to a tag in a sequence" in {
    val path = TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.PatientID)
    path.isRoot shouldBe false
  }

  "A list representation of tag path tags" should "contain a single entry for a tag in the root dataset" in {
    val path = TagPath.fromTag(Tag.PatientID)
    path.toList shouldBe path :: Nil
  }

  it should "contain four entries for a path of depth 3" in {
    val path = TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenItem(Tag.DerivationCodeSequence, 3).thenItem(Tag.DerivationCodeSequence, 2).thenTag(Tag.PatientID)
    path.toList shouldBe path.previous.previous.previous :: path.previous.previous :: path.previous :: path :: Nil
  }

  "The contains test" should "return for any tag number on the tag path" in {
    val path = TagPath.fromItem(1, 1).thenItem(2, 2).thenTag(3)
    path.contains(1) shouldBe true
    path.contains(2) shouldBe true
    path.contains(3) shouldBe true
    path.contains(4) shouldBe false
  }

  "The take operation" should "preserve elements from the left" in {
    val path = TagPath.fromItem(1, 1).thenItem(2, 1).thenItem(3, 3).thenTag(4)
    path.take(-100) shouldBe EmptyTagPath
    path.take(0) shouldBe EmptyTagPath
    path.take(1) shouldBe TagPath.fromItem(1, 1)
    path.take(2) shouldBe TagPath.fromItem(1, 1).thenItem(2, 1)
    path.take(3) shouldBe TagPath.fromItem(1, 1).thenItem(2, 1).thenItem(3, 3)
    path.take(4) shouldBe path
    path.take(100) shouldBe path
  }

  "The head of a tag path" should "be the root element of the path" in {
    TagPath.fromTag(1).head shouldBe TagPath.fromTag(1)
    TagPath.fromItem(1, 1).head shouldBe TagPath.fromItem(1, 1)
    TagPath.fromItem(1, 1).thenTag(2).head shouldBe TagPath.fromItem(1, 1)
    TagPath.fromItem(1, 1).thenItem(2, 2).thenTag(3).head shouldBe TagPath.fromItem(1, 1)
  }

  "The tail of a tag path" should "be the whole part except the root element" in {
    TagPath.fromTag(1).tail shouldBe EmptyTagPath
    TagPath.fromItem(1, 1).tail shouldBe EmptyTagPath
    TagPath.fromItem(1, 1).thenTag(2).tail shouldBe TagPath.fromTag(2)
    TagPath.fromItem(1, 1).thenItem(2, 1).thenTag(3).tail shouldBe TagPath.fromItem(2, 1).thenTag(3)
  }
}
