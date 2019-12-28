package com.exini.dicom.data

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers


class KeywordToTagTest extends AnyFlatSpec with Matchers {

  "A keyword" should "be correctly mapped to its corresponding tag" in {
    val realTag = Tag.PatientName
    val mappedTag = KeywordToTag.tagOf("PatientName")
    mappedTag shouldBe realTag
  }

  it should "throw exception for unknown keywords" in {
    intercept[IllegalArgumentException] {
      KeywordToTag.tagOf("oups")
    }
  }
}
