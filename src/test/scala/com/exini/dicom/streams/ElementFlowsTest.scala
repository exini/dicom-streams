package com.exini.dicom.streams

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestKit
import akka.util.ByteString
import com.exini.dicom.data.DicomElements._
import com.exini.dicom.data.TagPath.EmptyTagPath
import com.exini.dicom.data.TestData._
import com.exini.dicom.data._
import com.exini.dicom.streams.ElementFlows._
import com.exini.dicom.streams.TestUtils._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ Await, ExecutionContextExecutor }

class ElementFlowsTest
    extends TestKit(ActorSystem("ElementFlowsSpec"))
    with AnyFlatSpecLike
    with Matchers
    with BeforeAndAfterAll {

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  override def afterAll(): Unit = {
    Await.ready(system.terminate(), 10.seconds)
    ()
  }

  "A DICOM elements flow" should "combine headers and value chunks into elements" in {
    val bytes = personNameJohnDoe() ++ studyDate()

    val source = Source
      .single(bytes)
      .via(ParseFlow())
      .via(elementFlow)

    source
      .runWith(TestSink())
      .expectElement(Tag.PatientName)
      .expectElement(Tag.StudyDate)
      .expectDicomComplete()
  }

  it should "combine items in fragments into fragment elements" in {
    val bytes = pixeDataFragments() ++ item(4) ++ ByteString(1, 2, 3, 4) ++ item(4) ++ ByteString(
      5,
      6,
      7,
      8
    ) ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(ParseFlow())
      .via(elementFlow)

    source
      .runWith(TestSink())
      .expectFragments(Tag.PixelData)
      .expectFragment(4)
      .expectFragment(4)
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "handle elements and fragments of zero length" in {
    val bytes = ByteString(8, 0, 32, 0, 68, 65, 0, 0) ++ personNameJohnDoe() ++
      pixeDataFragments() ++ item(0) ++ item(4) ++ ByteString(5, 6, 7, 8) ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(ParseFlow())
      .via(elementFlow)

    source
      .runWith(TestSink())
      .expectElement(Tag.StudyDate, ByteString.empty)
      .expectElement(Tag.PatientName, ByteString("John^Doe"))
      .expectFragments(Tag.PixelData)
      .expectFragment(0)
      .expectFragment(4)
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "handle determinate length sequences and items" in {
    val bytes =
      sequence(Tag.DerivationCodeSequence, 24) ++ item(16) ++ personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(ParseFlow())
      .via(elementFlow)

    source
      .runWith(TestSink())
      .expectSequence(Tag.DerivationCodeSequence, 24)
      .expectItem(16)
      .expectElement(Tag.PatientName)
      .expectDicomComplete()
  }

  "The tag path flow" should "pair elements with their respective tag paths" in {
    val bytes = preamble ++ fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++
      studyDate() ++
      sequence(
        Tag.DerivationCodeSequence
      ) ++ item() ++ personNameJohnDoe() ++ itemDelimitation() ++ sequenceDelimitation() ++
      pixeDataFragments() ++ item(4) ++ ByteString(1, 2, 3, 4) ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(ParseFlow())
      .via(elementFlow)
      .via(tagPathFlow)

    source
      .runWith(TestSink())
      .request(1)
      .expectNextChainingPF {
        case (EmptyTagPath, PreambleElement) => true
      }
      .request(1)
      .expectNextChainingPF {
        case (tp: TagPath, e: ValueElement)
            if e.tag == Tag.FileMetaInformationGroupLength && tp == TagPath.fromTag(
              Tag.FileMetaInformationGroupLength
            ) =>
          true
      }
      .request(1)
      .expectNextChainingPF {
        case (tp: TagPath, e: ValueElement)
            if e.tag == Tag.TransferSyntaxUID && tp == TagPath.fromTag(Tag.TransferSyntaxUID) =>
          true
      }
      .request(1)
      .expectNextChainingPF {
        case (tp: TagPath, e: ValueElement) if e.tag == Tag.StudyDate && tp == TagPath.fromTag(Tag.StudyDate) => true
      }
      .request(1)
      .expectNextChainingPF {
        case (tp: TagPath, e: SequenceElement)
            if e.tag == Tag.DerivationCodeSequence && tp == TagPath.fromSequence(Tag.DerivationCodeSequence) =>
          true
      }
      .request(1)
      .expectNextChainingPF {
        case (tp: TagPath, _: ItemElement) if tp == TagPath.fromItem(Tag.DerivationCodeSequence, 1) =>
          true
      }
      .request(1)
      .expectNextChainingPF {
        case (tp: TagPath, e: ValueElement)
            if e.tag == Tag.PatientName && tp == TagPath
              .fromItem(Tag.DerivationCodeSequence, 1)
              .thenTag(Tag.PatientName) =>
          true
      }
      .request(1)
      .expectNextChainingPF {
        case (tp: TagPath, _: ItemDelimitationElement) if tp == TagPath.fromItemEnd(Tag.DerivationCodeSequence, 1) =>
          true
      }
      .request(1)
      .expectNextChainingPF {
        case (tp: TagPath, _: SequenceDelimitationElement)
            if tp == TagPath.fromSequenceEnd(Tag.DerivationCodeSequence) =>
          true
      }
      .request(1)
      .expectNextChainingPF {
        case (tp: TagPath, e: FragmentsElement) if e.tag == Tag.PixelData && tp == TagPath.fromTag(Tag.PixelData) =>
          true
      }
      .request(1)
      .expectNextChainingPF {
        case (tp: TagPath, _: FragmentElement) if tp == TagPath.fromTag(Tag.PixelData) => true
      }
      .request(1)
      .expectNextChainingPF {
        case (tp: TagPath, _: SequenceDelimitationElement) if tp == TagPath.fromTag(Tag.PixelData) => true
      }
      .request(1)
      .expectComplete()
  }

  it should "handle determinate length sequences and items" in {
    val bytes =
      sequence(Tag.DerivationCodeSequence, 24) ++ item(16) ++ personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(ParseFlow())
      .via(elementFlow)
      .via(tagPathFlow)

    source
      .runWith(TestSink())
      .request(1)
      .expectNextChainingPF {
        case (_: TagPath, _: SequenceElement) => true
      }
      .request(1)
      .expectNextChainingPF {
        case (_: TagPath, _: ItemElement) => true
      }
      .request(1)
      .expectNextChainingPF {
        case (_: TagPath, _: ValueElement) => true
      }
      .request(1)
      .expectComplete()
  }

  it should "handle zero length values, sequence and items" in {
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
      FragmentsElement(Tag.PixelData, VR.OB),
      FragmentElement(0, Value.empty),
      SequenceDelimitationElement(),
      FragmentsElement(Tag.PixelData, VR.OB),
      SequenceDelimitationElement()
    )

    val source = Source(elementList)
      .via(tagPathFlow)

    source
      .runWith(TestSink())
      .request(1)
      .expectNextChainingPF {
        case (_: TagPath, e: ValueElement) if e.length == 0 => true
      }
      .request(1)
      .expectNextChainingPF {
        case (_: TagPath, _: SequenceElement) => true
      }
      .request(1)
      .expectNextChainingPF {
        case (_: TagPath, _: SequenceDelimitationElement) => true
      }
      .request(1)
      .expectNextChainingPF {
        case (_: TagPath, _: SequenceElement) => true
      }
      .request(1)
      .expectNextChainingPF {
        case (_: TagPath, _: SequenceElement) => true
      }
      .request(1)
      .expectNextChainingPF {
        case (_: TagPath, _: ItemElement) => true
      }
      .request(1)
      .expectNextChainingPF {
        case (_: TagPath, _: ItemDelimitationElement) => true
      }
      .request(1)
      .expectNextChainingPF {
        case (_: TagPath, _: ItemElement) => true
      }
      .request(1)
      .expectNextChainingPF {
        case (_: TagPath, _: SequenceDelimitationElement) => true
      }
      .request(1)
      .expectNextChainingPF {
        case (_: TagPath, _: FragmentsElement) => true
      }
      .request(1)
      .expectNextChainingPF {
        case (_: TagPath, _: FragmentElement) => true
      }
      .request(1)
      .expectNextChainingPF {
        case (_: TagPath, _: SequenceDelimitationElement) => true
      }
      .request(1)
      .expectNextChainingPF {
        case (_: TagPath, _: FragmentsElement) => true
      }
      .request(1)
      .expectNextChainingPF {
        case (_: TagPath, _: SequenceDelimitationElement) => true
      }
      .request(1)
      .expectComplete()
  }

}
