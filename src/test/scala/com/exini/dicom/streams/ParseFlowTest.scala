package com.exini.dicom.streams

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestKit
import akka.util.ByteString
import com.exini.dicom.data.DicomElements.ValueElement
import com.exini.dicom.data.{ Tag, UID, VR, _ }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContextExecutor

class ParseFlowTest
    extends TestKit(ActorSystem("ParseFlowSpec"))
    with AnyFlatSpecLike
    with Matchers
    with BeforeAndAfterAll {

  import TestUtils._
  import com.exini.dicom.data.DicomParts._
  import com.exini.dicom.data.TestData._

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  override def afterAll(): Unit = system.terminate()

  "The parse flow" should "produce a preamble, FMI tags and dataset tags for a complete DICOM file" in {
    val bytes = preamble ++ fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(ParseFlow())

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

  it should "read files without preamble but with FMI" in {
    val bytes = fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "read a file with only FMI" in {
    val bytes = preamble ++ fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID()

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectPreamble()
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "read a file with neither FMI nor preamble" in {
    val bytes = personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "not output value chunks when value length is zero" in {
    val bytes = ByteString(8, 0, 32, 0, 68, 65, 0, 0) ++ ByteString(16, 0, 16, 0, 80, 78, 0, 0)
    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.StudyDate)
      .expectHeader(Tag.PatientName)
      .expectDicomComplete()
  }

  it should "output a warning message when file meta information group length is too long" in {
    val bytes = fmiGroupLength(transferSyntaxUID(), studyDate()) ++ transferSyntaxUID() ++ studyDate()

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "output a warning message when file meta information group length is too short" in {
    val bytes =
      fmiGroupLength(mediaStorageSOPInstanceUID()) ++ mediaStorageSOPInstanceUID() ++ transferSyntaxUID() ++ studyDate()

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.MediaStorageSOPInstanceUID)
      .expectValueChunk()
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "treat a preamble alone as a valid DICOM file" in {
    val bytes = preamble

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectPreamble()
      .expectDicomComplete()
  }

  it should "skip very long (and obviously erroneous) transfer syntaxes (see warning log message)" in {
    val malformedTsuid =
      transferSyntaxUID().take(6) ++ ByteString(20, 8) ++ transferSyntaxUID().takeRight(20) ++ ByteString.fromArray(
        new Array[Byte](2048)
      )
    val bytes = fmiGroupLength(malformedTsuid) ++ malformedTsuid ++ personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "fail reading a truncated DICOM file" in {
    val bytes = fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ personNameJohnDoe().dropRight(2)

    val source = Source
      .single(bytes)
      .via(ParseFlow(inflate = false))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectHeader(Tag.PatientName)
      .expectDicomError()
  }

  it should "inflate deflated datasets" in {
    val bytes = fmiGroupLength(transferSyntaxUID(UID.DeflatedExplicitVRLittleEndian)) ++ transferSyntaxUID(
      UID.DeflatedExplicitVRLittleEndian
    ) ++ deflate(personNameJohnDoe() ++ studyDate())

    val source = Source
      .single(bytes)
      .via(ParseFlow())

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

  it should "inflate gzip deflated datasets (with warning message)" in {
    val bytes = fmiGroupLength(transferSyntaxUID(UID.DeflatedExplicitVRLittleEndian)) ++ transferSyntaxUID(
      UID.DeflatedExplicitVRLittleEndian
    ) ++ deflate(personNameJohnDoe() ++ studyDate(), gzip = true)

    val source = Source
      .single(bytes)
      .via(ParseFlow())

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

  it should "pass through deflated data when asked not to inflate" in {
    val bytes = fmiGroupLength(transferSyntaxUID(UID.DeflatedExplicitVRLittleEndian)) ++ transferSyntaxUID(
      UID.DeflatedExplicitVRLittleEndian
    ) ++ deflate(personNameJohnDoe() ++ studyDate())

    val source = Source
      .single(bytes)
      .via(ParseFlow(inflate = false))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectDeflatedChunk()
      .expectDicomComplete()
  }

  it should "read DICOM data with fragments" in {
    val bytes = pixeDataFragments() ++ item(4) ++ ByteString(1, 2, 3, 4) ++ item(4) ++
      ByteString(5, 6, 7, 8) ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectFragments()
      .expectFragment(4)
      .expectValueChunk()
      .expectFragment(4)
      .expectValueChunk()
      .expectFragmentsDelimitation()
      .expectDicomComplete()
  }

  it should "issue a warning when a fragments delimitation tag has nonzero length" in {
    val bytes = pixeDataFragments() ++ item(4) ++ ByteString(1, 2, 3, 4) ++ item(4) ++
      ByteString(5, 6, 7, 8) ++ sequenceEndNonZeroLength()

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectFragments()
      .expectFragment(4)
      .expectValueChunk()
      .expectFragment(4)
      .expectValueChunk()
      .expectFragmentsDelimitation()
      .expectDicomComplete()
  }

  it should "parse a tag which is not an item, item data nor fragments delimitation inside fragments as unknown" in {
    val bytes = pixeDataFragments() ++ item(4) ++ ByteString(1, 2, 3, 4) ++ studyDate() ++ item(4) ++
      ByteString(5, 6, 7, 8) ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectFragments()
      .expectFragment(4)
      .expectValueChunk()
      .expectUnknownPart()
      .expectFragment(4)
      .expectValueChunk()
      .expectFragmentsDelimitation()
      .expectDicomComplete()
  }

  it should "read DICOM data containing a sequence" in {
    val bytes = sequence(
      Tag.DerivationCodeSequence
    ) ++ item() ++ personNameJohnDoe() ++ studyDate() ++ itemDelimitation() ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "read DICOM data containing a sequence in a sequence" in {
    val bytes = sequence(Tag.DerivationCodeSequence) ++ item() ++ sequence(
      Tag.DerivationCodeSequence
    ) ++ item() ++ personNameJohnDoe() ++ itemDelimitation() ++ sequenceDelimitation() ++ studyDate() ++ itemDelimitation() ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem()
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "read a valid DICOM file correctly when data chunks are very small" in {
    val bytes = preamble ++ fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(new Chunker(chunkSize = 1))
      .via(ParseFlow())

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

  it should "not accept a non-DICOM file" in {
    val bytes = ByteString(1, 2, 3, 4, 5, 6, 7, 8, 9)

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectDicomError()
  }

  it should "read DICOM files with explicit VR big-endian transfer syntax" in {
    val bytes = preamble ++ fmiGroupLength(transferSyntaxUID(UID.ExplicitVRBigEndianRetired)) ++ transferSyntaxUID(
      UID.ExplicitVRBigEndianRetired
    ) ++ personNameJohnDoe(bigEndian = true)

    val source = Source
      .single(bytes)
      .via(ParseFlow())

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

  it should "read DICOM files with implicit VR little endian transfer syntax" in {
    val bytes = preamble ++ fmiGroupLength(transferSyntaxUID(UID.ImplicitVRLittleEndian)) ++ transferSyntaxUID(
      UID.ImplicitVRLittleEndian
    ) ++ personNameJohnDoe(explicitVR = false)

    val source = Source
      .single(bytes)
      .via(ParseFlow())

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

  it should "chunk value data according to max chunk size" in {
    val bytes = preamble ++ fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(ParseFlow(chunkSize = 5))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectPreamble()
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectValueChunk()
      .expectValueChunk()
      .expectValueChunk()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "chunk deflated data according to max chunk size" in {
    val bytes = fmiGroupLength(transferSyntaxUID(UID.DeflatedExplicitVRLittleEndian)) ++ transferSyntaxUID(
      UID.DeflatedExplicitVRLittleEndian
    ) ++ deflate(personNameJohnDoe() ++ studyDate())

    val source = Source
      .single(bytes)
      .via(ParseFlow(chunkSize = 25, inflate = false))

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectDeflatedChunk()
      .expectDeflatedChunk()
      .expectDicomComplete()
  }

  it should "accept meta information encoded with implicit VR" in {
    val bytes =
      preamble ++ fmiGroupLengthImplicit(transferSyntaxUID(explicitVR = false)) ++
        transferSyntaxUID(explicitVR = false) ++ personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(ParseFlow())

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

  it should "handle values with length larger than the signed int range" in {
    val length = Int.MaxValue.toLong + 1
    val bytes  = ByteString(0xe0, 0x7f, 0x10, 0x00, 0x4f, 0x57, 0, 0) ++ intToBytes(length.toInt)

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.PixelData, VR.OW, length)
      .expectValueChunk(ByteString.empty)
      .expectDicomComplete()
  }

  it should "handle sequences and items of determinate length" in {
    val bytes = studyDate() ++ (sequence(Tag.DerivationCodeSequence, 8 + 16 + 16) ++ item(
      16 + 16
    ) ++ studyDate() ++ personNameJohnDoe()) ++ personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem()
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "handle fragments with empty basic offset table (first item)" in {
    val bytes = pixeDataFragments() ++ item(0) ++ item(4) ++ ByteString(1, 2, 3, 4) ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectFragments()
      .expectFragment(0)
      .expectFragment(4)
      .expectValueChunk(4)
      .expectFragmentsDelimitation()
      .expectDicomComplete()
  }

  it should "parse sequences with VR UN as a block of bytes" in {
    val unSequence = tagToBytes(Tag.CTExposureSequence) ++ ByteString('U', 'N', 0, 0) ++ intToBytes(24)
    val bytes      = personNameJohnDoe() ++ unSequence ++ item(16) ++ studyDate()

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectHeader(Tag.CTExposureSequence, VR.UN, 24)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "parse sequences of determinate length with VR UN with contents in implicit VR as a block of bytes" in {
    val unSequence = tagToBytes(Tag.CTExposureSequence) ++ ByteString('U', 'N', 0, 0) ++ intToBytes(24)
    val bytes      = personNameJohnDoe() ++ unSequence ++ item(16) ++ studyDate(explicitVR = false)

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectHeader(Tag.CTExposureSequence, VR.UN, 24)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "handle sequences of indefinite length with VR UN with contents in implicit VR" in {
    // see ftp://medical.nema.org/medical/dicom/final/cp246_ft.pdf for motivation
    val bytes = personNameJohnDoe() ++ cp264Sequence ++ item(60) ++
      element(Tag.CodeValue, "113691", explicitVR = false) ++
      element(Tag.CodingSchemeDesignator, "DCM", explicitVR = false) ++
      element(Tag.CodeMeaning, "IEC Body Dosimetry Phantom", explicitVR = false) ++
      sequenceDelimitation() ++ pixelData(10)

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectSequence(Tag.CTDIPhantomTypeCodeSequence)
      .expectItem(60)
      .expectHeader(Tag.CodeValue, VR.SH, 6)
      .expectValueChunk()
      .expectHeader(Tag.CodingSchemeDesignator, VR.SH, 4)
      .expectValueChunk()
      .expectHeader(Tag.CodeMeaning, VR.LO, 26)
      .expectValueChunk()
      .expectSequenceDelimitation()
      .expectHeader(Tag.PixelData)
      .expectValueChunk(10)
      .expectDicomComplete()
  }

  it should "handle data ending with a CP-246 sequence" in {
    val bytes = personNameJohnDoe() ++ cp264Sequence ++ item() ++ personNameJohnDoe(explicitVR = false) ++
      itemDelimitation() ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectSequence(Tag.CTDIPhantomTypeCodeSequence)
      .expectItem()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "handle a CP-246 sequence followed by a regular sequence" in {
    val bytes = personNameJohnDoe() ++
      cp264Sequence ++ item() ++ personNameJohnDoe(explicitVR =
      false
    ) ++ itemDelimitation() ++ sequenceDelimitation() ++
      sequence(Tag.CollimatorShapeSequence) ++ item() ++ studyDate() ++ itemDelimitation() ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectSequence(Tag.CTDIPhantomTypeCodeSequence)
      .expectItem()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectSequence(Tag.CollimatorShapeSequence)
      .expectItem()
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "handle a CP-246 sequence followed by a private attribute" in {
    val bytes = personNameJohnDoe() ++
      cp264Sequence ++ item() ++ personNameJohnDoe(explicitVR = false) ++ itemDelimitation() ++
      sequenceDelimitation() ++
      ValueElement(0x00990110, VR.SH, Value.fromString(VR.SH, "Value"), bigEndian = false, explicitVR = true).toBytes

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectSequence(Tag.CTDIPhantomTypeCodeSequence)
      .expectItem()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectHeader(0x00990110)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "handle a CP-246 sequence followed by fragments" in {
    val bytes = personNameJohnDoe() ++
      cp264Sequence ++ item() ++ personNameJohnDoe(explicitVR = false) ++ itemDelimitation() ++
      sequenceDelimitation() ++ pixeDataFragments() ++ item(4) ++ ByteString(1, 2, 3, 4)

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectSequence(Tag.CTDIPhantomTypeCodeSequence)
      .expectItem()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectFragments()
      .expectFragment(4)
      .expectValueChunk(4)
      .expectDicomComplete()
  }

  it should "handle nested CP-246 sequences" in {
    val bytes = personNameJohnDoe() ++
      cp264Sequence ++ item() ++ sequence(Tag.DerivationCodeSequence) ++ item() ++ studyDate(explicitVR = false) ++
      itemDelimitation() ++ sequenceDelimitation() ++ itemDelimitation() ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectSequence(Tag.CTDIPhantomTypeCodeSequence)
      .expectItem()
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem()
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "fail parsing if length bytes matches a known VR when checking for transfer syntax switch" in {
    // length of implicit attribute will be encoded as 0x4441 (little endian) which reads as VR 'DA'
    // this is the smallest length that could lead to such problems
    val bytes = personNameJohnDoe() ++ cp264Sequence ++ item() ++
      element(Tag.CodeMeaning, ByteString(new Array[Byte](0x4144)), bigEndian = false, explicitVR = false) ++
      itemDelimitation() ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectSequence(Tag.CTDIPhantomTypeCodeSequence)
      .expectItem()
      .expectHeader(Tag.CodeMeaning, VR.DA, 0)
      .expectUnknownPart()
      .expectUnknownPart()
      .expectUnknownPart()
      .expectUnknownPart()
    // etc for many more unknown parts
  }

  it should "handle odd-length attributes" in {

    def element(tag: Int, value: String): ByteString =
      ValueElement(tag, Lookup.vrOf(tag), Value(ByteString(value)), bigEndian = false, explicitVR = true).toBytes

    val mediaSopUidOdd =
      element(Tag.MediaStorageSOPInstanceUID, "1.2.276.0.7230010.3.1.4.1536491920.17152.1480884676.735")
    val sopUidOdd     = element(Tag.SOPInstanceUID, "1.2.276.0.7230010.3.1.4.1536491920.17152.1480884676.735")
    val personNameOdd = element(Tag.PatientName, "Jane^Mary")
    val bytes = fmiGroupLength(mediaSopUidOdd) ++ mediaSopUidOdd ++ sopUidOdd ++
      sequence(Tag.DerivationCodeSequence, 25) ++ item(17) ++ personNameOdd

    val source = Source
      .single(bytes)
      .via(ParseFlow())

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.FileMetaInformationGroupLength, VR.UL, 4)
      .expectValueChunk(4)
      .expectHeader(Tag.MediaStorageSOPInstanceUID, VR.UI, 55)
      .expectValueChunk(55)
      .expectHeader(Tag.SOPInstanceUID, VR.UI, 55)
      .expectValueChunk(55)
      .expectSequence(Tag.DerivationCodeSequence, 25)
      .expectItem(17)
      .expectHeader(Tag.PatientName, VR.PN, 9)
      .expectValueChunk(9)
      .expectDicomComplete()
  }
}
