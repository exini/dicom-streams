package com.exini.dicom.data

import org.scalatest.{FlatSpec, Matchers}

class DictionaryTest extends FlatSpec with Matchers {

  "The DICOM dictionary" should "support getting the value representation for a tag" in {
    Dictionary.vrOf(Tag.PatientName) shouldBe VR.PN
  }

  it should "support getting the multiplicity for a tag" in {
    Dictionary.vmOf(0x00041141) shouldBe Multiplicity.bounded(1, 8)
  }

  it should "support getting the keyword for a tag" in {
    Dictionary.keywordOf(Tag.PatientName) shouldBe "PatientName"
  }

  it should "support getting the tag for a keyword" in {
    Dictionary.tagOf("PatientName") shouldBe Tag.PatientName
  }

  it should "support listing all keywords" in {
    val keywords = Dictionary.keywords()
    keywords.length should be > 4000
    keywords.contains("PatientName") shouldBe true
  }
}
