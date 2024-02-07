package com.exini.dicom.streams

import com.exini.dicom.data.DicomElements._
import com.exini.dicom.data.TestData._
import com.exini.dicom.data._
import com.exini.dicom.streams.ElementFlows.elementFlow
import com.exini.dicom.streams.ElementSink.elementSink
import com.exini.dicom.streams.ParseFlow.parseFlow
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{ FileIO, Source }
import org.apache.pekko.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import java.io.File
import java.nio.file.Files
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ Await, ExecutionContextExecutor }

class ElementSinkTest
    extends TestKit(ActorSystem("ElementSinkSpec"))
    with AnyFlatSpecLike
    with Matchers
    with BeforeAndAfterAll {

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  override def afterAll(): Unit = {
    Await.ready(system.terminate(), 10.seconds)
    ()
  }

  "An element sink" should "aggregate streamed elements into an Elements" in {
    val elementList = List(
      ValueElement.fromString(Tag.TransferSyntaxUID, UID.ExplicitVRLittleEndian),
      ValueElement.fromString(Tag.StudyDate, "20040329"),
      SequenceElement(Tag.DerivationCodeSequence, indeterminateLength),
      ItemElement(indeterminateLength),
      ValueElement.fromString(Tag.StudyDate, "20040329"),
      ItemDelimitationElement(),
      ItemElement(indeterminateLength),
      SequenceElement(Tag.DerivationCodeSequence, indeterminateLength),
      ItemElement(indeterminateLength),
      ValueElement.fromString(Tag.StudyDate, "20040329"),
      ItemDelimitationElement(),
      SequenceDelimitationElement(),
      ItemDelimitationElement(),
      SequenceDelimitationElement(),
      ValueElement.fromString(Tag.PatientName, "Doe^John"),
      FragmentsElement(Tag.PixelData, VR.OB),
      FragmentElement(4, Value(bytesi(1, 2, 3, 4))),
      FragmentElement(4, Value(bytesi(1, 2, 3, 4))),
      SequenceDelimitationElement()
    )

    val elements = Await.result(Source(elementList).runWith(elementSink), 5.seconds)

    elements.toElements(false) shouldBe elementList
  }

  it should "handle zero length values, fragments, sequences and items" in {
    val elementList = List(
      ValueElement.fromString(Tag.StudyDate, ""),
      SequenceElement(Tag.DerivationCodeSequence, indeterminateLength),
      SequenceDelimitationElement(),
      SequenceElement(Tag.DerivationCodeSequence, 0),
      SequenceElement(Tag.DerivationCodeSequence, indeterminateLength),
      ItemElement(indeterminateLength),
      ItemDelimitationElement(),
      ItemElement(0),
      SequenceDelimitationElement(),
      SequenceElement(Tag.DerivationCodeSequence, 16 + 16 + 8),
      ItemElement(16 + 16),
      ValueElement.fromString(Tag.StudyDate, "20200202"),
      ValueElement.fromString(Tag.PatientName, "John^Doe"),
      FragmentsElement(Tag.PixelData, VR.OB),
      FragmentElement(0, Value.empty),
      SequenceDelimitationElement(),
      FragmentsElement(Tag.PixelData, VR.OB),
      SequenceDelimitationElement()
    )

    val elements = Await.result(Source(elementList).runWith(elementSink), 5.seconds)

    elements.toElements(false) shouldBe elementList
  }

  it should "handle determinate length items and sequences" in {
    val elementList = List(
      SequenceElement(Tag.DerivationCodeSequence, 68),
      ItemElement(16),
      ValueElement.fromString(Tag.StudyDate, "20040329"),
      ItemElement(36),
      SequenceElement(Tag.DerivationCodeSequence, 24),
      ItemElement(16),
      ValueElement.fromString(Tag.StudyDate, "20040329")
    )

    val elements = Await.result(Source(elementList).runWith(elementSink), 5.seconds)

    elements.toElements(false) shouldBe elementList
  }

  it should "handle item and sequence delimitations in when items and sequences are of determinate length" in {
    val elementList = List(
      SequenceElement(Tag.DerivationCodeSequence, 108),
      ItemElement(24),
      ValueElement.fromString(Tag.StudyDate, "20040329"),
      ItemDelimitationElement(),
      ItemElement(60),
      SequenceElement(Tag.DerivationCodeSequence, 40),
      ItemElement(24),
      ValueElement.fromString(Tag.StudyDate, "20040329"),
      ItemDelimitationElement(),
      SequenceDelimitationElement(),
      ItemDelimitationElement(),
      SequenceDelimitationElement()
    )

    val expectedElementList = List(
      SequenceElement(Tag.DerivationCodeSequence, 68),
      ItemElement(16),
      ValueElement.fromString(Tag.StudyDate, "20040329"),
      ItemElement(36),
      SequenceElement(Tag.DerivationCodeSequence, 24),
      ItemElement(16),
      ValueElement.fromString(Tag.StudyDate, "20040329")
    )

    val elements = Await.result(Source(elementList).runWith(elementSink), 5.seconds)

    elements.toElements(false) shouldBe expectedElementList
  }

  it should "handle implicit VR encoding" in {
    val bytes = preamble ++ fmiGroupLength(transferSyntaxUID(UID.ImplicitVRLittleEndian)) ++
      transferSyntaxUID(UID.ImplicitVRLittleEndian) ++ personNameJohnDoe(explicitVR = false) ++
      sequence(Tag.DerivationCodeSequence, explicitVR = false) ++ item() ++ personNameJohnDoe(explicitVR = false) ++
      studyDate(explicitVR = false) ++ itemDelimitation() ++ item() ++
      sequence(Tag.DerivationCodeSequence, 24, bigEndian = false, explicitVR = false) ++ item(16) ++
      personNameJohnDoe(explicitVR = false) ++ itemDelimitation() ++ sequenceDelimitation()

    val source = Source
      .single(bytes.toByteString)
      .via(ParseFlow())
      .via(elementFlow)

    val elements = Await.result(source.runWith(elementSink), 5.seconds)

    elements.toBytes() shouldBe bytes
  }

  "Fragments" should "be empty" in {
    val bytes = pixeDataFragments() ++ sequenceDelimitation()

    val fragments = toElementsBlocking(Source.single(bytes.toByteString)).getFragments(Tag.PixelData).get
    fragments.size shouldBe 0
    fragments.offsets shouldBe empty
  }

  it should "convert an empty offsets table item to an empty list of offsets" in {
    val elementList = List(
      FragmentsElement(Tag.PixelData, VR.OB),
      FragmentElement(0, Value.empty),
      FragmentElement(0, Value(bytesi(1, 2, 3, 4))),
      SequenceDelimitationElement()
    )

    val elements  = Await.result(Source(elementList).runWith(elementSink), 5.seconds)
    val fragments = elements.getFragments(Tag.PixelData).get

    fragments.offsets shouldBe defined
    fragments.offsets.get shouldBe empty
  }

  it should "map an offsets table to a list of offsets" in {
    val elementList = List(
      FragmentsElement(Tag.PixelData, VR.OB),
      FragmentElement(0, Value(intToBytesLE(1) ++ intToBytesLE(2) ++ intToBytesLE(3) ++ intToBytesLE(4))),
      SequenceDelimitationElement()
    )

    val elements  = Await.result(Source(elementList).runWith(elementSink), 5.seconds)
    val fragments = elements.getFragments(Tag.PixelData).get

    fragments.offsets shouldBe defined
    fragments.offsets.get shouldBe List(1, 2, 3, 4)
  }

  it should "convert an empty first item to an empty offsets list" in {
    val bytes = pixeDataFragments() ++ item(0) ++ item(4) ++ bytesi(1, 2, 3, 4) ++ sequenceDelimitation()

    val fragments = toElementsBlocking(Source.single(bytes.toByteString)).getFragments(Tag.PixelData).get
    fragments.offsets shouldBe defined
    fragments.offsets.get shouldBe empty
    fragments.size shouldBe 1
  }

  it should "convert first item to offsets" in {
    val bytes = pixeDataFragments() ++ item(8) ++ intToBytesLE(0) ++ intToBytesLE(456) ++ item(4) ++
      bytesi(1, 2, 3, 4) ++ sequenceDelimitation()

    val fragments = toElementsBlocking(Source.single(bytes.toByteString)).getFragments(Tag.PixelData).get
    fragments.offsets shouldBe defined
    fragments.offsets.get shouldBe List(0, 456)
  }

  it should "support access to frames based on fragments and offsets" in {
    val bytes = pixeDataFragments() ++ item(8) ++ intToBytesLE(0) ++ intToBytesLE(6) ++ item(4) ++
      bytesi(1, 2, 3, 4) ++ item(4) ++ bytesi(5, 6, 7, 8) ++ sequenceDelimitation()

    val iter = toElementsBlocking(Source.single(bytes.toByteString)).getFragments(Tag.PixelData).get.frameIterator
    iter.hasNext shouldBe true
    iter.next() shouldBe bytesi(1, 2, 3, 4, 5, 6)
    iter.hasNext shouldBe true
    iter.next() shouldBe bytesi(7, 8)
    iter.hasNext shouldBe false
  }

  it should "return an empty iterator when offsets list and/or fragments are empty" in {
    val bytes1 = pixeDataFragments() ++ sequenceDelimitation()
    val bytes2 = pixeDataFragments() ++ item(0) ++ sequenceDelimitation()
    val bytes3 = pixeDataFragments() ++ item(0) ++ item(0) ++ sequenceDelimitation()
    val bytes4 = pixeDataFragments() ++ item(4) ++ intToBytesLE(0) ++ sequenceDelimitation()
    val bytes5 = pixeDataFragments() ++ item(4) ++ intToBytesLE(0) ++ item(0) ++ sequenceDelimitation()

    val iter1 = toElementsBlocking(Source.single(bytes1.toByteString)).getFragments(Tag.PixelData).get.frameIterator
    val iter2 = toElementsBlocking(Source.single(bytes2.toByteString)).getFragments(Tag.PixelData).get.frameIterator
    val iter3 = toElementsBlocking(Source.single(bytes3.toByteString)).getFragments(Tag.PixelData).get.frameIterator
    val iter4 = toElementsBlocking(Source.single(bytes4.toByteString)).getFragments(Tag.PixelData).get.frameIterator
    val iter5 = toElementsBlocking(Source.single(bytes5.toByteString)).getFragments(Tag.PixelData).get.frameIterator

    iter1.hasNext shouldBe false
    iter2.hasNext shouldBe false
    iter3.hasNext shouldBe false
    iter4.hasNext shouldBe false
    iter5.hasNext shouldBe false
  }

  it should "support many frames per fragment and many fragments per frame" in {
    val bytes1 = pixeDataFragments() ++ item(12) ++ List(0, 2, 3).map(intToBytesLE).reduce(_ ++ _) ++ item(4) ++
      bytesi(1, 2, 3, 4) ++ sequenceDelimitation()
    val bytes2 = pixeDataFragments() ++ item(0) ++ item(2) ++ bytesi(1, 2) ++
      item(2) ++ bytesi(1, 2) ++ item(2) ++ bytesi(1, 2) ++ item(2) ++
      bytesi(1, 2) ++ sequenceDelimitation()

    val iter1 = toElementsBlocking(Source.single(bytes1.toByteString)).getFragments(Tag.PixelData).get.frameIterator
    val iter2 = toElementsBlocking(Source.single(bytes2.toByteString)).getFragments(Tag.PixelData).get.frameIterator

    iter1.next() shouldBe bytesi(1, 2)
    iter1.next() shouldBe bytesi(3)
    iter1.next() shouldBe bytesi(4)
    iter1.hasNext shouldBe false

    iter2.next() shouldBe bytesi(1, 2, 1, 2, 1, 2, 1, 2)
    iter2.hasNext shouldBe false
  }

  it should "create a identical set of elements as the non-streaming parser" in {
    val file = new File(getClass.getResource("../data/test001.dcm").toURI)
    val elements1 = FileIO
      .fromPath(file.toPath)
      .via(parseFlow)
      .via(elementFlow)
      .runWith(elementSink)
      .futureValue(Timeout(5.seconds))

    val elements2 = new Parser().parse(Files.readAllBytes(file.toPath).wrap).result()

    elements1 shouldBe elements2
  }
}
