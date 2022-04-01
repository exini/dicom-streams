package com.exini.dicom.streams

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestKit
import akka.util.ByteString
import com.exini.dicom.data.{ Tag, TagPath, VR, _ }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ Await, ExecutionContextExecutor }

class ModifyFlowTest
    extends TestKit(ActorSystem("ModifyFlowSpec"))
    with AnyFlatSpecLike
    with Matchers
    with BeforeAndAfterAll {

  import ModifyFlow._
  import ParseFlow.parseFlow
  import TestUtils._
  import com.exini.dicom.data.DicomParts._
  import com.exini.dicom.data.TestData._

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  override def afterAll(): Unit = {
    Await.ready(system.terminate(), 10.seconds)
    ()
  }

  "The modify flow" should "modify the value of the specified elements" in {
    val bytes = studyDate() ++ personNameJohnDoe()

    val mikeBytes = ByteString('M', 'i', 'k', 'e')

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(
        modifyFlow(modifications =
          Seq(
            TagModification.equals(TagPath.fromTag(Tag.StudyDate), _ => ByteString.empty),
            TagModification.equals(TagPath.fromTag(Tag.PatientName), _ => mikeBytes)
          )
        )
      )

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.StudyDate, VR.DA, 0L)
      .expectHeader(Tag.PatientName, VR.PN, mikeBytes.length.toLong)
      .expectValueChunk(mikeBytes)
      .expectDicomComplete()
  }

  it should "not modify elements in datasets other than the dataset the tag path points to" in {
    val bytes = sequence(
      Tag.DerivationCodeSequence
    ) ++ item() ++ personNameJohnDoe() ++ studyDate() ++ itemDelimitation() ++ sequenceDelimitation()

    val mikeBytes = ByteString('M', 'i', 'k', 'e')

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(modifyFlow(modifications = Seq(TagModification.equals(TagPath.fromTag(Tag.PatientName), _ => mikeBytes))))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem()
      .expectHeader(Tag.PatientName, VR.PN, personNameJohnDoe().length - 8L)
      .expectValueChunk(personNameJohnDoe().drop(8))
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "insert elements if not present" in {
    val bytes = personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(modifyFlow(insertions = Seq(TagInsertion(TagPath.fromTag(Tag.StudyDate), _ => studyDate().drop(8)))))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.StudyDate, VR.DA, studyDate().length - 8L)
      .expectValueChunk(studyDate().drop(8))
      .expectHeader(Tag.PatientName, VR.PN, personNameJohnDoe().length - 8L)
      .expectValueChunk(personNameJohnDoe().drop(8))
      .expectDicomComplete()
  }

  it should "insert elements if not present also at end of dataset" in {
    val bytes = studyDate()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(
        modifyFlow(insertions = Seq(TagInsertion(TagPath.fromTag(Tag.PatientName), _ => personNameJohnDoe().drop(8))))
      )

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.StudyDate, VR.DA, studyDate().length - 8L)
      .expectValueChunk(studyDate().drop(8))
      .expectHeader(Tag.PatientName, VR.PN, personNameJohnDoe().length - 8L)
      .expectValueChunk(personNameJohnDoe().drop(8))
      .expectDicomComplete()
  }

  it should "insert elements if not present also at end of dataset when last element is empty" in {
    val bytes = tagToBytesLE(0x00080050) ++ ByteString("SH") ++ shortToBytesLE(0x0000)

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(modifyFlow(insertions = Seq(TagInsertion(TagPath.fromTag(Tag.SOPInstanceUID), _ => ByteString("1.2.3.4 ")))))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.SOPInstanceUID, VR.UI, 8)
      .expectValueChunk(8)
      .expectHeader(Tag.AccessionNumber, VR.SH, 0)
      .expectDicomComplete()
  }

  it should "insert elements between a normal attribute and a sequence" in {
    val bytes = studyDate() ++ sequence(Tag.AbstractPriorCodeSequence) ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(
        modifyFlow(insertions = Seq(TagInsertion(TagPath.fromTag(Tag.PatientName), _ => personNameJohnDoe().drop(8))))
      )

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.StudyDate, VR.DA, studyDate().length - 8L)
      .expectValueChunk(studyDate().drop(8))
      .expectHeader(Tag.PatientName, VR.PN, personNameJohnDoe().length - 8L)
      .expectValueChunk(personNameJohnDoe().drop(8))
      .expectSequence(Tag.AbstractPriorCodeSequence)
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "insert elements between a sequence and a normal attribute" in {
    val bytes = sequence(Tag.DerivationCodeSequence) ++ sequenceDelimitation() ++ patientID()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(
        modifyFlow(insertions = Seq(TagInsertion(TagPath.fromTag(Tag.PatientName), _ => personNameJohnDoe().drop(8))))
      )

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence)
      .expectSequenceDelimitation()
      .expectHeader(Tag.PatientName, VR.PN, personNameJohnDoe().length - 8L)
      .expectValueChunk(personNameJohnDoe().drop(8))
      .expectHeader(Tag.PatientID, VR.LO, patientID().length - 8L)
      .expectValueChunk(patientID().drop(8))
      .expectDicomComplete()
  }

  it should "insert elements between two sequences" in {
    val bytes = sequence(Tag.DerivationCodeSequence) ++ sequenceDelimitation() ++ sequence(
      Tag.AbstractPriorCodeSequence
    ) ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(
        modifyFlow(insertions = Seq(TagInsertion(TagPath.fromTag(Tag.PatientName), _ => personNameJohnDoe().drop(8))))
      )

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence)
      .expectSequenceDelimitation()
      .expectHeader(Tag.PatientName, VR.PN, personNameJohnDoe().length - 8L)
      .expectValueChunk(personNameJohnDoe().drop(8))
      .expectSequence(Tag.AbstractPriorCodeSequence)
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "modify, not insert, when 'insert' elements are already present" in {
    val bytes = studyDate() ++ personNameJohnDoe()

    val mikeBytes = ByteString('M', 'i', 'k', 'e')

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(
        modifyFlow(insertions =
          Seq(
            TagInsertion(TagPath.fromTag(Tag.StudyDate), _ => ByteString.empty),
            TagInsertion(TagPath.fromTag(Tag.PatientName), _ => mikeBytes)
          )
        )
      )

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.StudyDate, VR.DA, 0L)
      .expectHeader(Tag.PatientName, VR.PN, mikeBytes.length.toLong)
      .expectValueChunk(mikeBytes)
      .expectDicomComplete()
  }

  it should "modify based on current value, when 'insert' elements are already present" in {
    val bytes = personNameJohnDoe()

    val mikeBytes = ByteString('M', 'i', 'k', 'e')

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(
        modifyFlow(insertions =
          Seq(TagInsertion(TagPath.fromTag(Tag.PatientName), _.map(_ ++ ByteString(" Senior ")).getOrElse(mikeBytes)))
        )
      )

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.PatientName, VR.PN, 16)
      .expectValueChunk(ByteString("John^Doe Senior "))
      .expectDicomComplete()
  }

  it should "insert all relevant elements below the current tag number" in {
    val bytes = personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(
        modifyFlow(insertions =
          Seq(
            TagInsertion(TagPath.fromTag(Tag.SeriesDate), _ => studyDate().drop(8)),
            TagInsertion(TagPath.fromTag(Tag.StudyDate), _ => studyDate().drop(8))
          )
        )
      )

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.StudyDate, VR.DA, studyDate().length - 8L)
      .expectValueChunk(studyDate().drop(8))
      .expectHeader(Tag.SeriesDate, VR.DA, studyDate().length - 8L)
      .expectValueChunk(studyDate().drop(8))
      .expectHeader(Tag.PatientName, VR.PN, personNameJohnDoe().length - 8L)
      .expectValueChunk(personNameJohnDoe().drop(8))
      .expectDicomComplete()
  }

  it should "not insert elements if dataset contains no elements" in {
    val source = Source.empty
      .via(parseFlow)
      .via(modifyFlow(insertions = Seq(TagInsertion(TagPath.fromTag(Tag.SeriesDate), _ => studyDate().drop(8)))))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectDicomComplete()
  }

  it should "insert elements in sequences if sequence is present but element is not present" in {
    val bytes = sequence(
      Tag.DerivationCodeSequence
    ) ++ item() ++ personNameJohnDoe() ++ itemDelimitation() ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(
        modifyFlow(insertions =
          Seq(
            TagInsertion(
              TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.StudyDate),
              _ => studyDate().drop(8)
            )
          )
        )
      )

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem()
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "skip inserting elements in missing sequences" in {
    val bytes = personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(
        modifyFlow(insertions =
          Seq(
            TagInsertion(
              TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.StudyDate),
              _ => studyDate().drop(8)
            ),
            TagInsertion(
              TagPath.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.PatientName),
              _ => ByteString.empty
            )
          )
        )
      )

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "not insert unknown elements" in {
    val bytes = personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(modifyFlow(insertions = Seq(TagInsertion(TagPath.fromTag(0x00200021), _ => ByteString(1, 2, 3, 4)))))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectDicomError()
  }

  it should "not insert sequences" in {
    val bytes = personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(
        modifyFlow(insertions = Seq(TagInsertion(TagPath.fromTag(Tag.DerivationCodeSequence), _ => ByteString.empty)))
      )

    source
      .runWith(TestSink.probe[DicomPart])
      .expectDicomError()
  }

  it should "insert into the correct sequence item" in {
    val bytes = sequence(
      Tag.DerivationCodeSequence
    ) ++ item() ++ personNameJohnDoe() ++ itemDelimitation() ++ item() ++ personNameJohnDoe() ++ itemDelimitation() ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(
        modifyFlow(insertions =
          Seq(
            TagInsertion(
              TagPath.fromItem(Tag.DerivationCodeSequence, 2).thenTag(Tag.StudyDate),
              _ => studyDate().drop(8)
            )
          )
        )
      )

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectItem()
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "modify the correct sequence item" in {
    val bytes = sequence(
      Tag.DerivationCodeSequence
    ) ++ item() ++ personNameJohnDoe() ++ itemDelimitation() ++ item() ++ personNameJohnDoe() ++ itemDelimitation() ++ sequenceDelimitation()

    val mikeBytes = ByteString('M', 'i', 'k', 'e')

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(
        modifyFlow(modifications =
          Seq(
            TagModification
              .equals(TagPath.fromItem(Tag.DerivationCodeSequence, 2).thenTag(Tag.PatientName), _ => mikeBytes)
          )
        )
      )

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem()
      .expectHeader(Tag.PatientName, VR.PN, personNameJohnDoe().drop(8).length.toLong)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectItem()
      .expectHeader(Tag.PatientName, VR.PN, mikeBytes.length.toLong)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "modify all sequence items" in {
    val bytes = sequence(
      Tag.DerivationCodeSequence
    ) ++ item() ++ personNameJohnDoe() ++ itemDelimitation() ++ item() ++ personNameJohnDoe() ++ itemDelimitation() ++ sequenceDelimitation()

    val mikeBytes = ByteString('M', 'i', 'k', 'e')
    val tagTree   = TagTree.fromAnyItem(Tag.DerivationCodeSequence).thenTag(Tag.PatientName)

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(modifyFlow(modifications = Seq(TagModification(tagTree.hasPath, _ => mikeBytes))))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem()
      .expectHeader(Tag.PatientName, VR.PN, mikeBytes.length.toLong)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectItem()
      .expectHeader(Tag.PatientName, VR.PN, mikeBytes.length.toLong)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "correctly sort elements with tag numbers exceeding the positive range of its signed integer representation" in {
    val bytes = preamble ++ fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ ByteString(0xff, 0xff, 0xff,
      0xff, 68, 65, 10, 0, 49, 56, 51, 49, 51, 56, 46, 55, 54, 53)

    val mikeBytes = ByteString('M', 'i', 'k', 'e')

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(modifyFlow(insertions = Seq(TagInsertion(TagPath.fromTag(Tag.PatientName), _ => mikeBytes))))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectPreamble()
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectHeader(Tag.PatientName, VR.PN, mikeBytes.length.toLong)
      .expectValueChunk()
      .expectHeader(-1, VR.DA, 10)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "work also with the endsWith modification matcher" in {
    val bytes = studyDate() ++ sequence(
      Tag.DerivationCodeSequence
    ) ++ item() ++ studyDate() ++ personNameJohnDoe() ++ itemDelimitation() ++ sequenceDelimitation()

    val studyBytes = ByteString("2012-01-01")

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(modifyFlow(modifications = Seq(TagModification.endsWith(TagPath.fromTag(Tag.StudyDate), _ => studyBytes))))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.StudyDate)
      .expectValueChunk(studyBytes)
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem()
      .expectHeader(Tag.StudyDate)
      .expectValueChunk(studyBytes)
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "pick up tag modifications from the stream" in {
    val bytes = studyDate() ++ personNameJohnDoe()

    val mikeBytes = ByteString('M', 'i', 'k', 'e')

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .prepend(
        Source.single(
          TagModificationsPart(Seq(TagModification.equals(TagPath.fromTag(Tag.PatientName), _ => mikeBytes)), Seq.empty)
        )
      )
      .via(
        modifyFlow(modifications = Seq(TagModification.equals(TagPath.fromTag(Tag.StudyDate), _ => ByteString.empty)))
      )

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.StudyDate, VR.DA, 0L)
      .expectHeader(Tag.PatientName, VR.PN, mikeBytes.length.toLong)
      .expectValueChunk(mikeBytes)
      .expectDicomComplete()
  }

  it should "pick up tag modifications and replace old modifications" in {
    val bytes = studyDate() ++ personNameJohnDoe()

    val mikeBytes = ByteString('M', 'i', 'k', 'e')

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .prepend(
        Source.single(
          TagModificationsPart(
            Seq(TagModification.equals(TagPath.fromTag(Tag.PatientName), _ => mikeBytes)),
            Seq.empty,
            replace = true
          )
        )
      )
      .via(
        modifyFlow(modifications = Seq(TagModification.equals(TagPath.fromTag(Tag.StudyDate), _ => ByteString.empty)))
      )

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectHeader(Tag.PatientName, VR.PN, mikeBytes.length.toLong)
      .expectValueChunk(mikeBytes)
      .expectDicomComplete()
  }

  it should "not emit sequence and item delimiters for data with explicit length sequences and items" in {
    val bytes = personNameJohnDoe() ++
      sequence(Tag.DerivationCodeSequence, 24) ++
      item(16) ++ studyDate()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(modifyFlow(logGroupLengthWarnings = false))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectSequence(Tag.DerivationCodeSequence, 24)
      .expectItem(16)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectDicomComplete()
  }
}
