package com.exini.dicom.data

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MultiplicityTest extends AnyFlatSpec with Matchers {

  "Multiplicity" should "support exactly 1" in {
    val m = Multiplicity.single
    m.min shouldBe 1
    m.max shouldBe Some(1)
    m.isBounded shouldBe true
    m.isUnbounded shouldBe false
    m.isSingle shouldBe true
    m.isMultiple shouldBe false
  }

  it should "support fixed multiplicities other than 1" in {
    val m = Multiplicity.fixed(5)
    m.min shouldBe 5
    m.max shouldBe Some(5)
    m.isBounded shouldBe true
    m.isUnbounded shouldBe false
    m.isSingle shouldBe false
    m.isMultiple shouldBe true
  }

  it should "support one to many multiplicities" in {
    val m = Multiplicity.oneToMany
    m.min shouldBe 1
    m.max shouldBe None
    m.isBounded shouldBe false
    m.isUnbounded shouldBe true
    m.isSingle shouldBe false
    m.isMultiple shouldBe true
  }

  it should "support bounded multiplicities" in {
    val m = Multiplicity.bounded(2, 5)
    m.min shouldBe 2
    m.max shouldBe Some(5)
    m.isBounded shouldBe true
    m.isUnbounded shouldBe false
    m.isSingle shouldBe false
    m.isMultiple shouldBe true
  }

  it should "support unbounded multiplicities" in {
    val m = Multiplicity.unbounded(2)
    m.min shouldBe 2
    m.max shouldBe None
    m.isBounded shouldBe false
    m.isUnbounded shouldBe true
    m.isSingle shouldBe false
    m.isMultiple shouldBe true
  }
}
