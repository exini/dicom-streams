package com.exini.dicom.data

import org.scalatest.{FlatSpec, Matchers}
import com.exini.dicom.BuildInfo

class ImplementationTest extends FlatSpec with Matchers {

  "Name" should "be defined by the build" in {
    Implementation.name shouldBe BuildInfo.name
  }

  "Version name" should "be name follwed by version" in {
    Implementation.versionName should startWith (Implementation.name)
    Implementation.versionName should endWith (Implementation.version)
  }

  "Class UID" should "be a valid UID" in {
    Implementation.classUid.matches("""([0-9]+\.)+[0-9]+""") shouldBe true
  }
}
