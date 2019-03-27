/*
 * Copyright 2019 EXINI Diagnostics
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exini.dicom

import java.math.BigInteger
import java.nio.{ByteBuffer, ByteOrder}
import java.time.{ZoneOffset, ZonedDateTime}
import java.util.UUID

import akka.util.ByteString
import com.exini.dicom.data.VR.VR

package object data {

  final val multiValueDelimiter = """\"""

  final val indeterminateLength = 0xFFFFFFFF

  private final val hexDigits = Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

  def groupNumber(tag: Int): Int = tag >>> 16
  def elementNumber(tag: Int): Int = tag & '\uffff'

  def bytesToShort(bytes: ByteString, bigEndian: Boolean = false): Short = if (bigEndian) bytesToShortBE(bytes) else bytesToShortLE(bytes)
  def bytesToShortBE(bytes: ByteString): Short = ((bytes(0) << 8) + (bytes(1) & 255)).toShort
  def bytesToShortLE(bytes: ByteString): Short = ((bytes(1) << 8) + (bytes(0) & 255)).toShort
  def bytesToLong(bytes: ByteString, bigEndian: Boolean = false): Long = if (bigEndian) bytesToLongBE(bytes) else bytesToLongLE(bytes)
  def bytesToLongBE(bytes: ByteString): Long = (bytes(0).toLong << 56) + ((bytes(1) & 255).toLong << 48) + ((bytes(2) & 255).toLong << 40) + ((bytes(3) & 255).toLong << 32) + ((bytes(4) & 255).toLong << 24) + ((bytes(5) & 255) << 16).toLong + ((bytes(6) & 255) << 8).toLong + (bytes(7) & 255).toLong
  def bytesToLongLE(bytes: ByteString): Long = (bytes(7).toLong << 56) + ((bytes(6) & 255).toLong << 48) + ((bytes(5) & 255).toLong << 40) + ((bytes(4) & 255).toLong << 32) + ((bytes(3) & 255).toLong << 24) + ((bytes(2) & 255) << 16).toLong + ((bytes(1) & 255) << 8).toLong + (bytes(0) & 255).toLong
  def bytesToDouble(bytes: ByteString, bigEndian: Boolean = false): Double = if (bigEndian) bytesToDoubleBE(bytes) else bytesToDoubleLE(bytes)
  def bytesToDoubleBE(bytes: ByteString): Double = java.lang.Double.longBitsToDouble(bytesToLongBE(bytes))
  def bytesToDoubleLE(bytes: ByteString): Double = java.lang.Double.longBitsToDouble(bytesToLongLE(bytes))
  def bytesToFloat(bytes: ByteString, bigEndian: Boolean = false): Float = if (bigEndian) bytesToFloatBE(bytes) else bytesToFloatLE(bytes)
  def bytesToFloatBE(bytes: ByteString): Float = java.lang.Float.intBitsToFloat(bytesToIntBE(bytes))
  def bytesToFloatLE(bytes: ByteString): Float = java.lang.Float.intBitsToFloat(bytesToIntLE(bytes))
  def bytesToUShort(bytes: ByteString, bigEndian: Boolean = false): Int = if (bigEndian) bytesToUShortBE(bytes) else bytesToUShortLE(bytes)
  def bytesToUShortBE(bytes: ByteString): Int = ((bytes(0) & 255) << 8) + (bytes(1) & 255)
  def bytesToUShortLE(bytes: ByteString): Int = ((bytes(1) & 255) << 8) + (bytes(0) & 255)
  def bytesToTag(bytes: ByteString, bigEndian: Boolean = false): Int = if (bigEndian) bytesToTagBE(bytes) else bytesToTagLE(bytes)
  def bytesToTagBE(bytes: ByteString): Int = bytesToIntBE(bytes)
  def bytesToTagLE(bytes: ByteString): Int = (bytes(1) << 24) + ((bytes(0) & 255) << 16) + ((bytes(3) & 255) << 8) + (bytes(2) & 255)
  def bytesToVR(bytes: ByteString): Int = bytesToUShortBE(bytes)
  def bytesToInt(bytes: ByteString, bigEndian: Boolean = false): Int = if (bigEndian) bytesToIntBE(bytes) else bytesToIntLE(bytes)
  def bytesToIntBE(bytes: ByteString): Int = (bytes(0) << 24) + ((bytes(1) & 255) << 16) + ((bytes(2) & 255) << 8) + (bytes(3) & 255)
  def bytesToIntLE(bytes: ByteString): Int = (bytes(3) << 24) + ((bytes(2) & 255) << 16) + ((bytes(1) & 255) << 8) + (bytes(0) & 255)

  def shortToBytes(i: Short, bigEndian: Boolean = false): ByteString = if (bigEndian) shortToBytesBE(i) else shortToBytesLE(i)
  def shortToBytesBE(i: Short): ByteString = ByteString((i >> 8).toByte, i.toByte)
  def shortToBytesLE(i: Short): ByteString = ByteString(i.toByte, (i >> 8).toByte)
  def intToBytes(i: Int, bigEndian: Boolean = false): ByteString = if (bigEndian) intToBytesBE(i) else intToBytesLE(i)
  def intToBytesBE(i: Int): ByteString = ByteString((i >> 24).toByte, (i >> 16).toByte, (i >> 8).toByte, i.toByte)
  def intToBytesLE(i: Int): ByteString = ByteString(i.toByte, (i >> 8).toByte, (i >> 16).toByte, (i >> 24).toByte)
  def longToBytes(i: Long, bigEndian: Boolean = false): ByteString = if (bigEndian) longToBytesBE(i) else longToBytesLE(i)
  def longToBytesBE(i: Long): ByteString = ByteString((i >> 56).toByte, (i >> 48).toByte, (i >> 40).toByte, (i >> 32).toByte, (i >> 24).toByte, (i >> 16).toByte, (i >> 8).toByte, i.toByte)
  def longToBytesLE(i: Long): ByteString = ByteString(i.toByte, (i >> 8).toByte, (i >> 16).toByte, (i >> 24).toByte, (i >> 32).toByte, (i >> 40).toByte, (i >> 48).toByte, (i >> 56).toByte)
  def floatToBytes(f: Float, bigEndian: Boolean = false): ByteString = intToBytes(java.lang.Float.floatToIntBits(f), bigEndian)
  def doubleToBytes(d: Double, bigEndian: Boolean = false): ByteString = ByteString(ByteBuffer.wrap(new Array[Byte](8)).order(if (bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN).putDouble(d).array)
  def tagToBytes(tag: Int, bigEndian: Boolean = false): ByteString = if (bigEndian) tagToBytesBE(tag) else tagToBytesLE(tag)
  def tagToBytesBE(tag: Int): ByteString = intToBytesBE(tag)
  def tagToBytesLE(tag: Int): ByteString = ByteString((tag >> 16).toByte, (tag >> 24).toByte, tag.toByte, (tag >> 8).toByte)

  def intToUnsignedLong(i: Int): Long = i & 0xFFFFFFFFL
  def shortToUnsignedInt(i: Short): Int = i & 0xFFFF

  def truncate(n: Int, bytes: ByteString, bigEndian: Boolean = false): ByteString = if (bigEndian) bytes.drop(n) else bytes.dropRight(n)

  def tagToString(tag: Int): String = new String(Array('(',
    hexDigits(tag >>> 28), hexDigits(tag >>> 24 & 15), hexDigits(tag >>> 20 & 15), hexDigits(tag >>> 16 & 15), ',',
    hexDigits(tag >>> 12 & 15), hexDigits(tag >>> 8 & 15), hexDigits(tag >>> 4 & 15), hexDigits(tag >>> 0 & 15), ')'))
  def byteToHexString(b: Byte) = new String(Array(hexDigits(b >>> 4 & 15), hexDigits(b >>> 0 & 15)))
  def shortToHexString(s: Short): String = new String(Array(hexDigits(s >>> 12 & 15), hexDigits(s >>> 8 & 15), hexDigits(s >>> 4 & 15), hexDigits(s >>> 0 & 15)))
  def intToHexString(i: Int): String = new String(Array(
    hexDigits(i >>> 28), hexDigits(i >>> 24 & 15), hexDigits(i >>> 20 & 15), hexDigits(i >>> 16 & 15),
    hexDigits(i >>> 12 & 15), hexDigits(i >>> 8 & 15), hexDigits(i >>> 4 & 15), hexDigits(i >>> 0 & 15)))

  def lengthToLong(length: Int): Long = if (length == indeterminateLength) -1L else intToUnsignedLong(length)

  def padToEvenLength(bytes: ByteString, tag: Int): ByteString = padToEvenLength(bytes, Dictionary.vrOf(tag))
  def padToEvenLength(bytes: ByteString, vr: VR): ByteString = {
    val padding = if ((bytes.length & 1) != 0) ByteString(vr.paddingByte) else ByteString.empty
    bytes ++ padding
  }

  final val itemLE: ByteString = tagToBytesLE(Tag.Item) ++ intToBytesLE(indeterminateLength)
  final val itemBE: ByteString = tagToBytesBE(Tag.Item) ++ intToBytesBE(indeterminateLength)
  def item(bigEndian: Boolean = false): ByteString = if (bigEndian) itemBE else itemLE
  def item(length: Int): ByteString = tagToBytesLE(Tag.Item) ++ intToBytesLE(length)
  def item(length: Int, bigEndian: Boolean): ByteString = tagToBytes(Tag.Item, bigEndian) ++ intToBytes(length, bigEndian)

  final val zero4Bytes = ByteString(0, 0, 0, 0)
  final val itemDelimitationLE: ByteString = tagToBytesLE(Tag.ItemDelimitationItem) ++ zero4Bytes
  final val itemDelimitationBE: ByteString = tagToBytesBE(Tag.ItemDelimitationItem) ++ zero4Bytes
  def itemDelimitation(bigEndian: Boolean = false): ByteString = if (bigEndian) itemDelimitationBE else itemDelimitationLE

  final val sequenceDelimitationLE: ByteString = tagToBytesLE(Tag.SequenceDelimitationItem) ++ zero4Bytes
  final val sequenceDelimitationBE: ByteString = tagToBytesBE(Tag.SequenceDelimitationItem) ++ zero4Bytes
  def sequenceDelimitation(bigEndian: Boolean = false): ByteString = if (bigEndian) sequenceDelimitationBE else sequenceDelimitationLE

  // System time zone and default character set
  def systemZone: ZoneOffset = ZonedDateTime.now().getOffset
  final val defaultCharacterSet: CharacterSets = CharacterSets.defaultOnly

  def isFileMetaInformation(tag: Int): Boolean = (tag & 0xFFFF0000) == 0x00020000
  def isPrivate(tag: Int): Boolean = groupNumber(tag) % 2 == 1
  def isGroupLength(tag: Int): Boolean = elementNumber(tag) == 0

  // UID utility tools (from dcm4che UIDUtils)
  private final val uuidRoot = "2.25"
  private def toUID(root: String, uuid: UUID) = {
    val uuidBytes = ByteString(0) ++
      longToBytesBE(uuid.getMostSignificantBits) ++
      longToBytesBE(uuid.getLeastSignificantBits)
    val uuidString = new BigInteger(uuidBytes.toArray).toString
    val rootLength = root.length
    val uuidLength = uuidString.length
    val cs = new Array[Char](rootLength + uuidLength + 1)
    root.getChars(0, rootLength, cs, 0)
    cs(rootLength) = '.'
    uuidString.getChars(0, uuidLength, cs, rootLength + 1)
    new String(cs)
  }
  private def nameBasedUID(name: ByteString, root: String): String = toUID(root, UUID.nameUUIDFromBytes(name.toArray))
  private def randomUID(root: String): String = toUID(root, UUID.randomUUID)
  def createUID(): String = randomUID(uuidRoot)
  def createUID(root: String): String = randomUID(root)
  def createNameBasedUID(name: ByteString): String = nameBasedUID(name, uuidRoot)
  def createNameBasedUID(name: ByteString, root: String): String = nameBasedUID(name, root)
}
