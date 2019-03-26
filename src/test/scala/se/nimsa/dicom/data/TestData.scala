package se.nimsa.dicom.data

import akka.util.ByteString
import se.nimsa.dicom.data.Elements.ValueElement

object TestData {

  val preamble: ByteString = ByteString.fromArray(new Array[Byte](128)) ++ ByteString("DICM")

  def element(tag: Int, value: String, bigEndian: Boolean = false, explicitVR: Boolean = true): ByteString = ValueElement(tag, Dictionary.vrOf(tag), Value.fromString(Dictionary.vrOf(tag), value, bigEndian), bigEndian, explicitVR).toBytes
  def element(tag: Int, value: ByteString, bigEndian: Boolean, explicitVR: Boolean): ByteString = ValueElement(tag, Dictionary.vrOf(tag), Value(value), bigEndian, explicitVR).toBytes

  def fmiGroupLength(fmis: ByteString*): ByteString = element(Tag.FileMetaInformationGroupLength, intToBytesLE(fmis.map(fmi => fmi.length + (fmi.length % 2)).sum), bigEndian = false, explicitVR = true)

  // File Meta Information Version
  def fmiVersion(bigEndian: Boolean = false, explicitVR: Boolean = true): ByteString = element(Tag.FileMetaInformationVersion, ByteString(0x00, 0x01), bigEndian, explicitVR)
  // (not conforming to standard)
  def fmiVersionImplicit(bigEndian: Boolean = false, explicitVR: Boolean = true): ByteString = element(Tag.FileMetaInformationVersion, ByteString(0x00, 0x01), bigEndian, explicitVR)

  def mediaStorageSOPClassUID(bigEndian: Boolean = false, explicitVR: Boolean = true): ByteString = element(Tag.MediaStorageSOPClassUID, UID.CTImageStorage, bigEndian, explicitVR)
  def sopClassUID(bigEndian: Boolean = false, explicitVR: Boolean = true): ByteString = element(Tag.SOPClassUID, UID.CTImageStorage, bigEndian, explicitVR)

  def instanceCreatorUID(bigEndian: Boolean = false, explicitVR: Boolean = true): ByteString = element(Tag.InstanceCreatorUID, "1.2.840.113619.6.184", bigEndian, explicitVR)

  def mediaStorageSOPInstanceUID(bigEndian: Boolean = false, explicitVR: Boolean = true): ByteString = element(Tag.MediaStorageSOPInstanceUID, "1.2.276.0.7230010.3.1.4.1536491920.17152.1480884676.735", bigEndian, explicitVR)

  // Transfer Syntax UIDs
  def transferSyntaxUID(uid: String = UID.ExplicitVRLittleEndian, bigEndian: Boolean = false, explicitVR: Boolean = true): ByteString = element(Tag.TransferSyntaxUID, uid, bigEndian, explicitVR)

  def groupLength(groupNumber: Short, length: Int, bigEndian: Boolean = false, explicitVR: Boolean = true): ByteString = shortToBytes(groupNumber, bigEndian) ++ ByteString(0, 0) ++ (if (explicitVR) ByteString("UL") ++ shortToBytes(4, bigEndian) else intToBytes(4, bigEndian)) ++ intToBytes(length, bigEndian)

  def characterSetsJis(bigEndian: Boolean = false, explicitVR: Boolean = true): ByteString = element(Tag.SpecificCharacterSet, "ISO 2022 IR 13\\ISO 2022 IR 87", bigEndian, explicitVR)

  def patientNameJohnDoe(bigEndian: Boolean = false, explicitVR: Boolean = true): ByteString = element(Tag.PatientName, "John^Doe", bigEndian, explicitVR)
  def emptyPatientName(bigEndian: Boolean = false, explicitVR: Boolean = true): ByteString = element(Tag.PatientName, "", bigEndian, explicitVR)

  def patientID(bigEndian: Boolean = false, explicitVR: Boolean = true): ByteString = element(Tag.PatientID, "12345678", bigEndian, explicitVR)

  def studyDate(bigEndian: Boolean = false, explicitVR: Boolean = true): ByteString = element(Tag.StudyDate, "19700101", bigEndian, explicitVR)

  def rows(bigEndian: Boolean = false, explicitVR: Boolean = true): ByteString = element(Tag.Rows, shortToBytes(512, bigEndian), bigEndian, explicitVR)
  def dataPointRows(bigEndian: Boolean = false, explicitVR: Boolean = true): ByteString = element(Tag.DataPointRows, intToBytes(1234, bigEndian), bigEndian, explicitVR)
  def apexPosition(bigEndian: Boolean = false, explicitVR: Boolean = true): ByteString = element(Tag.ApexPosition, doubleToBytes(math.Pi, bigEndian), bigEndian, explicitVR)

  def sequenceEndNonZeroLength(bigEndian: Boolean = false): ByteString = tagToBytes(Tag.SequenceDelimitationItem, bigEndian) ++ intToBytes(0x00000010, bigEndian)
  def pixeDataFragments(bigEndian: Boolean = false): ByteString = tagToBytes(Tag.PixelData, bigEndian) ++ ByteString('O', 'W', 0, 0) ++ ByteString(0xFF, 0xFF, 0xFF, 0xFF)

  def sequence(tag: Int, bigEndian: Boolean = false, explicitVR: Boolean = true): ByteString = sequence(tag, indeterminateLength, bigEndian, explicitVR)
  def sequence(tag: Int, length: Int): ByteString = sequence(tag, length, bigEndian = false, explicitVR = true)
  def sequence(tag: Int, length: Int, bigEndian: Boolean, explicitVR: Boolean): ByteString = tagToBytes(tag, bigEndian) ++ (if (explicitVR) ByteString('S', 'Q', 0, 0) else ByteString.empty) ++ intToBytes(length, bigEndian)

  def waveformSeqStart(bigEndian: Boolean = false, explicitVR: Boolean = true): ByteString = sequence(Tag.WaveformSequence, bigEndian)

  def pixelData(length: Int, bigEndian: Boolean = false, explicitVR: Boolean = true): ByteString = element(Tag.PixelData, ByteString(new Array[Byte](length)), bigEndian, explicitVR)
  def waveformData(length: Int, bigEndian: Boolean = false, explicitVR: Boolean = true): ByteString = element(Tag.WaveformData , ByteString(new Array[Byte](length)), bigEndian, explicitVR)

}
