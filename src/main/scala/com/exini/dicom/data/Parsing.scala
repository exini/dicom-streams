package com.exini.dicom.data

import akka.event.LoggingAdapter
import akka.util.ByteString
import com.exini.dicom.data.ByteParser.ByteReader

import scala.util.Try

object Parsing {

  final val dicomPreambleLength = 132

  case class HeaderInfo(bigEndian: Boolean, explicitVR: Boolean, hasFmi: Boolean)

  case class AttributeInfo(tag: Int, vr: VR, headerLength: Int, valueLength: Long)

  trait ParseState {
    val maySwitchTs: Boolean
    val bigEndian: Boolean
    val explicitVR: Boolean
  }

  def isSpecial(tag: Int): Boolean = tag == 0xfffee000 || tag == 0xfffee00d || tag == 0xfffee0dd

  def tagVr(data: ByteString, bigEndian: Boolean, explicitVr: Boolean): (Int, VR) = {
    val tag = bytesToTag(data, bigEndian)
    if (isSpecial(tag))
      (tag, null)
    else if (explicitVr) {
      val vr = Try(VR.withValue(bytesToVR(data.drop(4)))).getOrElse(null)
      (tag, vr)
    } else
      (tag, Lookup.vrOf(tag))
  }

  def tryReadHeader(data: ByteString): Option[HeaderInfo] =
    tryReadHeader(data, assumeBigEndian = false)
      .orElse(tryReadHeader(data, assumeBigEndian = true))

  def tryReadHeader(data: ByteString, assumeBigEndian: Boolean): Option[HeaderInfo] = {
    val (tag, vr) = tagVr(data, assumeBigEndian, explicitVr = false)
    if (vr == VR.UN)
      None
    else if (bytesToVR(data.drop(4)) == vr.value)
      Some(HeaderInfo(bigEndian = assumeBigEndian, explicitVR = true, hasFmi = isFileMetaInformation(tag)))
    else if (intToUnsignedLong(bytesToInt(data.drop(4), assumeBigEndian)) >= 0)
      if (assumeBigEndian)
        throw new DicomParseException("Implicit VR Big Endian encoded DICOM Stream")
      else
        Some(HeaderInfo(bigEndian = false, explicitVR = false, hasFmi = isFileMetaInformation(tag)))
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

  def isPreamble(data: ByteString): Boolean =
    data.length >= dicomPreambleLength && data
      .slice(dicomPreambleLength - 4, dicomPreambleLength) == ByteString('D', 'I', 'C', 'M')

  def warnIfOdd(tag: Int, vr: VR, valueLength: Long, log: LoggingAdapter): Unit =
    if (valueLength % 2 > 0 && valueLength != indeterminateLength && vr != null && vr != VR.SQ)
      log.warning(s"Element ${tagToString(tag)} has odd length")
}
