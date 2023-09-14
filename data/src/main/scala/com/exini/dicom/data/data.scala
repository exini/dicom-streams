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
import java.nio.charset.StandardCharsets
import java.nio.{ ByteBuffer, ByteOrder }
import java.time.{ ZoneOffset, ZonedDateTime }
import java.util.UUID

package object data {

  final val multiValueDelimiter = """\"""

  final val indeterminateLength = 0xffffffff

  private final val hexDigits = Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

  final val magicBytes: Array[Byte] = "DICM".getBytes(StandardCharsets.UTF_8)

  def bytes(xs: Int*): Array[Byte] = xs.map(_.toByte).toArray[Byte]

  def groupNumber(tag: Int): Int   = tag >>> 16
  def elementNumber(tag: Int): Int = tag & '\uffff'

  def bytesToShort(bytes: Array[Byte], bigEndian: Boolean = false): Short =
    if (bigEndian) bytesToShortBE(bytes) else bytesToShortLE(bytes)
  def bytesToShortBE(bytes: Array[Byte]): Short = ((bytes(0) << 8) + (bytes(1) & 255)).toShort
  def bytesToShortLE(bytes: Array[Byte]): Short = ((bytes(1) << 8) + (bytes(0) & 255)).toShort
  def bytesToLong(bytes: Array[Byte], bigEndian: Boolean = false): Long =
    if (bigEndian) bytesToLongBE(bytes) else bytesToLongLE(bytes)
  def bytesToLongBE(bytes: Array[Byte]): Long =
    (bytes(0).toLong << 56) + ((bytes(1) & 255).toLong << 48) + ((bytes(2) & 255).toLong << 40) + ((bytes(
      3
    ) & 255).toLong << 32) + ((bytes(4) & 255).toLong << 24) + ((bytes(5) & 255) << 16).toLong + ((bytes(
      6
    ) & 255) << 8).toLong + (bytes(7) & 255).toLong
  def bytesToLongLE(bytes: Array[Byte]): Long =
    (bytes(7).toLong << 56) + ((bytes(6) & 255).toLong << 48) + ((bytes(5) & 255).toLong << 40) + ((bytes(
      4
    ) & 255).toLong << 32) + ((bytes(3) & 255).toLong << 24) + ((bytes(2) & 255) << 16).toLong + ((bytes(
      1
    ) & 255) << 8).toLong + (bytes(0) & 255).toLong
  def bytesToDouble(bytes: Array[Byte], bigEndian: Boolean = false): Double =
    if (bigEndian) bytesToDoubleBE(bytes) else bytesToDoubleLE(bytes)
  def bytesToDoubleBE(bytes: Array[Byte]): Double = java.lang.Double.longBitsToDouble(bytesToLongBE(bytes))
  def bytesToDoubleLE(bytes: Array[Byte]): Double = java.lang.Double.longBitsToDouble(bytesToLongLE(bytes))
  def bytesToFloat(bytes: Array[Byte], bigEndian: Boolean = false): Float =
    if (bigEndian) bytesToFloatBE(bytes) else bytesToFloatLE(bytes)
  def bytesToFloatBE(bytes: Array[Byte]): Float = java.lang.Float.intBitsToFloat(bytesToIntBE(bytes))
  def bytesToFloatLE(bytes: Array[Byte]): Float = java.lang.Float.intBitsToFloat(bytesToIntLE(bytes))
  def bytesToUShort(bytes: Array[Byte], bigEndian: Boolean = false): Int =
    if (bigEndian) bytesToUShortBE(bytes) else bytesToUShortLE(bytes)
  def bytesToUShortBE(bytes: Array[Byte]): Int = ((bytes(0) & 255) << 8) + (bytes(1) & 255)
  def bytesToUShortLE(bytes: Array[Byte]): Int = ((bytes(1) & 255) << 8) + (bytes(0) & 255)
  def bytesToTag(bytes: Array[Byte], bigEndian: Boolean = false): Int =
    if (bigEndian) bytesToTagBE(bytes) else bytesToTagLE(bytes)
  def bytesToTagBE(bytes: Array[Byte]): Int = bytesToIntBE(bytes)
  def bytesToTagLE(bytes: Array[Byte]): Int =
    (bytes(1) << 24) + ((bytes(0) & 255) << 16) + ((bytes(3) & 255) << 8) + (bytes(2) & 255)
  def bytesToVR(bytes: Array[Byte]): Int = bytesToUShortBE(bytes)
  def bytesToInt(bytes: Array[Byte], bigEndian: Boolean = false): Int =
    if (bigEndian) bytesToIntBE(bytes) else bytesToIntLE(bytes)
  def bytesToIntBE(bytes: Array[Byte]): Int =
    (bytes(0) << 24) + ((bytes(1) & 255) << 16) + ((bytes(2) & 255) << 8) + (bytes(3) & 255)
  def bytesToIntLE(bytes: Array[Byte]): Int =
    (bytes(3) << 24) + ((bytes(2) & 255) << 16) + ((bytes(1) & 255) << 8) + (bytes(0) & 255)

  def shortToBytes(i: Short, bigEndian: Boolean = false): Array[Byte] =
    if (bigEndian) shortToBytesBE(i) else shortToBytesLE(i)
  def shortToBytesBE(i: Short): Array[Byte]                       = Array[Byte]((i >> 8).toByte, i.toByte)
  def shortToBytesLE(i: Short): Array[Byte]                       = Array[Byte](i.toByte, (i >> 8).toByte)
  def intToBytes(i: Int, bigEndian: Boolean = false): Array[Byte] = if (bigEndian) intToBytesBE(i) else intToBytesLE(i)
  def intToBytesBE(i: Int): Array[Byte]                           = bytes(i >> 24, i >> 16, i >> 8, i)
  def intToBytesLE(i: Int): Array[Byte]                           = bytes(i, i >> 8, i >> 16, i >> 24)
  def longToBytes(i: Long, bigEndian: Boolean = false): Array[Byte] =
    if (bigEndian) longToBytesBE(i) else longToBytesLE(i)
  def longToBytesBE(i: Long): Array[Byte] =
    Array[Byte](
      (i >> 56).toByte,
      (i >> 48).toByte,
      (i >> 40).toByte,
      (i >> 32).toByte,
      (i >> 24).toByte,
      (i >> 16).toByte,
      (i >> 8).toByte,
      i.toByte
    )
  def longToBytesLE(i: Long): Array[Byte] =
    Array[Byte](
      i.toByte,
      (i >> 8).toByte,
      (i >> 16).toByte,
      (i >> 24).toByte,
      (i >> 32).toByte,
      (i >> 40).toByte,
      (i >> 48).toByte,
      (i >> 56).toByte
    )
  def floatToBytes(f: Float, bigEndian: Boolean = false): Array[Byte] =
    intToBytes(java.lang.Float.floatToIntBits(f), bigEndian)
  def doubleToBytes(d: Double, bigEndian: Boolean = false): Array[Byte] =
    ByteBuffer
      .wrap(new Array[Byte](8))
      .order(if (bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
      .putDouble(d)
      .array
  def tagToBytes(tag: Int, bigEndian: Boolean = false): Array[Byte] =
    if (bigEndian) tagToBytesBE(tag) else tagToBytesLE(tag)
  def tagToBytesBE(tag: Int): Array[Byte] = intToBytesBE(tag)
  def tagToBytesLE(tag: Int): Array[Byte] =
    bytes(tag >> 16, tag >> 24, tag, tag >> 8)

  def intToUnsignedLong(i: Int): Long   = i & 0xffffffffL
  def shortToUnsignedInt(i: Short): Int = i & 0xffff

  def truncate(n: Int, bytes: Array[Byte], bigEndian: Boolean = false): Array[Byte] =
    if (bigEndian) bytes.drop(n) else bytes.dropRight(n)

  private def bytesToHexString(bytes: Array[Byte]) =
    new String(bytes.flatMap(b => List(hexDigits(b >>> 4 & 0xf), hexDigits(b & 0xf))))
  def tagToString(tag: Int): String = {
    val s = bytesToHexString(tagToBytes(tag, bigEndian = true))
    s"(${s.substring(0, 4)},${s.substring(4, 8)})"
  }
  def byteToHexString(b: Byte): String   = bytesToHexString(Array[Byte](b))
  def shortToHexString(s: Short): String = bytesToHexString(shortToBytes(s, bigEndian = true))
  def intToHexString(i: Int): String     = bytesToHexString(intToBytes(i, bigEndian = true))
  def longToHexString(i: Long): String   = bytesToHexString(longToBytes(i, bigEndian = true))

  def lengthToLong(length: Int): Long = if (length == indeterminateLength) -1L else intToUnsignedLong(length)

  def padToEvenLength(bytes: Array[Byte], tag: Int): Array[Byte] = padToEvenLength(bytes, Lookup.vrOf(tag))
  def padToEvenLength(bytes: Array[Byte], vr: VR): Array[Byte] = {
    val padding = if ((bytes.length & 1) != 0) Array[Byte](vr.paddingByte) else Array.emptyByteArray
    bytes ++ padding
  }

  final val itemLE: Array[Byte]                     = tagToBytesLE(Tag.Item) ++ intToBytesLE(indeterminateLength)
  final val itemBE: Array[Byte]                     = tagToBytesBE(Tag.Item) ++ intToBytesBE(indeterminateLength)
  def item(bigEndian: Boolean = false): Array[Byte] = if (bigEndian) itemBE else itemLE
  def item(length: Int): Array[Byte]                = tagToBytesLE(Tag.Item) ++ intToBytesLE(length)
  def item(length: Int, bigEndian: Boolean): Array[Byte] =
    tagToBytes(Tag.Item, bigEndian) ++ intToBytes(length, bigEndian)

  final val zero4Bytes                      = bytes(0, 0, 0, 0)
  final val itemDelimitationLE: Array[Byte] = tagToBytesLE(Tag.ItemDelimitationItem) ++ zero4Bytes
  final val itemDelimitationBE: Array[Byte] = tagToBytesBE(Tag.ItemDelimitationItem) ++ zero4Bytes
  def itemDelimitation(bigEndian: Boolean = false): Array[Byte] =
    if (bigEndian) itemDelimitationBE else itemDelimitationLE

  final val sequenceDelimitationLE: Array[Byte] = tagToBytesLE(Tag.SequenceDelimitationItem) ++ zero4Bytes
  final val sequenceDelimitationBE: Array[Byte] = tagToBytesBE(Tag.SequenceDelimitationItem) ++ zero4Bytes
  def sequenceDelimitation(bigEndian: Boolean = false): Array[Byte] =
    if (bigEndian) sequenceDelimitationBE else sequenceDelimitationLE

  // System time zone and default character set
  def systemZone: ZoneOffset = ZonedDateTime.now().getOffset

  def isFileMetaInformation(tag: Int): Boolean = (tag & 0xffff0000) == 0x00020000
  def isPrivate(tag: Int): Boolean             = groupNumber(tag) % 2 == 1
  def isGroupLength(tag: Int): Boolean         = elementNumber(tag) == 0

  // UID utility tools
  private final val uidRoot = "2.25"
  private def toUID(root: String, uuid: UUID) = {
    val uuidBytes = bytes(0) ++
      longToBytesBE(uuid.getMostSignificantBits) ++
      longToBytesBE(uuid.getLeastSignificantBits)
    val uuidString = new BigInteger(uuidBytes).toString
    s"$root.$uuidString".take(64)
  }
  private def randomUID(root: String): String                       = toUID(root, UUID.randomUUID)
  private def nameBasedUID(name: Array[Byte], root: String): String = toUID(root, UUID.nameUUIDFromBytes(name))
  def createUID(): String                                           = randomUID(uidRoot)
  def createUID(root: String): String                               = randomUID(root)
  def createNameBasedUID(name: Array[Byte]): String                 = nameBasedUID(name, uidRoot)
  def createNameBasedUID(name: Array[Byte], root: String): String   = nameBasedUID(name, root)

  implicit class RichByteArray(val bytes: Array[Byte]) extends AnyVal {
    def utf8String: String  = if (bytes.isEmpty) "" else new String(bytes, StandardCharsets.UTF_8)
    def arrayString: String = bytes.mkString("[", ", ", "]") // TODO limit here?
  }

  implicit class RichString(val s: String) extends AnyVal {
    def utf8Bytes: Array[Byte] = s.getBytes(StandardCharsets.UTF_8)
  }
}
