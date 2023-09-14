package com.exini.dicom.data

import com.exini.dicom.data.ByteParser.ByteReader

object Parsing {

  final val dicomPreambleLength = 132

  case class HeaderInfo(tag: Int, bigEndian: Boolean, explicitVR: Boolean, isFmi: Boolean)

  case class AttributeInfo(tag: Int, vr: VR, headerLength: Int, valueLength: Long)

  trait ParseState {
    val maySwitchTs: Boolean = false
    val bigEndian: Boolean
    val explicitVR: Boolean
  }

  def isSpecial(tag: Int): Boolean = tag == 0xfffee000 || tag == 0xfffee00d || tag == 0xfffee0dd

  def tagVr(data: Array[Byte], bigEndian: Boolean, explicitVr: Boolean): (Int, VR) = {
    val tag = bytesToTag(data, bigEndian)
    if (isSpecial(tag))
      (tag, null)
    else if (explicitVr) {
      val vr = VR.withValue(bytesToVR(data.drop(4))).orNull
      (tag, vr)
    } else
      (tag, Lookup.vrOf(tag))
  }

  def tryReadHeader(data: Array[Byte]): Option[HeaderInfo] =
    tryReadHeader(data, assumeBigEndian = false)
      .orElse(tryReadHeader(data, assumeBigEndian = true))

  def tryReadHeader(data: Array[Byte], assumeBigEndian: Boolean): Option[HeaderInfo] = {
    val (tag, vr) = tagVr(data, assumeBigEndian, explicitVr = false)
    if (vr == VR.UN)
      None
    else if (bytesToVR(data.drop(4)) == vr.value)
      Some(HeaderInfo(tag, bigEndian = assumeBigEndian, explicitVR = true, isFmi = isFileMetaInformation(tag)))
    else if (intToUnsignedLong(bytesToInt(data.drop(4), assumeBigEndian)) >= 0)
      if (assumeBigEndian)
        throw new ParseException("Implicit VR big endian encoded DICOM data")
      else
        Some(HeaderInfo(tag, bigEndian = false, explicitVR = false, isFmi = isFileMetaInformation(tag)))
    else
      None
  }

  def readHeader(reader: ByteReader, state: ParseState): AttributeInfo = {
    reader.ensure(8)
    val tagVrBytes = reader.remainingData.take(8)
    val (tag, vr)  = tagVr(tagVrBytes, state.bigEndian, state.explicitVR)
    if (vr == null)
      AttributeInfo(tag, vr, 8, lengthToLong(bytesToInt(tagVrBytes.drop(4), state.bigEndian)))
    else if (state.explicitVR)
      if (vr.headerLength == 8)
        AttributeInfo(tag, vr, 8, lengthToLong(bytesToUShort(tagVrBytes.drop(6), state.bigEndian)))
      else {
        reader.ensure(12)
        AttributeInfo(tag, vr, 12, lengthToLong(bytesToInt(reader.remainingData.drop(8), state.bigEndian)))
      }
    else
      AttributeInfo(tag, vr, 8, lengthToLong(bytesToInt(tagVrBytes.drop(4), state.bigEndian)))
  }

  def isPreamble(data: Array[Byte]): Boolean =
    data.length >= dicomPreambleLength && data
      .slice(dicomPreambleLength - 4, dicomPreambleLength)
      .sameElements(magicBytes)

  def isDeflated(transferSyntaxUid: String): Boolean =
    transferSyntaxUid == UID.DeflatedExplicitVRLittleEndian || transferSyntaxUid == UID.JPIPReferencedDeflate

  def hasZLIBHeader(firstTwoBytes: Array[Byte]): Boolean = bytesToUShortBE(firstTwoBytes) == 0x789c
}
