package com.exini.dicom.data

import com.exini.dicom.data.DicomElements.ValueElement

object TestData {

  val preamble: Bytes = zeroBytes(128) ++ magicBytes

  def element(tag: Int, value: String, bigEndian: Boolean = false, explicitVR: Boolean = true): Bytes =
    ValueElement(
      tag,
      Lookup.vrOf(tag),
      Value.fromString(Lookup.vrOf(tag), value, bigEndian),
      bigEndian,
      explicitVR
    ).toBytes

  def element(tag: Int, value: Bytes, bigEndian: Boolean, explicitVR: Boolean): Bytes =
    ValueElement(tag, Lookup.vrOf(tag), Value(value), bigEndian, explicitVR).toBytes

  def fmiGroupLength(fmis: Bytes*): Bytes =
    element(
      Tag.FileMetaInformationGroupLength,
      intToBytesLE(fmis.map(fmi => fmi.length + (fmi.length % 2)).sum),
      bigEndian = false,
      explicitVR = true
    )

  def fmiGroupLengthImplicit(fmis: Bytes*): Bytes =
    element(
      Tag.FileMetaInformationGroupLength,
      intToBytesLE(fmis.map(fmi => fmi.length + (fmi.length % 2)).sum),
      bigEndian = false,
      explicitVR = false
    )

  // File Meta Information Version
  def fmiVersion(bigEndian: Boolean = false, explicitVR: Boolean = true): Bytes =
    element(Tag.FileMetaInformationVersion, bytesi(0x00, 0x01), bigEndian, explicitVR)
  // (not conforming to standard)
  def fmiVersionImplicit(bigEndian: Boolean = false, explicitVR: Boolean = true): Bytes =
    element(Tag.FileMetaInformationVersion, bytesi(0x00, 0x01), bigEndian, explicitVR)

  def mediaStorageSOPClassUID(bigEndian: Boolean = false, explicitVR: Boolean = true): Bytes =
    element(Tag.MediaStorageSOPClassUID, UID.CTImageStorage, bigEndian, explicitVR)
  def sopClassUID(bigEndian: Boolean = false, explicitVR: Boolean = true): Bytes =
    element(Tag.SOPClassUID, UID.CTImageStorage, bigEndian, explicitVR)

  def instanceCreatorUID(bigEndian: Boolean = false, explicitVR: Boolean = true): Bytes =
    element(Tag.InstanceCreatorUID, "1.2.840.113619.6.184", bigEndian, explicitVR)

  def mediaStorageSOPInstanceUID(bigEndian: Boolean = false, explicitVR: Boolean = true): Bytes =
    element(
      Tag.MediaStorageSOPInstanceUID,
      "1.2.276.0.7230010.3.1.4.1536491920.17152.1480884676.735",
      bigEndian,
      explicitVR
    )

  // Transfer Syntax UIDs
  def transferSyntaxUID(
      uid: String = UID.ExplicitVRLittleEndian,
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Bytes = element(Tag.TransferSyntaxUID, uid, bigEndian, explicitVR)

  def groupLength(
      groupNumber: Short,
      length: Int,
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Bytes =
    shortToBytes(groupNumber, bigEndian) ++
      bytesi(0, 0) ++
      (if (explicitVR) "UL".utf8Bytes ++ shortToBytes(4, bigEndian) else intToBytes(4, bigEndian)) ++
      intToBytes(length, bigEndian)

  def characterSetsJis(bigEndian: Boolean = false, explicitVR: Boolean = true): Bytes =
    element(Tag.SpecificCharacterSet, "ISO 2022 IR 13\\ISO 2022 IR 87", bigEndian, explicitVR)

  def personNameJohnDoe(bigEndian: Boolean = false, explicitVR: Boolean = true): Bytes =
    element(Tag.PatientName, "John^Doe", bigEndian, explicitVR)
  def emptyPatientName(bigEndian: Boolean = false, explicitVR: Boolean = true): Bytes =
    element(Tag.PatientName, "", bigEndian, explicitVR)

  def patientID(bigEndian: Boolean = false, explicitVR: Boolean = true): Bytes =
    element(Tag.PatientID, "12345678", bigEndian, explicitVR)

  def studyDate(bigEndian: Boolean = false, explicitVR: Boolean = true): Bytes =
    element(Tag.StudyDate, "19700101", bigEndian, explicitVR)

  def rows(bigEndian: Boolean = false, explicitVR: Boolean = true): Bytes =
    element(Tag.Rows, shortToBytes(512, bigEndian), bigEndian, explicitVR)
  def dataPointRows(bigEndian: Boolean = false, explicitVR: Boolean = true): Bytes =
    element(Tag.DataPointRows, intToBytes(1234, bigEndian), bigEndian, explicitVR)
  def apexPosition(bigEndian: Boolean = false, explicitVR: Boolean = true): Bytes =
    element(Tag.ApexPosition, doubleToBytes(math.Pi, bigEndian), bigEndian, explicitVR)

  def sequenceEndNonZeroLength(bigEndian: Boolean = false): Bytes =
    tagToBytes(Tag.SequenceDelimitationItem, bigEndian) ++ intToBytes(0x00000010, bigEndian)
  def pixeDataFragments(bigEndian: Boolean = false): Bytes =
    tagToBytes(Tag.PixelData, bigEndian) ++ bytesb('O', 'W', 0.toByte, 0.toByte) ++ bytesi(0xff, 0xff, 0xff, 0xff)

  def sequence(tag: Int, bigEndian: Boolean = false, explicitVR: Boolean = true): Bytes =
    sequence(tag, indeterminateLength, bigEndian, explicitVR)
  def sequence(tag: Int, length: Int): Bytes = sequence(tag, length, bigEndian = false, explicitVR = true)
  def sequence(tag: Int, length: Int, bigEndian: Boolean, explicitVR: Boolean): Bytes =
    tagToBytes(tag, bigEndian) ++ (if (explicitVR) bytesb('S', 'Q', 0.toByte, 0.toByte)
                                   else emptyBytes) ++ intToBytes(
      length,
      bigEndian
    )
  val cp264Sequence: Bytes =
    tagToBytes(Tag.CTDIPhantomTypeCodeSequence) ++ bytesb('U', 'N') ++ bytesi(0, 0, 0xff, 0xff, 0xff, 0xff)

  def waveformSeqStart(bigEndian: Boolean = false, explicitVR: Boolean = true): Bytes =
    sequence(Tag.WaveformSequence, bigEndian, explicitVR)

  def pixelData(length: Int, bigEndian: Boolean = false, explicitVR: Boolean = true): Bytes =
    element(Tag.PixelData, zeroBytes(length), bigEndian, explicitVR)
  def waveformData(length: Int, bigEndian: Boolean = false, explicitVR: Boolean = true): Bytes =
    element(Tag.WaveformData, zeroBytes(length), bigEndian, explicitVR)

}
