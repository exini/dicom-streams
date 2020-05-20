package com.exini.dicom.streams

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestKit
import akka.util.ByteString
import com.exini.dicom.data.DicomParts.{ DicomPart, ElementsPart }
import com.exini.dicom.data.TestData._
import com.exini.dicom.data.{ Tag, TagTree, item, _ }
import com.exini.dicom.streams.CollectFlow._
import com.exini.dicom.streams.ParseFlow.parseFlow
import com.exini.dicom.streams.TestUtils._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContextExecutor

class CollectFlowTest
    extends TestKit(ActorSystem("CollectFlowSpec"))
    with AnyFlatSpecLike
    with Matchers
    with BeforeAndAfterAll {

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  override def afterAll(): Unit = system.terminate()

  "A collect elements flow" should "first produce an elements part followed by the input dicom parts" in {
    val bytes = studyDate() ++ personNameJohnDoe()
    val tags  = Set(Tag.StudyDate, Tag.PatientName).map(TagTree.fromTag)
    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(collectFlow(tags, "tag"))

    source
      .runWith(TestSink.probe[DicomPart])
      .request(1)
      .expectNextChainingPF {
        case e: ElementsPart =>
          e.label shouldBe "tag"
          e.elements should have size 2
          e.elements(Tag.StudyDate) should not be empty
          e.elements(Tag.PatientName) should not be empty
      }
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "produce an empty elements part when stream is empty" in {
    val bytes = ByteString.empty

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(collectFlow(Set.empty, "tag"))

    source
      .runWith(TestSink.probe[DicomPart])
      .request(1)
      .expectNextChainingPF {
        case e: ElementsPart => e.elements.isEmpty shouldBe true
      }
      .expectDicomComplete()
  }

  it should "produce an empty elements part when no relevant data elements are present" in {
    val bytes = personNameJohnDoe() ++ studyDate()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(collectFlow(Set(Tag.Modality, Tag.SeriesInstanceUID).map(TagTree.fromTag), "tag"))

    source
      .runWith(TestSink.probe[DicomPart])
      .request(1)
      .expectNextChainingPF {
        case e: ElementsPart => e.elements.isEmpty shouldBe true
      }
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "apply the stop tag appropriately" in {
    val bytes = studyDate() ++ personNameJohnDoe() ++ pixelData(2000)

    val source = Source
      .single(bytes)
      .via(ParseFlow(chunkSize = 500))
      .via(collectFlow(Set(Tag.StudyDate, Tag.PatientName).map(TagTree.fromTag), "tag", maxBufferSize = 1000))

    source
      .runWith(TestSink.probe[DicomPart])
      .request(1)
      .expectNextChainingPF {
        case e: ElementsPart =>
          e.label shouldBe "tag"
          e.elements.size shouldBe 2
          e.elements(Tag.StudyDate) should not be empty
          e.elements(Tag.PatientName) should not be empty
      }
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectHeader(Tag.PixelData)
      .expectValueChunk()
      .expectValueChunk()
      .expectValueChunk()
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "fail if max buffer size is exceeded" in {
    val bytes = studyDate() ++ personNameJohnDoe() ++ pixelData(2000)

    val source = Source
      .single(bytes)
      .via(ParseFlow(chunkSize = 500))
      .via(collectFlow(_.tag == Tag.PatientName, _.tag > Tag.PixelData, "tag", maxBufferSize = 1000))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectDicomError()
  }

  it should "collect attributes in sequences" in {
    val bytes = studyDate() ++
      (
        sequence(Tag.DerivationCodeSequence, 8 + 16 + 12 + 8 + 16 + 8 + 8 + 16) ++
          item(16 + 12 + 8 + 16 + 8 + 8 + 16) ++
          studyDate() ++ (sequence(Tag.DerivationCodeSequence) ++ item() ++ studyDate() ++
            itemDelimitation() ++ sequenceDelimitation()) ++
          personNameJohnDoe()
      ) ++ patientID()

    val source = Source
      .single(bytes)
      .via(ParseFlow(chunkSize = 500))
      .via(
        collectFlow(
          Set(
            TagTree.fromTag(Tag.PatientID),
            TagTree.fromItem(Tag.DerivationCodeSequence, 1)
          ),
          "tag"
        )
      )

    source
      .runWith(TestSink.probe[DicomPart])
      .request(1)
      .expectNextChainingPF {
        case e: ElementsPart =>
          e.label shouldBe "tag"
          e.elements.size shouldBe 2
          e.elements(Tag.PatientID) should not be empty
          e.elements(Tag.DerivationCodeSequence) should not be empty
          e.elements.getSequence(Tag.DerivationCodeSequence).get.item(1).size shouldBe 1
      }
  }

  it should "collect fragments" in {
    val bytes = studyDate() ++ pixeDataFragments() ++ item(4) ++ ByteString(1, 2, 3, 4) ++ item(4) ++
      ByteString(5, 6, 7, 8) ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(ParseFlow(chunkSize = 500))
      .via(collectFlow(Set(TagTree.fromTag(Tag.PixelData)),"tag"))

    source
      .runWith(TestSink.probe[DicomPart])
      .request(1)
      .expectNextChainingPF {
        case e: ElementsPart =>
          e.label shouldBe "tag"
          e.elements.size shouldBe 1
          val f = e.elements.getFragments(Tag.PixelData)
          f should not be empty
          f.get.offsets shouldBe defined
          f.get.fragments should have length 1
      }
  }
}
