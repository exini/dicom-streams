package com.exini.dicom.data

import java.math.BigInteger
import java.net.URI
import java.time.{ LocalDate, LocalTime, ZoneOffset }

import com.exini.dicom.data.DicomElements._
import com.exini.dicom.data.DicomParts.HeaderPart
import com.exini.dicom.data.TagPath.EmptyTagPath
import com.exini.dicom.data.TestData._
import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ElementsTest extends AnyFlatSpec with Matchers {

  def create(elements: ElementSet*): Elements = Elements(CharacterSets.defaultOnly, systemZone, elements.toVector)

  val studyDate: ValueElement   = ValueElement.fromString(Tag.StudyDate, "20041230")
  val patientName: ValueElement = ValueElement.fromString(Tag.PatientName, "John^Doe")
  val patientID1: ValueElement  = ValueElement.fromString(Tag.PatientID, "12345678")
  val patientID2: ValueElement  = ValueElement.fromString(Tag.PatientID, "87654321")
  val patientID3: ValueElement  = ValueElement.fromString(Tag.PatientID, "18273645")
  val seq: Sequence = Sequence.fromElements(
    Tag.DerivationCodeSequence,
    List(
      Elements.empty().set(patientID1),
      Elements.empty().set(patientID2)
    )
  )

  val elements: Elements = create(studyDate, seq, patientName)

  "Elements" should "return an existing element" in {
    elements(Tag.PatientName) shouldBe Some(patientName)
    elements(TagPath.fromTag(Tag.PatientName)) shouldBe Some(patientName)
    elements(TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.PatientID)) shouldBe Some(patientID1)
    elements(TagPath.fromItem(Tag.DerivationCodeSequence, 2).thenTag(Tag.PatientID)) shouldBe Some(patientID2)
  }

  it should "support empty and contains tests" in {
    elements.isEmpty shouldBe false
    elements.nonEmpty shouldBe true
    elements.contains(Tag.StudyDate) shouldBe true
    elements.contains(TagPath.fromItem(Tag.DerivationCodeSequence, 1)) shouldBe true
    elements.contains(TagPath.fromItem(Tag.DerivationCodeSequence, 3)) shouldBe false
    elements.contains(TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.PatientID)) shouldBe true
    elements.contains(TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.PatientName)) shouldBe false
    elements.head shouldBe studyDate
  }

  it should "support sorting elements" in {
    val unsorted = create(patientName, studyDate)
    unsorted.head shouldBe patientName
    val sorted = unsorted.sorted()
    sorted.head shouldBe studyDate
  }

  it should "return None for missing element" in {
    elements(Tag.SeriesDate) shouldBe None
  }

  it should "return value elements only" in {
    elements.getValueElement(Tag.PatientName) shouldBe defined
    elements.getValueElement(TagPath.fromTag(Tag.PatientName)) shouldBe defined
    elements.getValueElement(Tag.SeriesDate) shouldBe empty
    elements.getValueElement(Tag.DerivationCodeSequence) shouldBe empty
  }

  it should "return the value of an element" in {
    val value = elements.getValue(Tag.PatientName)
    value shouldBe defined
    value.get.length should be > 0
    elements.getValue(Tag.SeriesDate) shouldBe empty
    elements.getValue(TagPath.fromTag(Tag.PatientName)) shouldBe defined
  }

  it should "return the bytes of an element" in {
    elements.getBytes(Tag.PatientName) shouldBe defined
    elements.getBytes(TagPath.fromTag(Tag.PatientName)) shouldBe defined
  }

  it should "return all strings in value with VM > 1" in {
    val elements = create(ValueElement.fromString(Tag.ImageType, """ORIGINAL\RECON TOMO"""), seq)
    val strings  = elements.getStrings(Tag.ImageType)
    strings should have length 2
    strings(1) shouldBe "RECON TOMO"
    elements.getStrings(TagPath.fromTag(Tag.ImageType)) should have length 2
    elements.getStrings(Tag.SeriesDate) should have length 0
    elements.getStrings(Tag.DerivationCodeSequence) should have length 0
  }

  it should "return a concatenated string in value with VM > 1" in {
    val elements = create(ValueElement.fromString(Tag.ImageType, """ORIGINAL\RECON TOMO"""))
    elements.getSingleString(Tag.ImageType) shouldBe Some("""ORIGINAL\RECON TOMO""")
    elements.getSingleString(TagPath.fromTag(Tag.ImageType)) shouldBe Some("""ORIGINAL\RECON TOMO""")
  }

  it should "return the first string of a value with VM > 1" in {
    val elements = create(ValueElement.fromString(Tag.ImageType, """ORIGINAL\RECON TOMO"""))
    elements.getString(Tag.ImageType) shouldBe Some("ORIGINAL")
    elements.getString(TagPath.fromTag(Tag.ImageType)) shouldBe Some("ORIGINAL")
  }

  it should "return shorts" in {
    val elements = create(ValueElement.fromString(Tag.ReferencedFrameNumber, """1\2\3"""))
    elements.getShorts(Tag.ReferencedFrameNumber) shouldBe Seq(1, 2, 3).map(_.toShort)
    elements.getShort(Tag.ReferencedFrameNumber) shouldBe Some(1.toShort)
    elements.getShorts(TagPath.fromTag(Tag.ReferencedFrameNumber)) shouldBe Seq(1, 2, 3).map(_.toShort)
    elements.getShort(TagPath.fromTag(Tag.ReferencedFrameNumber)) shouldBe Some(1.toShort)
  }

  it should "return ints" in {
    val elements = create(ValueElement.fromString(Tag.ReferencedFrameNumber, """1\2\3"""))
    elements.getInts(Tag.ReferencedFrameNumber) shouldBe Seq(1, 2, 3)
    elements.getInt(Tag.ReferencedFrameNumber) shouldBe Some(1)
    elements.getInts(TagPath.fromTag(Tag.ReferencedFrameNumber)) shouldBe Seq(1, 2, 3)
    elements.getInt(TagPath.fromTag(Tag.ReferencedFrameNumber)) shouldBe Some(1)
  }

  it should "return longs" in {
    val elements = create(ValueElement.fromString(Tag.ReferencedFrameNumber, """1\2\3"""))
    elements.getLongs(Tag.ReferencedFrameNumber) shouldBe Seq(1L, 2L, 3L)
    elements.getLong(Tag.ReferencedFrameNumber) shouldBe Some(1L)
    elements.getLongs(TagPath.fromTag(Tag.ReferencedFrameNumber)) shouldBe Seq(1L, 2L, 3L)
    elements.getLong(TagPath.fromTag(Tag.ReferencedFrameNumber)) shouldBe Some(1L)
  }

  it should "return very longs" in {
    val elements = create(
      new ValueElement(
        0x44550010,
        VR.UV,
        Value.fromVeryLong(VR.UV, BigInteger.valueOf(1)),
        bigEndian = false,
        explicitVR = true
      )
    )
    elements.getVeryLongs(0x44550010) shouldBe Seq(BigInteger.valueOf(1))
    elements.getVeryLong(0x44550010) shouldBe Some(BigInteger.valueOf(1))
    elements.getVeryLongs(TagPath.fromTag(0x44550010)) shouldBe Seq(BigInteger.valueOf(1))
    elements.getVeryLong(TagPath.fromTag(0x44550010)) shouldBe Some(BigInteger.valueOf(1))
  }

  it should "return floats" in {
    val elements = create(ValueElement.fromString(Tag.ReferencedFrameNumber, """1\2\3"""))
    elements.getFloats(Tag.ReferencedFrameNumber) shouldBe Seq(1f, 2f, 3f)
    elements.getFloat(Tag.ReferencedFrameNumber) shouldBe Some(1f)
    elements.getFloats(TagPath.fromTag(Tag.ReferencedFrameNumber)) shouldBe Seq(1f, 2f, 3f)
    elements.getFloat(TagPath.fromTag(Tag.ReferencedFrameNumber)) shouldBe Some(1f)
  }

  it should "return doubles" in {
    val elements = create(ValueElement.fromString(Tag.ReferencedFrameNumber, """1\2\3"""))
    elements.getDoubles(Tag.ReferencedFrameNumber) shouldBe Seq(1.0, 2.0, 3.0)
    elements.getDouble(Tag.ReferencedFrameNumber) shouldBe Some(1.0)
    elements.getDoubles(TagPath.fromTag(Tag.ReferencedFrameNumber)) shouldBe Seq(1.0, 2.0, 3.0)
    elements.getDouble(TagPath.fromTag(Tag.ReferencedFrameNumber)) shouldBe Some(1.0)
  }

  it should "return dates" in {
    val dates    = Seq(LocalDate.parse("2005-01-01"), LocalDate.parse("2010-01-01"))
    val elements = create(ValueElement(Tag.StudyDate, Value.fromDates(VR.DA, dates)))
    elements.getDates(Tag.StudyDate) shouldBe dates
    elements.getDate(Tag.StudyDate) shouldBe dates.headOption
    elements.getDates(TagPath.fromTag(Tag.StudyDate)) shouldBe dates
    elements.getDate(TagPath.fromTag(Tag.StudyDate)) shouldBe dates.headOption
  }

  it should "return times" in {
    val times    = Seq(LocalTime.parse("22:30:10"), LocalTime.parse("12:00:00"))
    val elements = create(ValueElement(Tag.AcquisitionTime, Value.fromTimes(VR.TM, times)))
    elements.getTimes(Tag.AcquisitionTime) shouldBe times
    elements.getTime(Tag.AcquisitionTime) shouldBe times.headOption
    elements.getTimes(TagPath.fromTag(Tag.AcquisitionTime)) shouldBe times
    elements.getTime(TagPath.fromTag(Tag.AcquisitionTime)) shouldBe times.headOption
  }

  it should "return date times" in {
    val dateTimes = Seq(LocalDate.parse("2005-01-01"), LocalDate.parse("2010-01-01"))
      .map(_.atStartOfDay(ZoneOffset.of("+04:00")))
    val elements = create(ValueElement(Tag.InstanceCoercionDateTime, Value.fromDateTimes(VR.DT, dateTimes)))
    elements.getDateTimes(Tag.InstanceCoercionDateTime) shouldBe dateTimes
    elements.getDateTime(Tag.InstanceCoercionDateTime) shouldBe dateTimes.headOption
    elements.getDateTimes(TagPath.fromTag(Tag.InstanceCoercionDateTime)) shouldBe dateTimes
    elements.getDateTime(TagPath.fromTag(Tag.InstanceCoercionDateTime)) shouldBe dateTimes.headOption
  }

  it should "return person names" in {
    val names       = Seq("Doe^John", "Doe^Jane")
    val personNames = names.flatMap(PersonName.parse)
    val elements    = create(ValueElement.fromString(Tag.PatientName, names.mkString("\\")))
    elements.getPersonNames(Tag.PatientName) shouldBe personNames
    elements.getPersonName(Tag.PatientName) shouldBe personNames.headOption
    elements.getPersonNames(TagPath.fromTag(Tag.PatientName)) shouldBe personNames
    elements.getPersonName(TagPath.fromTag(Tag.PatientName)) shouldBe personNames.headOption
  }

  it should "return sequences" in {
    val seq = elements.getSequence(Tag.DerivationCodeSequence)
    seq shouldBe defined
    seq.get.tag shouldBe Tag.DerivationCodeSequence
    elements.getSequence(Tag.PatientName) shouldBe empty
  }

  it should "return items" in {
    val item = elements.getItem(Tag.DerivationCodeSequence, 1)
    item shouldBe defined
    elements.getItem(Tag.DerivationCodeSequence, 0) shouldBe empty
    elements.getItem(Tag.DerivationCodeSequence, 2) shouldBe defined
    elements.getItem(Tag.DerivationCodeSequence, 3) shouldBe empty
    item shouldBe elements.getSequence(Tag.DerivationCodeSequence).get.item(1)
  }

  it should "return nested elements" in {
    elements.getNested(TagPath.fromItem(Tag.DerivationCodeSequence, 1)).get shouldBe create(patientID1)
    elements.getNested(TagPath.fromItem(Tag.DerivationCodeSequence, 2)).get shouldBe create(patientID2)
    elements.getNested(Tag.DerivationCodeSequence, 1) shouldBe elements.getNested(
      TagPath.fromItem(Tag.DerivationCodeSequence, 1)
    )
  }

  it should "return deeply nested elements" in {
    val elements = create(seq + Item(create(seq)))
    elements
      .getNested(
        TagPath
          .fromItem(Tag.DerivationCodeSequence, 3)
          .thenItem(Tag.DerivationCodeSequence, 1)
      )
      .get shouldBe create(patientID1)
  }

  it should "return fragments" in {
    val elements =
      create(studyDate, Fragments(Tag.PixelData, VR.OB, Some(Nil), List(Fragment(4, Value(bytesi(1, 2, 3, 4))))))
    elements.getFragments(Tag.PixelData) shouldBe defined
    elements.getFragments(Tag.SeriesDate) shouldBe empty
    elements.getFragments(Tag.StudyDate) shouldBe empty
  }

  it should "return elements based on tag condition" in {
    val elements2 = elements.set(patientID3)
    elements2
      .filter(_.tag == Tag.PatientID) shouldBe Elements(elements.characterSets, elements.zoneOffset, Vector(patientID3))
  }

  it should "remove element if present" in {
    val updatedSeq1 = seq.copy(items = seq.items.head :: seq.items(1).copy(elements = Elements.empty()) :: Nil)
    val updatedSeq2 = seq.copy(items = seq.items.head :: Nil)
    val deepSeq: Sequence = Sequence.fromElements(
      Tag.DerivationCodeSequence,
      List(Elements.empty().set(patientID1), Elements.empty().set(seq))
    )
    val deepElements = create(studyDate, deepSeq, patientName)
    val updatedDeepSeq = deepSeq.copy(items =
      deepSeq.items.head :: deepSeq.items(1).copy(elements = Elements.empty().set(updatedSeq2)) :: Nil
    )
    val updatedDeepElements = create(studyDate, updatedDeepSeq, patientName)

    elements.remove(Tag.DerivationCodeSequence) shouldBe elements.copy(data = Vector(studyDate, patientName))
    elements.remove(Tag.PatientName) shouldBe elements.copy(data = Vector(studyDate, seq))
    elements.remove(Tag.StudyDate) shouldBe elements.copy(data = Vector(seq, patientName))
    elements.remove(Tag.Modality) shouldBe elements
    elements.remove(EmptyTagPath) shouldBe elements
    elements.remove(TagPath.fromTag(Tag.StudyDate)) shouldBe elements.copy(data = Vector(seq, patientName))
    elements.remove(TagPath.fromItem(Tag.DerivationCodeSequence, 1)) shouldBe elements.copy(data =
      Vector(studyDate, seq.copy(items = seq.items.tail), patientName)
    )
    elements.remove(TagPath.fromItem(Tag.DerivationCodeSequence, 2).thenTag(Tag.PatientID)) shouldBe elements
      .copy(data = Vector(studyDate, updatedSeq1, patientName))
    elements.remove(TagPath.fromItem(Tag.DerivationCodeSequence, 3)) shouldBe elements
    elements.remove(TagPath.fromItem(Tag.DetectorInformationSequence, 1)) shouldBe elements
    deepElements.remove(
      TagPath.fromItem(Tag.DerivationCodeSequence, 2).thenItem(Tag.DerivationCodeSequence, 2)
    ) shouldBe updatedDeepElements
  }

  it should "set elements in the correct position" in {
    val characterSets = ValueElement.fromString(Tag.SpecificCharacterSet, "CS1 ")
    val modality      = ValueElement.fromString(Tag.Modality, "NM")
    elements.set(patientID3).data shouldBe Vector(studyDate, seq, patientName, patientID3)
    elements.set(characterSets).data shouldBe Vector(characterSets, studyDate, seq, patientName)
    elements.set(modality).data shouldBe Vector(studyDate, modality, seq, patientName)
  }

  it should "not create duplicate elements if inserted twice" in {
    val e = Elements
      .empty()
      .setString(Tag.PatientName, "John")
      .setString(Tag.PatientName, "John")
    e.size shouldBe 1
  }

  it should "set elements in sequences" in {
    val updated = elements.set(TagPath.fromItem(Tag.DerivationCodeSequence, 2), studyDate)
    updated(TagPath.fromItem(Tag.DerivationCodeSequence, 2).thenTag(Tag.StudyDate)).get shouldBe studyDate
  }

  it should "not add elements to sequences that does not exist" in {
    val updated = elements.set(TagPath.fromItem(Tag.DetectorInformationSequence, 1), studyDate)
    updated shouldBe elements
  }

  it should "replace items in sequences" in {
    val newElements = Elements.empty().set(studyDate)
    val updated     = elements.setNested(TagPath.fromItem(Tag.DerivationCodeSequence, 2), newElements)
    updated.getNested(Tag.DerivationCodeSequence, 2) shouldBe Some(newElements)
  }

  it should "not add items when trying to replace item at specified index" in {
    val newElements = Elements.empty().set(studyDate)
    val updated     = elements.setNested(TagPath.fromItem(Tag.DerivationCodeSequence, 3), newElements)
    updated shouldBe elements
    updated.getNested(Tag.DerivationCodeSequence, 3) shouldBe None
  }

  it should "not add new sequences" in {
    val newElements = Elements.empty().set(studyDate)
    val updated     = elements.setNested(TagPath.fromItem(Tag.DetectorInformationSequence, 1), newElements)
    updated shouldBe elements
    updated.getNested(Tag.DetectorInformationSequence, 1) shouldBe None
  }

  it should "add an item to a sequence" in {
    val newItem = Elements.empty().set(studyDate)
    val updated = elements.addItem(TagPath.fromSequence(Tag.DerivationCodeSequence), newItem)
    updated.getNested(Tag.DerivationCodeSequence, 3).get shouldBe newItem
  }

  it should "not add new sequence when adding item to a sequence that does not exist" in {
    val newItem = Elements.empty().set(studyDate)
    val updated = elements.addItem(TagPath.fromSequence(Tag.DetectorInformationSequence), newItem)
    updated shouldBe elements
  }

  it should "add a new sequence" in {
    val updated = elements.setSequence(
      TagPath.fromItem(Tag.DerivationCodeSequence, 1),
      Sequence(
        Tag.DetectorInformationSequence,
        indeterminateLength,
        List(Item(Elements.empty().set(studyDate)))
      )
    )
    updated(
      TagPath
        .fromItem(Tag.DerivationCodeSequence, 1)
        .thenItem(Tag.DetectorInformationSequence, 1)
        .thenTag(Tag.StudyDate)
    ).get shouldBe studyDate
  }

  it should "overwrite element if already present" in {
    val newPatientName = patientName.setValue(Value("Jane^Doe".utf8Bytes))
    val updated        = elements.set(newPatientName)

    updated.size shouldBe elements.size
    updated.getValueElement(Tag.PatientName).get.value.bytes.utf8String shouldBe "Jane^Doe"
  }

  it should "set value" in {
    val updated = elements.setValue(Tag.SeriesDate, Value.fromString(VR.DA, "20100101"))
    updated.getDate(Tag.SeriesDate).get shouldBe LocalDate.parse("2010-01-01")
  }

  it should "set nested value in root dataset" in {
    val tagPath = TagPath.fromTag(Tag.SeriesDate)
    val updated = elements.setNestedValue(tagPath, Value.fromString(VR.DA, "20100101"))
    updated.getDate(Tag.SeriesDate).get shouldBe LocalDate.parse("2010-01-01")
  }

  it should "set nested value in deep dataset" in {
    val tagPath = TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.SeriesDate)
    val updated = elements.setNestedValue(tagPath, Value.fromString(VR.DA, "20100101"))
    updated.getDate(tagPath).get shouldBe LocalDate.parse("2010-01-01")
  }

  it should "set bytes" in {
    val updated = elements.setBytes(Tag.SeriesDate, "20100101".utf8Bytes)
    updated.getDate(Tag.SeriesDate).get shouldBe LocalDate.parse("2010-01-01")
  }

  it should "set nested bytes" in {
    val tagPath = TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.SeriesDate)
    val updated = elements.setNestedBytes(tagPath, "20100101".utf8Bytes)
    updated.getDate(tagPath).get shouldBe LocalDate.parse("2010-01-01")
  }

  it should "set strings" in {
    val names = Seq("Smith^Dr", "Jones^Dr")
    elements
      .setStrings(Tag.ReferringPhysicianName, names)
      .getStrings(Tag.ReferringPhysicianName) shouldBe names
    elements
      .setString(Tag.ReferringPhysicianName, names.head)
      .getStrings(Tag.ReferringPhysicianName) shouldBe Seq(names.head)
  }

  it should "set nested strings" in {
    val tagPath = TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.ReferringPhysicianName)
    val names   = Seq("Smith^Dr", "Jones^Dr")
    elements
      .setNestedStrings(tagPath, names)
      .getStrings(tagPath) shouldBe names
    elements
      .setNestedString(tagPath, names.head)
      .getStrings(tagPath) shouldBe Seq(names.head)
  }

  it should "set shorts" in {
    elements
      .setShorts(Tag.ReferencedFrameNumber, Seq(1, 2, 3))
      .getShorts(Tag.ReferencedFrameNumber) shouldBe Seq(1, 2, 3)
    elements
      .setShort(Tag.ReferencedFrameNumber, 42)
      .getShorts(Tag.ReferencedFrameNumber) shouldBe Seq(42)
  }

  it should "set nested shorts" in {
    val tagPath = TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.ReferencedFrameNumber)
    elements
      .setNestedShorts(tagPath, Seq(1, 2, 3))
      .getShorts(tagPath) shouldBe Seq(1, 2, 3)
    elements
      .setNestedShort(tagPath, 42)
      .getShorts(tagPath) shouldBe Seq(42)
  }

  it should "set ints" in {
    elements
      .setInts(Tag.ReferencePixelX0, Seq(1, 2, 3))
      .getInts(Tag.ReferencePixelX0) shouldBe Seq(1, 2, 3)
    elements
      .setInt(Tag.ReferencePixelX0, 42)
      .getInts(Tag.ReferencePixelX0) shouldBe Seq(42)
  }

  it should "set nested ints" in {
    val tagPath = TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.ReferencePixelX0)
    elements
      .setNestedInts(tagPath, Seq(1, 2, 3))
      .getInts(tagPath) shouldBe Seq(1, 2, 3)
    elements
      .setNestedInt(tagPath, 42)
      .getInts(tagPath) shouldBe Seq(42)
  }

  it should "set longs" in {
    elements
      .setLongs(Tag.SimpleFrameList, Seq(1, 2, 3))
      .getLongs(Tag.SimpleFrameList) shouldBe Seq(1, 2, 3)
    elements
      .setLong(Tag.SimpleFrameList, 42)
      .getLongs(Tag.SimpleFrameList) shouldBe Seq(42)
  }

  it should "set nested longs" in {
    val tagPath = TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.SimpleFrameList)
    elements
      .setNestedLongs(tagPath, Seq(1, 2, 3))
      .getLongs(tagPath) shouldBe Seq(1, 2, 3)
    elements
      .setNestedLong(tagPath, 42)
      .getLongs(tagPath) shouldBe Seq(42)
  }

  it should "set very longs" in {
    elements
      .setVeryLongs(
        0x44550010,
        VR.UV,
        Seq(BigInteger.valueOf(1), BigInteger.valueOf(2)),
        bigEndian = false,
        explicitVR = true
      )
      .getVeryLongs(0x44550010) shouldBe Seq(BigInteger.valueOf(1), BigInteger.valueOf(2))
    elements
      .setVeryLong(0x44550010, VR.UV, BigInteger.valueOf(1), bigEndian = false, explicitVR = true)
      .getVeryLongs(0x44550010) shouldBe Seq(BigInteger.valueOf(1))
  }

  it should "set nested very longs" in {
    val tagPath = TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(0x44550010)
    elements
      .setNestedVeryLongs(
        tagPath,
        VR.UV,
        Seq(BigInteger.valueOf(1), BigInteger.valueOf(2)),
        bigEndian = false,
        explicitVR = true
      )
      .getVeryLongs(tagPath) shouldBe Seq(BigInteger.valueOf(1), BigInteger.valueOf(2))
    elements
      .setNestedVeryLong(tagPath, VR.UV, BigInteger.valueOf(1), bigEndian = false, explicitVR = true)
      .getVeryLongs(tagPath) shouldBe Seq(BigInteger.valueOf(1))
  }

  it should "set floats" in {
    elements
      .setFloats(Tag.RecommendedDisplayFrameRateInFloat, Seq(1f, 2f, 3f))
      .getFloats(Tag.RecommendedDisplayFrameRateInFloat) shouldBe Seq(1f, 2f, 3f)
    elements
      .setFloat(Tag.RecommendedDisplayFrameRateInFloat, 42f)
      .getFloats(Tag.RecommendedDisplayFrameRateInFloat) shouldBe Seq(42f)
  }

  it should "set nested floats" in {
    val tagPath = TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.RecommendedDisplayFrameRateInFloat)
    elements
      .setNestedFloats(tagPath, Seq(1f, 2f, 3f))
      .getFloats(tagPath) shouldBe Seq(1f, 2f, 3f)
    elements
      .setNestedFloat(tagPath, 42f)
      .getFloats(tagPath) shouldBe Seq(42f)
  }

  it should "set doubles" in {
    elements
      .setDoubles(Tag.TimeRange, Seq(1.0, 2.0, 3.0))
      .getDoubles(Tag.TimeRange) shouldBe Seq(1.0, 2.0, 3.0)
    elements
      .setDouble(Tag.TimeRange, 42.0)
      .getDoubles(Tag.TimeRange) shouldBe Seq(42.0)
  }

  it should "set nested doubles" in {
    val tagPath = TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.TimeRange)
    elements
      .setNestedDoubles(tagPath, Seq(1.0, 2.0, 3.0))
      .getDoubles(tagPath) shouldBe Seq(1.0, 2.0, 3.0)
    elements
      .setNestedDouble(tagPath, 42.0)
      .getDoubles(tagPath) shouldBe Seq(42.0)
  }

  it should "set dates" in {
    val dates = Seq(LocalDate.parse("2005-01-01"), LocalDate.parse("2010-01-01"))
    elements
      .setDates(Tag.StudyDate, dates)
      .getDates(Tag.StudyDate) shouldBe dates
    elements
      .setDate(Tag.StudyDate, dates.head)
      .getDates(Tag.StudyDate) shouldBe Seq(dates.head)
  }

  it should "set nested dates" in {
    val tagPath = TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.StudyDate)
    val dates   = Seq(LocalDate.parse("2005-01-01"), LocalDate.parse("2010-01-01"))
    elements
      .setNestedDates(tagPath, dates)
      .getDates(tagPath) shouldBe dates
    elements
      .setNestedDate(tagPath, dates.head)
      .getDates(tagPath) shouldBe Seq(dates.head)
  }

  it should "set times" in {
    val times = Seq(LocalTime.parse("23:30:10"), LocalTime.parse("12:00:00"))
    elements
      .setTimes(Tag.AcquisitionTime, times)
      .getTimes(Tag.AcquisitionTime) shouldBe times
    elements
      .setTime(Tag.AcquisitionTime, times.head)
      .getTimes(Tag.AcquisitionTime) shouldBe Seq(times.head)
  }

  it should "set nested times" in {
    val tagPath = TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.AcquisitionTime)
    val times   = Seq(LocalTime.parse("23:30:10"), LocalTime.parse("12:00:00"))
    elements
      .setNestedTimes(tagPath, times)
      .getTimes(tagPath) shouldBe times
    elements
      .setNestedTime(tagPath, times.head)
      .getTimes(tagPath) shouldBe Seq(times.head)
  }

  it should "set date times" in {
    val dateTimes = Seq(LocalDate.parse("2005-01-01"), LocalDate.parse("2010-01-01"))
      .map(_.atStartOfDay(ZoneOffset.of("+04:00")))
    elements
      .setDateTimes(Tag.InstanceCoercionDateTime, dateTimes)
      .getDateTimes(Tag.InstanceCoercionDateTime) shouldBe dateTimes
    elements
      .setDateTime(Tag.InstanceCoercionDateTime, dateTimes.head)
      .getDateTimes(Tag.InstanceCoercionDateTime) shouldBe Seq(dateTimes.head)
  }

  it should "set nested date times" in {
    val tagPath = TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.InstanceCoercionDateTime)
    val dateTimes = Seq(LocalDate.parse("2005-01-01"), LocalDate.parse("2010-01-01"))
      .map(_.atStartOfDay(ZoneOffset.of("+04:00")))
    elements
      .setNestedDateTimes(tagPath, dateTimes)
      .getDateTimes(tagPath) shouldBe dateTimes
    elements
      .setNestedDateTime(tagPath, dateTimes.head)
      .getDateTimes(tagPath) shouldBe Seq(dateTimes.head)
  }

  it should "set person names" in {
    val names        = Seq("Doe^John", "Doe^Jane")
    val patientNames = names.flatMap(PersonName.parse)
    elements
      .setPersonNames(Tag.PatientName, patientNames)
      .getPersonNames(Tag.PatientName) shouldBe patientNames
    elements
      .setPersonName(Tag.PatientName, patientNames.head)
      .getPersonNames(Tag.PatientName) shouldBe Seq(patientNames.head)
  }

  it should "set nested person names" in {
    val tagPath      = TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.PatientName)
    val names        = Seq("Doe^John", "Doe^Jane")
    val patientNames = names.flatMap(PersonName.parse)
    elements
      .setNestedPersonNames(tagPath, patientNames)
      .getPersonNames(tagPath) shouldBe patientNames
    elements
      .setNestedPersonName(tagPath, patientNames.head)
      .getPersonNames(tagPath) shouldBe Seq(patientNames.head)
  }

  it should "set URI" in {
    val uri = new URI("https://example.com:8080/path?q1=45")
    elements.setURI(Tag.StorageURL, uri).getURI(Tag.StorageURL) shouldBe Some(uri)
  }

  it should "set nested URI" in {
    val tagPath = TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.StorageURL)
    val uri     = new URI("https://example.com:8080/path?q1=45")
    elements.setNestedURI(tagPath, uri).getURI(tagPath) shouldBe Some(uri)
  }

  it should "update character sets" in {
    val updatedCs1 = elements.setCharacterSets(CharacterSets("\\ISO 2022 IR 127".utf8Bytes)).characterSets
    updatedCs1.charsetNames shouldBe Seq("", "ISO 2022 IR 127")
    val updatedCs2 = elements.set(ValueElement.fromString(Tag.SpecificCharacterSet, "\\ISO 2022 IR 13")).characterSets
    updatedCs2.charsetNames shouldBe Seq("", "ISO 2022 IR 13")
  }

  it should "update zone offset" in {
    val updatedZo1 = elements.setZoneOffset(ZoneOffset.of("-06:00")).zoneOffset
    updatedZo1.toString shouldBe "-06:00"
    val updatedZo2 = elements.set(ValueElement.fromString(Tag.TimezoneOffsetFromUTC, "+04:00")).zoneOffset
    updatedZo2.toString shouldBe "+04:00"
    val updatedZo3 =
      elements.set(ValueElement.fromString(Tag.TimezoneOffsetFromUTC, "bad zone offset string")).zoneOffset
    updatedZo3 shouldBe elements.zoneOffset
  }

  it should "set sequence" in {
    val e1 = Elements.empty().setString(Tag.PatientName, "Last1^First1")
    val e2 = Elements.empty().setString(Tag.PatientName, "Last2^First2")
    val i1 = Item.fromElements(e1)
    val i2 = Item.fromElements(e2)
    val s  = Sequence(Tag.DerivationCodeSequence, indeterminateLength, List(i1, i2))

    s shouldBe Sequence.fromItems(Tag.DerivationCodeSequence, List(i1, i2))
    s shouldBe Sequence.fromElements(Tag.DerivationCodeSequence, List(e1, e2))
    s.items.length shouldBe 2
    s.items(1) shouldBe i2

    val updated = elements.setSequence(s)
    updated shouldBe elements.set(s)
    updated.contains(Tag.DerivationCodeSequence) shouldBe true
    updated.getSequence(Tag.DerivationCodeSequence) shouldBe defined
    updated.getSequence(Tag.DerivationCodeSequence).get shouldBe s
  }

  it should "aggregate the bytes of all its elements" in {
    val bytes = preamble ++
      element(Tag.StudyDate, "20041230") ++
      sequence(Tag.DerivationCodeSequence) ++
      item() ++ element(Tag.PatientID, "12345678") ++ itemDelimitation() ++
      item() ++ element(Tag.PatientID, "87654321") ++ itemDelimitation() ++
      sequenceDelimitation() ++
      element(Tag.PatientName, "John^Doe")

    elements.toBytes() shouldBe bytes
  }

  it should "return an empty byte string when aggregating bytes with no data" in {
    Elements.empty().toBytes(withPreamble = false) shouldBe emptyBytes
  }

  it should "render an informative string representation" in {
    val s = elements.toString
    (s.length - s.replace(System.lineSeparator, "").length) / System.lineSeparator.length shouldBe 9
  }

  it should "return the specified element based on tag path" in {
    elements(TagPath.fromItem(Tag.DerivationCodeSequence, 2).thenTag(Tag.PatientID)) shouldBe Some(patientID2)
    elements(TagPath.fromItem(Tag.DerivationCodeSequence, 3).thenTag(Tag.PatientID)) shouldBe None
    elements(TagPath.fromItem(Tag.DerivationCodeSequence, 3).thenTag(Tag.PatientID)) shouldBe None
    elements(TagPath.fromItem(Tag.DerivationCodeSequence, 2).thenTag(Tag.PatientName)) shouldBe None
    elements(TagPath.fromItem(Tag.AbstractPriorCodeSequence, 1).thenTag(Tag.PatientID)) shouldBe None
  }

  it should "return the specified sequence based on tag path" in {
    elements.getNested(TagPath.fromItem(Tag.DerivationCodeSequence, 1)) shouldBe seq.item(1).map(_.elements)
  }

  it should "update element specified by tag path" in {
    elements(TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.PatientID)) shouldBe Some(patientID1)
    val e2 = elements.set(TagPath.fromItem(Tag.DerivationCodeSequence, 1), patientID2)
    e2(TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.PatientID)) shouldBe Some(patientID2)
  }

  it should "provide a legible toString" in {
    val updated = elements.set(Fragments(Tag.PixelData, VR.OB, None, List(Fragment(4, Value(bytesi(1, 2, 3, 4))))))
    updated.toString.count(_ == System.lineSeparator.charAt(0)) shouldBe updated.toElements(false).length - 1
  }

  it should "create file meta information" in {
    val fmiList = Elements.fileMetaInformationElements("iuid", "cuid", "ts")
    val fmi     = Elements.empty().set(fmiList)
    fmi.getInt(Tag.FileMetaInformationGroupLength).get shouldBe
      (12 + 5 * 8 + 2 + 4 + 4 + 2 +
        padToEvenLength(Implementation.classUid.utf8Bytes, Tag.ImplementationClassUID).length +
        padToEvenLength(Implementation.versionName.utf8Bytes, Tag.ImplementationVersionName).length)
    fmi.getBytes(Tag.FileMetaInformationVersion).get shouldBe bytesi(0, 1)
    fmi.getString(Tag.MediaStorageSOPClassUID).get shouldBe "cuid"
    fmi.getString(Tag.MediaStorageSOPInstanceUID).get shouldBe "iuid"
    fmi.getString(Tag.TransferSyntaxUID).get shouldBe "ts"
    fmi.getString(Tag.ImplementationClassUID).get shouldBe Implementation.classUid
    fmi.getString(Tag.ImplementationVersionName).get shouldBe Implementation.versionName
  }

  "Elements data classes" should "return the correct byte representation" in {
    PreambleElement.toBytes should have length (128 + 4)
    PreambleElement.toBytes.takeRight(4) shouldBe magicBytes
    ValueElement(Tag.StudyDate, Value.fromString(VR.DA, "20010101")).toBytes shouldBe HeaderPart(
      Tag.StudyDate,
      VR.DA,
      8
    ).bytes ++ "20010101".utf8Bytes
    SequenceElement(Tag.DerivationCodeSequence, 10).toBytes shouldBe sequence(Tag.DerivationCodeSequence, 10)
    FragmentsElement(Tag.PixelData, VR.OW).toBytes shouldBe pixeDataFragments()
    FragmentElement(4, Value(bytesi(1, 2, 3, 4))).toBytes shouldBe item(4) ++ bytesi(1, 2, 3, 4)
    ItemElement(10).toBytes shouldBe item(10)
    ItemDelimitationElement().toBytes shouldBe itemDelimitation()
    SequenceDelimitationElement().toBytes shouldBe sequenceDelimitation()
    Sequence(Tag.DerivationCodeSequence, indeterminateLength, List(Item(Elements.empty()))).toBytes shouldBe sequence(
      Tag.DerivationCodeSequence
    ) ++ item(indeterminateLength) ++ itemDelimitation() ++ sequenceDelimitation()
    Fragments(
      Tag.PixelData,
      VR.OW,
      Some(Nil),
      List(Fragment(4, Value(bytesi(1, 2, 3, 4))))
    ).toBytes shouldBe pixeDataFragments() ++ item(0) ++ item(4) ++ bytesi(1, 2, 3, 4) ++ sequenceDelimitation()
  }

  it should "have expected string representations in terms of number of lines" in {
    def checkString(string: String, nLines: Int): Assertion = string.count(_ == '\n') shouldBe nLines - 1

    checkString(PreambleElement.toString, 1)
    checkString(ValueElement(Tag.StudyDate, Value.fromString(VR.DA, "20010101")).toString, 1)
    checkString(SequenceElement(Tag.DerivationCodeSequence, 10).toString, 1)
    checkString(FragmentsElement(Tag.PixelData, VR.OW).toString, 1)
    checkString(FragmentElement(4, Value(bytesi(1, 2, 3, 4))).toString, 1)
    checkString(ItemElement(10).toString, 1)
    checkString(ItemDelimitationElement.toString, 1)
    checkString(SequenceDelimitationElement().toString, 1)
    checkString(Sequence(Tag.DerivationCodeSequence, indeterminateLength, List(Item(Elements.empty()))).toString, 1)
    checkString(
      Fragments(Tag.PixelData, VR.OW, Some(Nil), List(Fragment(4, Value(bytesi(1, 2, 3, 4))))).toString,
      1
    )
  }

}
