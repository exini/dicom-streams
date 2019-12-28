package com.exini.dicom.data

import com.exini.dicom.BuildInfo
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ImplementationTest extends AnyFlatSpec with Matchers {

  "Name" should "be defined by the build" in {
    Implementation.name shouldBe BuildInfo.name
  }

  "Version name" should "be name follwed by version" in {
    Implementation.versionName should startWith(Implementation.name)
    Implementation.versionName should endWith(Implementation.version)
  }

  "Class UID" should "be a valid UID" in {
    Implementation.classUid.matches("""([0-9]+\.)+[0-9]+""") shouldBe true
  }
}
