package com.exini.dicom.streams

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.testkit.TestKit
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import com.exini.dicom.data.Elements.{SequenceDelimitationElement, SequenceElement, _}
import com.exini.dicom.data.TestData.pixeDataFragments
import com.exini.dicom.data.{Tag, UID, _}
import com.exini.dicom.streams.ElementSink.elementSink

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor}

class ElementSinkTest extends TestKit(ActorSystem("ElementSinkSpec")) with FlatSpecLike with Matchers with BeforeAndAfterAll {

  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  override def afterAll(): Unit = system.terminate()

  "An element sink" should "aggregate streamed elements into an Elements" in {
    val elementList = List(
      ValueElement.fromString(Tag.TransferSyntaxUID, UID.ExplicitVRLittleEndian),
      ValueElement.fromString(Tag.StudyDate, "20040329"),
      SequenceElement(Tag.DerivationCodeSequence, indeterminateLength),
      ItemElement(1, indeterminateLength),
      ValueElement.fromString(Tag.StudyDate, "20040329"),
      ItemDelimitationElement(1),
      ItemElement(2, indeterminateLength),
      SequenceElement(Tag.DerivationCodeSequence, indeterminateLength),
      ItemElement(1, indeterminateLength),
      ValueElement.fromString(Tag.StudyDate, "20040329"),
      ItemDelimitationElement(1),
      SequenceDelimitationElement(),
      ItemDelimitationElement(2),
      SequenceDelimitationElement(),
      ValueElement.fromString(Tag.PatientName, "Doe^John"),
      FragmentsElement(Tag.PixelData, VR.OB),
      FragmentElement(1, 4, Value(ByteString(1, 2, 3, 4))),
      FragmentElement(2, 4, Value(ByteString(1, 2, 3, 4))),
      SequenceDelimitationElement())

    val elements = Await.result(Source(elementList).runWith(elementSink), 5.seconds)

    elements.toElements shouldBe elementList
  }

  it should "handle zero length values, fragments, sequences and items" in {
    val elementList = List(
      ValueElement.fromString(Tag.StudyDate, ""),
      SequenceElement(Tag.DerivationCodeSequence, indeterminateLength),
      SequenceDelimitationElement(),
      SequenceElement(Tag.DerivationCodeSequence, 0),
      SequenceDelimitationElement(marker = true),
      SequenceElement(Tag.DerivationCodeSequence, indeterminateLength),
      ItemElement(1, indeterminateLength),
      ItemDelimitationElement(1),
      ItemElement(2, 0),
      ItemDelimitationElement(2, marker = true),
      SequenceDelimitationElement(),
      FragmentsElement(Tag.PixelData, VR.OB),
      FragmentElement(1, 0, Value.empty),
      SequenceDelimitationElement(),
      FragmentsElement(Tag.PixelData, VR.OB),
      SequenceDelimitationElement())

    val elements = Await.result(Source(elementList).runWith(elementSink), 5.seconds)

    elements.toElements shouldBe elementList
  }

  it should "convert an empty offsets table item to an empty list of offsets" in {
    val elementList = List(
      FragmentsElement(Tag.PixelData, VR.OB),
      FragmentElement(1, 0, Value.empty),
      FragmentElement(2, 0, Value(ByteString(1, 2, 3, 4l))),
      SequenceDelimitationElement())

    val elements = Await.result(Source(elementList).runWith(elementSink), 5.seconds)
    val fragments = elements.getFragments(Tag.PixelData).get

    fragments.offsets shouldBe defined
    fragments.offsets.get shouldBe empty
  }

  it should "map an offsets table to a list of offsets" in {
    val elementList = List(
      FragmentsElement(Tag.PixelData, VR.OB),
      FragmentElement(1, 0, Value(intToBytesLE(1) ++ intToBytesLE(2) ++ intToBytesLE(3) ++ intToBytesLE(4))),
      SequenceDelimitationElement())

    val elements = Await.result(Source(elementList).runWith(elementSink), 5.seconds)
    val fragments = elements.getFragments(Tag.PixelData).get

    fragments.offsets shouldBe defined
    fragments.offsets.get shouldBe List(1, 2, 3, 4)
  }

  "Fragments" should "be empty" in {
    val bytes = pixeDataFragments() ++ sequenceDelimitation()

    val fragments = toElementsBlocking(Source.single(bytes)).getFragments(Tag.PixelData).get
    fragments.size shouldBe 0
    fragments.offsets shouldBe empty
  }

