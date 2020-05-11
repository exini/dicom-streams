package com.exini.dicom.data

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LookupTest extends AnyFlatSpec with Matchers {

  "The DICOM dictionary" should "support getting the value representation for a tag" in {
    Lookup.vrOf(Tag.PatientName) shouldBe VR.PN
  }

  it should "support getting the multiplicity for a tag" in {
    Lookup.vmOf(0x00041141) shouldBe Some(Multiplicity.bounded(1, 8))
    Lookup.vmOf(0x00031141) shouldBe None
  }

  it should "support getting the keyword for a tag" in {
    Lookup.keywordOf(Tag.PatientName) shouldBe Some("PatientName")
    Lookup.keywordOf(0x00031141) shouldBe None
  }

  it should "support getting the tag for a keyword" in {
    Lookup.tagOf("PatientName") shouldBe Tag.PatientName
  }

  it should "support listing all keywords" in {
    val keywords = Lookup.keywords()
    keywords.length should be > 4000
    keywords.contains("PatientName") shouldBe true
  }
}
