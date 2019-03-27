package com.exini.dicom.data

import org.scalatest.{FlatSpec, Matchers}


class KeywordToTagTest extends FlatSpec with Matchers {

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
