package com.exini.dicom.data

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class KeywordToTagTest extends AnyFlatSpec with Matchers {

  "A keyword" should "be correctly mapped to its corresponding tag" in {
    val realTag   = Tag.PatientName
    val mappedTag = KeywordToTag.tagOf("PatientName")
    mappedTag shouldBe Some(realTag)
  }

  it should "return None for unknown keywords" in {
    KeywordToTag.tagOf("oups") shouldBe None
  }
}