  it should "convert an empty first item to an empty offsets list" in {
    val bytes = pixeDataFragments() ++ item(0) ++ item(4) ++ ByteString(1, 2, 3, 4) ++ sequenceDelimitation()

    val fragments = toElementsBlocking(Source.single(bytes)).getFragments(Tag.PixelData).get
    fragments.offsets shouldBe defined
    fragments.offsets.get shouldBe empty
    fragments.size shouldBe 1
  }

  it should "convert first item to offsets" in {
    val bytes = pixeDataFragments() ++ item(8) ++ intToBytesLE(0) ++ intToBytesLE(456) ++ item(4) ++
      ByteString(1, 2, 3, 4) ++ sequenceDelimitation()

    val fragments = toElementsBlocking(Source.single(bytes)).getFragments(Tag.PixelData).get
    fragments.offsets shouldBe defined
    fragments.offsets.get shouldBe List(0, 456)
  }

  it should "support access to frames based on fragments and offsets" in {
    val bytes = pixeDataFragments() ++ item(8) ++ intToBytesLE(0) ++ intToBytesLE(6) ++ item(4) ++
      ByteString(1, 2, 3, 4) ++ item(4) ++ ByteString(5, 6, 7, 8) ++ sequenceDelimitation()

    val iter = toElementsBlocking(Source.single(bytes)).getFragments(Tag.PixelData).get.frameIterator
    iter.hasNext shouldBe true
    iter.next shouldBe ByteString(1, 2, 3, 4, 5, 6)
    iter.hasNext shouldBe true
    iter.next shouldBe ByteString(7, 8)
    iter.hasNext shouldBe false
  }

  it should "return an empty iterator when offsets list and/or fragments are empty" in {
    val bytes1 = pixeDataFragments() ++ sequenceDelimitation()
    val bytes2 = pixeDataFragments() ++ item(0) ++ sequenceDelimitation()
    val bytes3 = pixeDataFragments() ++ item(0) ++ item(0) ++ sequenceDelimitation()
    val bytes4 = pixeDataFragments() ++ item(4) ++ intToBytesLE(0) ++ sequenceDelimitation()
    val bytes5 = pixeDataFragments() ++ item(4) ++ intToBytesLE(0) ++ item(0) ++ sequenceDelimitation()

    val iter1 = toElementsBlocking(Source.single(bytes1)).getFragments(Tag.PixelData).get.frameIterator
    val iter2 = toElementsBlocking(Source.single(bytes2)).getFragments(Tag.PixelData).get.frameIterator
    val iter3 = toElementsBlocking(Source.single(bytes3)).getFragments(Tag.PixelData).get.frameIterator
    val iter4 = toElementsBlocking(Source.single(bytes4)).getFragments(Tag.PixelData).get.frameIterator
    val iter5 = toElementsBlocking(Source.single(bytes5)).getFragments(Tag.PixelData).get.frameIterator

    iter1.hasNext shouldBe false
    iter2.hasNext shouldBe false
    iter3.hasNext shouldBe false
    iter4.hasNext shouldBe false
    iter5.hasNext shouldBe false
  }

  it should "support many frames per fragment and many fragments per frame" in {
    val bytes1 = pixeDataFragments() ++ item(12) ++ List(0, 2, 3).map(intToBytesLE).reduce(_ ++ _) ++ item(4) ++
      ByteString(1, 2, 3, 4) ++ sequenceDelimitation()
    val bytes2 = pixeDataFragments() ++ item(0) ++ item(2) ++ ByteString(1, 2) ++
      item(2) ++ ByteString(1, 2) ++ item(2) ++ ByteString(1, 2) ++ item(2) ++ ByteString(1, 2) ++ sequenceDelimitation()

    val iter1 = toElementsBlocking(Source.single(bytes1)).getFragments(Tag.PixelData).get.frameIterator
    val iter2 = toElementsBlocking(Source.single(bytes2)).getFragments(Tag.PixelData).get.frameIterator

    iter1.next shouldBe ByteString(1, 2)
    iter1.next shouldBe ByteString(3)
    iter1.next shouldBe ByteString(4)
    iter1.hasNext shouldBe false

    iter2.next shouldBe ByteString(1, 2, 1, 2, 1, 2, 1, 2)
    iter2.hasNext shouldBe false
  }
}
