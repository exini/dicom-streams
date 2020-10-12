package com.exini.dicom.streams

import java.io.File

import akka.actor.ActorSystem
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestKit
import akka.util.ByteString
import com.exini.dicom.data.DicomElements.ValueElement
import com.exini.dicom.data.DicomParts.{DicomPart, MetaPart}
import com.exini.dicom.data.TestData._
import com.exini.dicom.data._
import com.exini.dicom.streams.DicomFlows._
import com.exini.dicom.streams.ModifyFlow._
import com.exini.dicom.streams.ParseFlow._
import com.exini.dicom.streams.TestUtils._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor}

class DicomFlowsTest
    extends TestKit(ActorSystem("DicomFlowsSpec"))
    with AnyFlatSpecLike
    with Matchers
    with BeforeAndAfterAll {

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  override def afterAll(): Unit = system.terminate()

  "A print flow" should "not change incoming elements" in {
    val bytes = preamble ++ fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(printFlow)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectPreamble()
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectDicomComplete()
  }

  "The stop tag flow" should "stop reading data when a stop tag is reached" in {
    val bytes = studyDate() ++ personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(stopTagFlow(Tag.PatientName))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "stop reading data when a tag number is higher than the stop tag" in {
    val bytes = studyDate() ++ personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(stopTagFlow(Tag.StudyDate + 1))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "apply stop tag correctly also when preceded by sequence and ignore tags in sequences" in {
    val bytes = studyDate() ++ sequence(Tag.DerivationCodeSequence) ++ item() ++ pixelData(
      10
    ) ++ itemDelimitation() ++ sequenceDelimitation() ++ personNameJohnDoe() ++ pixelData(100)

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(stopTagFlow(Tag.PatientName + 1))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem(1)
      .expectHeader(Tag.PixelData)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectDicomComplete()
  }

  "The DICOM group length discard filter" should "discard group length elements except 0002,0000" in {
    val bytes = preamble ++ fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ groupLength(
      8,
      studyDate().length
    ) ++ studyDate()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(groupLengthDiscardFilter)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectPreamble()
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "discard group length elements except 0002,0000 when testing with dicom file" in {
    val file = new File(getClass.getResource("../data/test001.dcm").toURI)
    val source = FileIO
      .fromPath(file.toPath)
      .via(parseFlow)
      .via(groupLengthDiscardFilter)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectPreamble()
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.FileMetaInformationVersion)
      .expectValueChunk()
  }

  "The DICOM file meta information discard filter" should "discard file meta informaton" in {
    val bytes =
      preamble ++ fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ personNameJohnDoe() ++ studyDate()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(fmiDiscardFilter)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "discard file meta information when testing with dicom files" in {
    val file = new File(getClass.getResource("../data/test001.dcm").toURI)
    val source = FileIO
      .fromPath(file.toPath)
      .via(parseFlow)
      .via(fmiDiscardFilter)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.SpecificCharacterSet)
      .expectValueChunk()
      .expectHeader(Tag.ImageType)
  }

  "The tag filter" should "filter elements in sequences" in {
    val bytes = sequence(
      Tag.DerivationCodeSequence
    ) ++ item() ++ studyDate() ++ personNameJohnDoe() ++ itemDelimitation() ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(tagFilter(tagPath => tagPath.tag != Tag.PatientName))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem(1)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "filter elements not matching the condition" in {
    val bytes = preamble ++ fmiGroupLength(
      fmiVersion(),
      transferSyntaxUID()
    ) ++ fmiVersion() ++ transferSyntaxUID() ++ personNameJohnDoe() ++ studyDate()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(tagFilter(tagPath => groupNumber(tagPath.tag) >= 8, _ => false))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "filter elements not matching the condition when testing with sample dicom files" in {
    val file = new File(getClass.getResource("../data/test001.dcm").toURI)
    val source = FileIO
      .fromPath(file.toPath)
      .via(parseFlow)
      .via(tagFilter(tagPath => tagPath.tag == Tag.PatientName, _ => false))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "filter elements matching the condition" in {
    val bytes = preamble ++ fmiGroupLength(
      fmiVersion(),
      transferSyntaxUID()
    ) ++ fmiVersion() ++ transferSyntaxUID() ++ studyDate()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(tagFilter(tagPath => !isFileMetaInformation(tagPath.tag), _ => false))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "filter elements matching the blacklist condition when testing with sample dicom files" in {
    val file = new File(getClass.getResource("../data/test001.dcm").toURI)
    val source = FileIO
      .fromPath(file.toPath)
      .via(parseFlow)
      .via(tagFilter(tagPath => !isFileMetaInformation(tagPath.tag), _ => false))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.SpecificCharacterSet)
      .expectValueChunk()
      .expectHeader(Tag.ImageType)
  }

  it should "filter leave the dicom file unchanged when blacklist condition does not match any elements" in {
    val file = new File(getClass.getResource("../data/test001.dcm").toURI)
    val source = FileIO
      .fromPath(file.toPath)
      .via(parseFlow)
      .via(tagFilter(tagPath => !isPrivate(tagPath.tag)))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectPreamble()
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.FileMetaInformationVersion)
      .expectValueChunk()
  }

  "The whitelist filter" should "block all elements not on the white list" in {
    val bytes =
      preamble ++ fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ personNameJohnDoe() ++ studyDate()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(whitelistFilter(Set(TagTree.fromTag(Tag.StudyDate)), _ => false))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "only apply to elements in the root dataset when filter points to root dataset" in {
    val bytes = sequence(
      Tag.DerivationCodeSequence
    ) ++ item() ++ personNameJohnDoe() ++ studyDate() ++ itemDelimitation() ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(whitelistFilter(Set(TagTree.fromTag(Tag.StudyDate)), _ => false))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectDicomComplete()
  }

  it should "also work on fragments" in {
    val bytes = pixeDataFragments() ++ item(4) ++ ByteString(1, 2, 3, 4) ++ item(4) ++
      ByteString(5, 6, 7, 8) ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(whitelistFilter(Set.empty, _ => false))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectDicomComplete()
  }

  it should "preserve sequences and items in nested structures when using wildcards" in {
    val bytes = personNameJohnDoe() ++ sequence(
      Tag.DerivationCodeSequence
    ) ++ item() ++ personNameJohnDoe() ++ studyDate() ++ itemDelimitation() ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(whitelistFilter(Set(TagTree.fromAnyItem(Tag.DerivationCodeSequence).thenTag(Tag.StudyDate)), _ => false))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem(1)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "preserve sequences and items in nested structures when using item indices" in {
    val bytes = personNameJohnDoe() ++ sequence(
      Tag.DerivationCodeSequence
    ) ++ item() ++ personNameJohnDoe() ++ itemDelimitation() ++ item() ++ studyDate() ++ itemDelimitation() ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(whitelistFilter(Set(TagTree.fromItem(Tag.DerivationCodeSequence, 2).thenTag(Tag.StudyDate)), _ => false))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem(2)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  "The blacklist filter" should "block the entire sequence when a sequence tag is on the black list" in {
    val bytes = studyDate() ++
      (sequence(Tag.DerivationCodeSequence) ++ item() ++ personNameJohnDoe() ++
        (sequence(
          Tag.AbstractPriorCodeSequence
        ) ++ item() ++ personNameJohnDoe() ++ itemDelimitation() ++ sequenceDelimitation()) ++
        itemDelimitation() ++ sequenceDelimitation()) ++
      personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(blacklistFilter(Set(TagTree.fromAnyItem(Tag.DerivationCodeSequence)), _ => false))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "block a single item inside a sequence" in {
    val bytes = studyDate() ++
      sequence(
        Tag.DerivationCodeSequence
      ) ++ item() ++ personNameJohnDoe() ++ itemDelimitation() ++ item() ++ studyDate() ++ itemDelimitation() ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(
        blacklistFilter(
          Set(TagTree.fromTag(Tag.StudyDate), TagTree.fromItem(Tag.DerivationCodeSequence, 1)),
          _ => false
        )
      )

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem(2)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "block an element in an item in a sequence" in {
    val bytes = studyDate() ++
      sequence(
        Tag.DerivationCodeSequence
      ) ++ item() ++ personNameJohnDoe() ++ itemDelimitation() ++ item() ++ studyDate() ++ itemDelimitation() ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(blacklistFilter(Set(TagTree.fromItem(Tag.DerivationCodeSequence, 1).thenTag(Tag.StudyDate)), _ => true))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem(1)
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectItem(2)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  "The header part filter" should "discard elements based on its header part" in {
    val bytes =
      studyDate() ++ sequence(Tag.DerivationCodeSequence) ++ item() ++ personNameJohnDoe() ++ itemDelimitation() ++
        item() ++ studyDate() ++ itemDelimitation() ++ sequenceDelimitation() ++ personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(headerFilter(header => header.vr == VR.PN))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem(1)
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectItem(2)
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectDicomComplete()
  }

  "The context validation flow" should "accept DICOM data that corresponds to the given contexts" in {
    val contexts = Seq(ValidationContext(UID.CTImageStorage, UID.ExplicitVRLittleEndian))
    val bytes = preamble ++
      fmiGroupLength(mediaStorageSOPClassUID() ++ mediaStorageSOPInstanceUID() ++ transferSyntaxUID()) ++
      mediaStorageSOPClassUID() ++ mediaStorageSOPInstanceUID() ++ transferSyntaxUID()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(validateContextFlow(contexts))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectPreamble()
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.MediaStorageSOPClassUID)
      .expectValueChunk()
      .expectHeader(Tag.MediaStorageSOPInstanceUID)
      .expectValueChunk()
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "accept SOP Class UID specified in either file meta information or in the dataset" in {
    val contexts = Seq(ValidationContext(UID.CTImageStorage, UID.ExplicitVRLittleEndian))
    val bytes = preamble ++
      fmiGroupLength(mediaStorageSOPInstanceUID() ++ transferSyntaxUID()) ++
      mediaStorageSOPInstanceUID() ++ transferSyntaxUID() ++ sopClassUID()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(validateContextFlow(contexts))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectPreamble()
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.MediaStorageSOPInstanceUID)
      .expectValueChunk()
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectHeader(Tag.SOPClassUID)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "not accept DICOM data that does not correspond to the given contexts" in {
    val contexts = Seq(ValidationContext(UID.CTImageStorage, "1.2.840.10008.1.2.2"))
    val bytes = preamble ++
      fmiGroupLength(
        fmiVersion() ++ mediaStorageSOPClassUID() ++ mediaStorageSOPInstanceUID() ++ transferSyntaxUID()
      ) ++
      fmiVersion() ++ mediaStorageSOPClassUID() ++ mediaStorageSOPInstanceUID() ++ transferSyntaxUID()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(validateContextFlow(contexts))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectDicomError()
  }

  it should "not accept a file with no SOPCLassUID if a context is given" in {
    val contexts = Seq(ValidationContext(UID.CTImageStorage, UID.ExplicitVRLittleEndian))
    val bytes = preamble ++
      fmiGroupLength(fmiVersion() ++ mediaStorageSOPInstanceUID() ++ transferSyntaxUID()) ++
      fmiVersion() ++ mediaStorageSOPInstanceUID() ++ transferSyntaxUID()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(validateContextFlow(contexts))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectDicomError()
  }

  it should "not accept a file with no TransferSyntaxUID if a context is given" in {
    val contexts = Seq(ValidationContext(UID.CTImageStorage, UID.ExplicitVRLittleEndian))
    val bytes = preamble ++
      fmiGroupLength(fmiVersion() ++ mediaStorageSOPClassUID() ++ mediaStorageSOPInstanceUID()) ++
      fmiVersion() ++ mediaStorageSOPClassUID() ++ mediaStorageSOPInstanceUID()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(validateContextFlow(contexts))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectDicomError()
  }

  it should "not accept DICOM data if no valid contexts are given" in {
    val contexts = Seq()
    val bytes = preamble ++
      fmiGroupLength(
        fmiVersion() ++ mediaStorageSOPClassUID() ++ mediaStorageSOPInstanceUID() ++ transferSyntaxUID()
      ) ++
      fmiVersion() ++ mediaStorageSOPClassUID() ++ mediaStorageSOPInstanceUID() ++ transferSyntaxUID()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(validateContextFlow(contexts))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectDicomError()
  }

  "The deflate flow" should "recreate the dicom parts of a dataset which has been deflated and inflated again" in {
    val bytes = fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ personNameJohnDoe() ++ studyDate()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(
        modifyFlow(modifications =
          Seq(
            TagModification.equals(
              TagPath.fromTag(Tag.FileMetaInformationGroupLength),
              _ => fmiGroupLength(transferSyntaxUID(UID.DeflatedExplicitVRLittleEndian)).drop(8)
            ),
            TagModification
              .equals(TagPath.fromTag(Tag.TransferSyntaxUID), _ => ByteString(UID.DeflatedExplicitVRLittleEndian))
          )
        )
      )
      .via(deflateDatasetFlow)
      .map(_.bytes)
      .via(parseFlow)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "not deflate meta information" in {
    val bytes = fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ personNameJohnDoe() ++ studyDate()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(
        modifyFlow(modifications =
          Seq(
            TagModification.equals(
              TagPath.fromTag(Tag.FileMetaInformationGroupLength),
              _ => fmiGroupLength(transferSyntaxUID(UID.DeflatedExplicitVRLittleEndian)).drop(8)
            ),
            TagModification
              .equals(TagPath.fromTag(Tag.TransferSyntaxUID), _ => ByteString(UID.DeflatedExplicitVRLittleEndian))
          )
        )
      )
      .via(deflateDatasetFlow)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectDeflatedChunk()
  }

  it should "not deflate data with non-deflated transfer syntax" in {
    val bytes = fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ personNameJohnDoe() ++ studyDate()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(deflateDatasetFlow)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "not ouput bytes when the stream is empty" in {
    val bytes = ByteString.empty

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(deflateDatasetFlow)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectDicomComplete()
  }

  "The bulk data filter flow" should "remove pixel data" in {
    val bytes =
      preamble ++ fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ personNameJohnDoe() ++ pixelData(1000)

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(bulkDataFilter)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectPreamble()
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "not remove pixel data in sequences" in {
    val bytes = sequence(Tag.DerivationCodeSequence) ++ item() ++ personNameJohnDoe() ++ pixelData(
      100
    ) ++ itemDelimitation() ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(bulkDataFilter)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem(1)
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectHeader(Tag.PixelData)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "only remove waveform data when inside waveform sequence" in {
    val bytes = waveformSeqStart() ++ item() ++ personNameJohnDoe() ++ waveformData(
      100
    ) ++ itemDelimitation() ++ sequenceDelimitation() ++ personNameJohnDoe() ++ waveformData(100)

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(bulkDataFilter)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.WaveformSequence)
      .expectItem(1)
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectHeader(Tag.WaveformData)
      .expectValueChunk()
      .expectDicomComplete()
  }

  "The FMI group length flow" should "calculate and emit the correct group length attribute" in {
    val correctLength = transferSyntaxUID().length
    val bytes = preamble ++ fmiGroupLength(
      fmiVersion(),
      transferSyntaxUID()
    ) ++ fmiVersion() ++ transferSyntaxUID() ++ personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(blacklistFilter(Set(TagTree.fromTag(Tag.FileMetaInformationVersion)), _ => true))
      .via(fmiGroupLengthFlow)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectPreamble()
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk(intToBytesLE(correctLength))
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "work also in flows with file meta information only" in {
    val correctLength = transferSyntaxUID().length
    val bytes         = preamble ++ transferSyntaxUID() // missing file meta information group length

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(fmiGroupLengthFlow)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectPreamble()
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk(intToBytesLE(correctLength))
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "work in flows without preamble" in {
    val correctLength = transferSyntaxUID().length
    val bytes         = transferSyntaxUID() ++ personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(fmiGroupLengthFlow)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk(intToBytesLE(correctLength))
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "not emit anything in empty flows" in {
    val bytes = ByteString.empty

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(fmiGroupLengthFlow)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectDicomComplete()
  }

  it should "not emit a group length attribute when there is no FMI" in {
    val bytes = preamble ++ personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(fmiGroupLengthFlow)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectPreamble()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "keep a zero length group length attribute" in {
    val bytes = fmiGroupLength(ByteString.empty) ++ personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(fmiGroupLengthFlow)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk(ByteString(0, 0, 0, 0))
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "ignore DICOM parts of unknown type" in {
    case object SomePart extends MetaPart

    val correctLength = transferSyntaxUID().length
    val bytes         = preamble ++ transferSyntaxUID() // missing file meta information group length

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .prepend(Source.single(SomePart))
      .via(fmiGroupLengthFlow)

    source
      .runWith(TestSink.probe[DicomPart])
      .request(1)
      .expectNextChainingPF {
        case SomePart => true
      }
      .expectPreamble()
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk(intToBytesLE(correctLength))
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectDicomComplete()
  }

  "The sequence length filter" should "replace determinate length sequences and items with indeterminate, and insert delimitations" in {
    val bytes =
      sequence(Tag.DerivationCodeSequence, 56) ++ item(
        16
      ) ++ studyDate() ++ item() ++ studyDate() ++ itemDelimitation() ++
        sequence(Tag.AbstractPriorCodeSequence) ++ item() ++ studyDate() ++ itemDelimitation() ++ item(
        16
      ) ++ studyDate() ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(toIndeterminateLengthSequences)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence, -1)
      .expectItem(1, -1)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation() // inserted
      .expectItem(2)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation() // inserted
      .expectSequence(Tag.AbstractPriorCodeSequence, -1)
      .expectItem(1, -1)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectItem(2)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation() // inserted
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "handle sequences that end with an item delimitation" in {
    val bytes =
      sequence(Tag.DerivationCodeSequence, 32) ++ item() ++ studyDate() ++ itemDelimitation()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(toIndeterminateLengthSequences)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence, -1)
      .expectItem(1, -1)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "should not remove length from items in fragments" in {
    val bytes =
      pixeDataFragments() ++ item(4) ++ ByteString(1, 2, 3, 4) ++ sequenceDelimitation() ++
        sequence(Tag.DerivationCodeSequence, 40) ++ item(32) ++
        pixeDataFragments() ++ item(4) ++ ByteString(1, 2, 3, 4) ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(toIndeterminateLengthSequences)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectFragments()
      .expectItem(1, 4)
      .expectValueChunk()
      .expectFragmentsDelimitation()
      .expectSequence(Tag.DerivationCodeSequence, -1)
      .expectItem(1, -1)
      .expectFragments()
      .expectItem(1, 4)
      .expectValueChunk()
      .expectFragmentsDelimitation()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "work in datasets with nested sequences" in {
    val bytes = studyDate() ++ sequence(Tag.DerivationCodeSequence, 60) ++ item(52) ++ studyDate() ++
      sequence(Tag.DerivationCodeSequence, 24) ++ item(16) ++ studyDate() ++ personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(toIndeterminateLengthSequences)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectSequence(Tag.DerivationCodeSequence, -1)
      .expectItem(1, -1)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectSequence(Tag.DerivationCodeSequence, -1)
      .expectItem(1, -1)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "handle empty sequences and items" in {
    val bytes =
      sequence(Tag.DerivationCodeSequence, 52) ++ item(16) ++ studyDate() ++ item(0) ++ item(12) ++ sequence(
        Tag.DerivationCodeSequence,
        0
      )

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(toIndeterminateLengthSequences)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence, -1)
      .expectItem(1, -1)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectItem(2, -1)
      .expectItemDelimitation()
      .expectItem(3, -1)
      .expectSequence(Tag.DerivationCodeSequence, -1)
      .expectSequenceDelimitation()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  "The utf8 flow" should "transform a japanese patient name encoded with multiple character sets to valid utf8" in {
    val specificCharacterSet = tagToBytesLE(Tag.SpecificCharacterSet) ++ ByteString("CS") ++
      shortToBytesLE(0x001e.toShort) ++ padToEvenLength(ByteString("ISO 2022 IR 13\\ISO 2022 IR 87"), VR.CS)
    val patientName = tagToBytesLE(0x00100010) ++ ByteString("PN") ++ shortToBytesLE(0x0038) ++
      padToEvenLength(
        ByteString(0xd4, 0xcf, 0xc0, 0xde, 0x5e, 0xc0, 0xdb, 0xb3, 0x3d, 0x1b, 0x24, 0x42, 0x3b, 0x33, 0x45, 0x44, 0x1b,
          0x28, 0x4a, 0x5e, 0x1b, 0x24, 0x42, 0x42, 0x40, 0x4f, 0x3a, 0x1b, 0x28, 0x4a, 0x3d, 0x1b, 0x24, 0x42, 0x24,
          0x64, 0x24, 0x5e, 0x24, 0x40, 0x1b, 0x28, 0x4a, 0x5e, 0x1b, 0x24, 0x42, 0x24, 0x3f, 0x24, 0x6d, 0x24, 0x26,
          0x1b, 0x28, 0x4a),
        VR.PN
      )

    val bytes = specificCharacterSet ++ patientName

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(toUtf8Flow)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.SpecificCharacterSet)
      .expectValueChunk()
      .expectHeader(Tag.PatientName)
      .expectValueChunk(ByteString("ﾔﾏﾀﾞ^ﾀﾛｳ=山田^太郎=やまだ^たろう"))
      .expectDicomComplete()
  }

  it should "set specific character set to ISO_IR 192 (UTF-8)" in {
    val specificCharacterSet = tagToBytesLE(Tag.SpecificCharacterSet) ++ ByteString("CS") ++
      shortToBytesLE(0x001e.toShort) ++ padToEvenLength(ByteString("ISO 2022 IR 13\\ISO 2022 IR 87"), VR.CS)

    val bytes = specificCharacterSet

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(toUtf8Flow)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.SpecificCharacterSet)
      .expectValueChunk(ByteString("ISO_IR 192"))
      .expectDicomComplete()
  }

  it should "transform data without the specific character set attribute, decoding using the default character set" in {
    val bytes = personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(toUtf8Flow)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.SpecificCharacterSet)
      .expectValueChunk(ByteString("ISO_IR 192"))
      .expectHeader(Tag.PatientName)
      .expectValueChunk(personNameJohnDoe().drop(8))
      .expectDicomComplete()
  }

  it should "transform data contained in sequences" in {
    val specificCharacterSet = tagToBytesLE(Tag.SpecificCharacterSet) ++ ByteString("CS") ++
      shortToBytesLE(0x001e.toShort) ++ padToEvenLength(ByteString("ISO 2022 IR 13\\ISO 2022 IR 87"), VR.CS)
    val patientName = tagToBytesLE(0x00100010) ++ ByteString("PN") ++ shortToBytesLE(0x0004) ++ padToEvenLength(
      ByteString(0xd4, 0xcf, 0xc0, 0xde),
      VR.PN
    )

    val bytes = specificCharacterSet ++ sequence(
      Tag.DerivationCodeSequence
    ) ++ item() ++ patientName ++ itemDelimitation() ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(toUtf8Flow)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.SpecificCharacterSet)
      .expectValueChunk()
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem(1)
      .expectHeader(Tag.PatientName)
      .expectValueChunk(ByteString("ﾔﾏﾀﾞ"))
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "not transform data with VR that doesn't support non-default encodings" in {
    val specificCharacterSet = tagToBytesLE(Tag.SpecificCharacterSet) ++ ByteString("CS") ++
      shortToBytesLE(0x001e.toShort) ++ padToEvenLength(ByteString("ISO 2022 IR 13\\ISO 2022 IR 87"), VR.CS)
    val patientNameCS = tagToBytesLE(0x00100010) ++ ByteString("CS") ++ shortToBytesLE(0x0004) ++ padToEvenLength(
      ByteString(0xd4, 0xcf, 0xc0, 0xde),
      VR.PN
    )

    val bytes = specificCharacterSet ++ patientNameCS

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(toUtf8Flow)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.SpecificCharacterSet)
      .expectValueChunk()
      .expectHeader(Tag.PatientName)
      .expectValueChunk(ByteString(0xd4, 0xcf, 0xc0, 0xde))
      .expectDicomComplete()
  }

  it should "not change a file already encoded with ISO_IR 192 (UTF-8)" in {
    val specificCharacterSet =
      tagToBytesLE(Tag.SpecificCharacterSet) ++ ByteString("CS") ++ shortToBytesLE(0x000a.toShort) ++ ByteString(
        "ISO_IR 192"
      )
    val patientName =
      tagToBytesLE(Tag.PatientName) ++ ByteString("PN") ++ shortToBytesLE(0x000c.toShort) ++ ByteString("ABC^ÅÖ^ﾔ")
    val bytes = specificCharacterSet ++ patientName

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(toUtf8Flow)
      .map(_.bytes)
      .reduce(_ ++ _)

    source
      .runWith(TestSink.probe[ByteString])
      .request(1)
      .expectNextChainingPF { case newBytes => newBytes shouldBe bytes }
      .expectComplete()
  }

  it should "leave and empty element empty" in {
    val bytes = emptyPatientName()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(toUtf8Flow)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.SpecificCharacterSet)
      .expectValueChunk()
      .expectHeader(Tag.PatientName)
      .expectDicomComplete()
  }

  "The explicit VR little endian flow" should "convert explicit VR big endian to explicit VR little endian" in {
    val bigEndian = true
    val bytes = preamble ++ fmiGroupLength(transferSyntaxUID(UID.ExplicitVRBigEndianRetired)) ++ transferSyntaxUID(
      UID.ExplicitVRBigEndianRetired
    ) ++
      personNameJohnDoe(bigEndian) ++ rows(bigEndian) ++ dataPointRows(bigEndian) ++ apexPosition(bigEndian) ++
      sequence(Tag.DerivationCodeSequence, bigEndian) ++ item(bigEndian) ++ studyDate(bigEndian) ++
      itemDelimitation(bigEndian) ++ sequenceDelimitation(bigEndian) ++ pixeDataFragments(bigEndian) ++ item(
      1000,
      bigEndian
    ) ++
      (1 to 500).map(_.toShort).map(shortToBytesBE).reduce(_ ++ _) ++ sequenceDelimitation(bigEndian)

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(toExplicitVrLittleEndianFlow)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectPreamble()
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk(transferSyntaxUID().drop(8))
      .expectHeader(Tag.PatientName)
      .expectValueChunk(personNameJohnDoe().drop(8))
      .expectHeader(Tag.Rows)
      .expectValueChunk(rows().drop(8))
      .expectHeader(Tag.DataPointRows)
      .expectValueChunk(dataPointRows().drop(8))
      .expectHeader(Tag.ApexPosition)
      .expectValueChunk(apexPosition().drop(8))
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem(1)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk(studyDate().drop(8))
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectFragments()
      .expectItem(1, 1000)
      .expectValueChunk((1 to 500).map(_.toShort).map(shortToBytesLE).reduce(_ ++ _))
      .expectFragmentsDelimitation()
      .expectDicomComplete()
  }

  it should "convert implicit VR little endian to explicit VR little endian" in {
    val bytes = personNameJohnDoe(explicitVR = false)

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(toExplicitVrLittleEndianFlow)
      .map(_.bytes)
      .reduce(_ ++ _)

    source
      .runWith(TestSink.probe[ByteString])
      .request(1)
      .expectNextChainingPF { case newBytes: ByteString => newBytes shouldBe personNameJohnDoe() }
      .expectComplete()
  }

  it should "not change a file already encoded with explicit VR little endian" in {
    val bigEndian = false
    val bytes =
      preamble ++ fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ personNameJohnDoe(bigEndian) ++
        rows(bigEndian) ++ dataPointRows(bigEndian) ++ apexPosition(bigEndian) ++
        sequence(Tag.DerivationCodeSequence, bigEndian) ++ item(bigEndian) ++ studyDate(bigEndian) ++
        itemDelimitation(bigEndian) ++ sequenceDelimitation(bigEndian) ++ pixeDataFragments(bigEndian) ++ item(
        1000,
        bigEndian
      ) ++
        (1 to 500).map(_.toShort).map(shortToBytesLE).reduce(_ ++ _) ++ sequenceDelimitation(bigEndian)

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(toExplicitVrLittleEndianFlow)
      .map(_.bytes)
      .reduce(_ ++ _)

    source
      .runWith(TestSink.probe[ByteString])
      .request(1)
      .expectNextChainingPF { case newBytes: ByteString => newBytes shouldBe bytes }
      .expectComplete()
  }

  it should "leave and empty element empty" in {
    val bytes = emptyPatientName(bigEndian = true)

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(toExplicitVrLittleEndianFlow)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.PatientName)
      .expectDicomComplete()
  }

  it should "update encoding also in FMI" in {
    val bytes = preamble ++ fmiGroupLengthImplicit(transferSyntaxUID(explicitVR = false)) ++
      transferSyntaxUID(explicitVR = false) ++ personNameJohnDoe()
    val expectedBytes = preamble ++ fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ personNameJohnDoe()

    val actualBytes = Await.result(
      Source
        .single(bytes)
        .via(parseFlow)
        .via(toExplicitVrLittleEndianFlow)
        .map(_.bytes)
        .runWith(Sink.fold(ByteString.empty)(_ ++ _)),
      5.seconds
    )

    actualBytes shouldBe expectedBytes
  }

  "The even value length flow" should "pad odd length attributes" in {

    def odd(tag: Int, value: String): ByteString =
      ValueElement(tag, Lookup.vrOf(tag), Value(ByteString(value)), bigEndian = false, explicitVR = true).toBytes

    val mediaSopUidOdd =
      odd(Tag.MediaStorageSOPInstanceUID, "1.2.276.0.7230010.3.1.4.1536491920.17152.1480884676.735")
    val mediaSopUid =
      element(Tag.MediaStorageSOPInstanceUID, "1.2.276.0.7230010.3.1.4.1536491920.17152.1480884676.735")
    val sopUidOdd     = odd(Tag.SOPInstanceUID, "1.2.276.0.7230010.3.1.4.1536491920.17152.1480884676.735")
    val sopUid        = element(Tag.SOPInstanceUID, "1.2.276.0.7230010.3.1.4.1536491920.17152.1480884676.735")
    val personNameOdd = odd(Tag.PatientName, "Jane^Mary")
    val personName    = element(Tag.PatientName, "Jane^Mary")

    val bytes = fmiGroupLength(mediaSopUidOdd) ++ mediaSopUidOdd ++ sopUidOdd ++
      sequence(Tag.DerivationCodeSequence, 25) ++ item(17) ++ personNameOdd ++
      pixeDataFragments() ++ item(3) ++ ByteString(1, 2, 3) ++ sequenceDelimitation()
    val expectedBytes = fmiGroupLength(mediaSopUid) ++ mediaSopUid ++ sopUid ++
      sequence(Tag.DerivationCodeSequence) ++ item() ++ personName ++ itemDelimitation() ++ sequenceDelimitation() ++
      pixeDataFragments() ++ item(4) ++ ByteString(1, 2, 3, 0) ++ sequenceDelimitation()

    val actualBytes = Await.result(
      Source
        .single(bytes)
        .via(parseFlow)
        .via(toIndeterminateLengthSequences)
        .via(toEvenValueLengthFlow)
        .map(_.bytes)
        .runWith(Sink.fold(ByteString.empty)(_ ++ _)),
      5.seconds
    )

    actualBytes shouldBe expectedBytes
  }
}
