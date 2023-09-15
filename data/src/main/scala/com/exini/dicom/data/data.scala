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
import scala.collection.immutable

package object data {

  type Bytes = immutable.ArraySeq[Byte]

  final val emptyBytes = immutable.ArraySeq.empty[Byte]

  final val multiValueDelimiter = """\"""

  final val indeterminateLength = 0xffffffff

  private final val hexDigits = Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

  final val magicBytes: Bytes = "DICM".getBytes(StandardCharsets.UTF_8).wrap

  def bytesb(xs: Byte*): Bytes  = xs.toArray.wrap
  def bytess(xs: Short*): Bytes = xs.map(_.toByte).toArray.wrap
  def bytesi(xs: Int*): Bytes   = xs.map(_.toByte).toArray.wrap
  def bytesl(xs: Long*): Bytes  = xs.map(_.toByte).toArray.wrap
  def zeroBytes(n: Int): Bytes  = new Array[Byte](n).wrap

  def groupNumber(tag: Int): Int   = tag >>> 16
  def elementNumber(tag: Int): Int = tag & '\uffff'

  def bytesToShort(bytes: Bytes, bigEndian: Boolean = false): Short =
    if (bigEndian) bytesToShortBE(bytes) else bytesToShortLE(bytes)
  def bytesToShortBE(bytes: Bytes): Short = ((bytes(0) << 8) + (bytes(1) & 255)).toShort
  def bytesToShortLE(bytes: Bytes): Short = ((bytes(1) << 8) + (bytes(0) & 255)).toShort
  def bytesToLong(bytes: Bytes, bigEndian: Boolean = false): Long =
    if (bigEndian) bytesToLongBE(bytes) else bytesToLongLE(bytes)
  def bytesToLongBE(bytes: Bytes): Long =
    (bytes(0).toLong << 56) + ((bytes(1) & 255).toLong << 48) + ((bytes(2) & 255).toLong << 40) + ((bytes(
      3
    ) & 255).toLong << 32) + ((bytes(4) & 255).toLong << 24) + ((bytes(5) & 255) << 16).toLong + ((bytes(
      6
    ) & 255) << 8).toLong + (bytes(7) & 255).toLong
  def bytesToLongLE(bytes: Bytes): Long =
    (bytes(7).toLong << 56) + ((bytes(6) & 255).toLong << 48) + ((bytes(5) & 255).toLong << 40) + ((bytes(
      4
    ) & 255).toLong << 32) + ((bytes(3) & 255).toLong << 24) + ((bytes(2) & 255) << 16).toLong + ((bytes(
      1
    ) & 255) << 8).toLong + (bytes(0) & 255).toLong
  def bytesToDouble(bytes: Bytes, bigEndian: Boolean = false): Double =
    if (bigEndian) bytesToDoubleBE(bytes) else bytesToDoubleLE(bytes)
  def bytesToDoubleBE(bytes: Bytes): Double = java.lang.Double.longBitsToDouble(bytesToLongBE(bytes))
  def bytesToDoubleLE(bytes: Bytes): Double = java.lang.Double.longBitsToDouble(bytesToLongLE(bytes))
  def bytesToFloat(bytes: Bytes, bigEndian: Boolean = false): Float =
    if (bigEndian) bytesToFloatBE(bytes) else bytesToFloatLE(bytes)
  def bytesToFloatBE(bytes: Bytes): Float = java.lang.Float.intBitsToFloat(bytesToIntBE(bytes))
  def bytesToFloatLE(bytes: Bytes): Float = java.lang.Float.intBitsToFloat(bytesToIntLE(bytes))
  def bytesToUShort(bytes: Bytes, bigEndian: Boolean = false): Int =
    if (bigEndian) bytesToUShortBE(bytes) else bytesToUShortLE(bytes)
  def bytesToUShortBE(bytes: Bytes): Int = ((bytes(0) & 255) << 8) + (bytes(1) & 255)
  def bytesToUShortLE(bytes: Bytes): Int = ((bytes(1) & 255) << 8) + (bytes(0) & 255)
  def bytesToTag(bytes: Bytes, bigEndian: Boolean = false): Int =
    if (bigEndian) bytesToTagBE(bytes) else bytesToTagLE(bytes)
  def bytesToTagBE(bytes: Bytes): Int = bytesToIntBE(bytes)
  def bytesToTagLE(bytes: Bytes): Int =
    (bytes(1) << 24) + ((bytes(0) & 255) << 16) + ((bytes(3) & 255) << 8) + (bytes(2) & 255)
  def bytesToVR(bytes: Bytes): Int = bytesToUShortBE(bytes)
  def bytesToInt(bytes: Bytes, bigEndian: Boolean = false): Int =
    if (bigEndian) bytesToIntBE(bytes) else bytesToIntLE(bytes)
  def bytesToIntBE(bytes: Bytes): Int =
    (bytes(0) << 24) + ((bytes(1) & 255) << 16) + ((bytes(2) & 255) << 8) + (bytes(3) & 255)
  def bytesToIntLE(bytes: Bytes): Int =
    (bytes(3) << 24) + ((bytes(2) & 255) << 16) + ((bytes(1) & 255) << 8) + (bytes(0) & 255)

  def shortToBytes(i: Short, bigEndian: Boolean = false): Bytes =
    if (bigEndian) shortToBytesBE(i) else shortToBytesLE(i)
  def shortToBytesBE(i: Short): Bytes                       = bytesi(i >> 8, i.toInt)
  def shortToBytesLE(i: Short): Bytes                       = bytesi(i.toInt, i >> 8)
  def intToBytes(i: Int, bigEndian: Boolean = false): Bytes = if (bigEndian) intToBytesBE(i) else intToBytesLE(i)
  def intToBytesBE(i: Int): Bytes                           = bytesi(i >> 24, i >> 16, i >> 8, i)
  def intToBytesLE(i: Int): Bytes                           = bytesi(i, i >> 8, i >> 16, i >> 24)
  def longToBytes(i: Long, bigEndian: Boolean = false): Bytes =
    if (bigEndian) longToBytesBE(i) else longToBytesLE(i)
  def longToBytesBE(i: Long): Bytes = bytesl(i >> 56, i >> 48, i >> 40, i >> 32, i >> 24, i >> 16, i >> 8, i)
  def longToBytesLE(i: Long): Bytes = bytesl(i, i >> 8, i >> 16, i >> 24, i >> 32, i >> 40, i >> 48, i >> 56)
  def floatToBytes(f: Float, bigEndian: Boolean = false): Bytes =
    intToBytes(java.lang.Float.floatToIntBits(f), bigEndian)
  def doubleToBytes(d: Double, bigEndian: Boolean = false): Bytes =
    ByteBuffer
      .wrap(new Array[Byte](8))
      .order(if (bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
      .putDouble(d)
      .array
      .wrap
  def tagToBytes(tag: Int, bigEndian: Boolean = false): Bytes =
    if (bigEndian) tagToBytesBE(tag) else tagToBytesLE(tag)
  def tagToBytesBE(tag: Int): Bytes = intToBytesBE(tag)
  def tagToBytesLE(tag: Int): Bytes =
    bytesi(tag >> 16, tag >> 24, tag, tag >> 8)

  def intToUnsignedLong(i: Int): Long   = i & 0xffffffffL
  def shortToUnsignedInt(i: Short): Int = i & 0xffff

  def truncate(n: Int, bytes: Bytes, bigEndian: Boolean = false): Bytes =
    if (bigEndian) bytes.drop(n) else bytes.dropRight(n)

  private def bytesToHexString(bytes: Bytes) =
    new String(bytes.unwrap.flatMap(b => List(hexDigits(b >>> 4 & 0xf), hexDigits(b & 0xf))))
  def tagToString(tag: Int): String = {
    val s = bytesToHexString(tagToBytes(tag, bigEndian = true))
    s"(${s.substring(0, 4)},${s.substring(4, 8)})"
  }
  def byteToHexString(b: Byte): String   = bytesToHexString(bytesb(b))
  def shortToHexString(s: Short): String = bytesToHexString(shortToBytes(s, bigEndian = true))
  def intToHexString(i: Int): String     = bytesToHexString(intToBytes(i, bigEndian = true))
  def longToHexString(i: Long): String   = bytesToHexString(longToBytes(i, bigEndian = true))

  def lengthToLong(length: Int): Long = if (length == indeterminateLength) -1L else intToUnsignedLong(length)

  def padToEvenLength(bytes: Bytes, tag: Int): Bytes = padToEvenLength(bytes, Lookup.vrOf(tag))
  def padToEvenLength(bytes: Bytes, vr: VR): Bytes = {
    val padding = if ((bytes.length & 1) != 0) bytesb(vr.paddingByte) else emptyBytes
    bytes ++ padding
  }

  final val itemLE: Bytes                     = tagToBytesLE(Tag.Item) ++ intToBytesLE(indeterminateLength)
  final val itemBE: Bytes                     = tagToBytesBE(Tag.Item) ++ intToBytesBE(indeterminateLength)
  def item(bigEndian: Boolean = false): Bytes = if (bigEndian) itemBE else itemLE
  def item(length: Int): Bytes                = tagToBytesLE(Tag.Item) ++ intToBytesLE(length)
  def item(length: Int, bigEndian: Boolean): Bytes =
    tagToBytes(Tag.Item, bigEndian) ++ intToBytes(length, bigEndian)

  final val zero4Bytes                = bytesi(0, 0, 0, 0)
  final val itemDelimitationLE: Bytes = tagToBytesLE(Tag.ItemDelimitationItem) ++ zero4Bytes
  final val itemDelimitationBE: Bytes = tagToBytesBE(Tag.ItemDelimitationItem) ++ zero4Bytes
  def itemDelimitation(bigEndian: Boolean = false): Bytes =
    if (bigEndian) itemDelimitationBE else itemDelimitationLE

  final val sequenceDelimitationLE: Bytes = tagToBytesLE(Tag.SequenceDelimitationItem) ++ zero4Bytes
  final val sequenceDelimitationBE: Bytes = tagToBytesBE(Tag.SequenceDelimitationItem) ++ zero4Bytes
  def sequenceDelimitation(bigEndian: Boolean = false): Bytes =
    if (bigEndian) sequenceDelimitationBE else sequenceDelimitationLE

  // System time zone and default character set
  def systemZone: ZoneOffset = ZonedDateTime.now().getOffset

  def isFileMetaInformation(tag: Int): Boolean = (tag & 0xffff0000) == 0x00020000
  def isPrivate(tag: Int): Boolean             = groupNumber(tag) % 2 == 1
  def isGroupLength(tag: Int): Boolean         = elementNumber(tag) == 0

  // UID utility tools
  private final val uidRoot = "2.25"
  private def toUID(root: String, uuid: UUID) = {
    val uuidBytes = bytesi(0) ++
      longToBytesBE(uuid.getMostSignificantBits) ++
      longToBytesBE(uuid.getLeastSignificantBits)
    val uuidString = new BigInteger(uuidBytes.unwrap).toString
    s"$root.$uuidString".take(64)
  }
  private def randomUID(root: String): String                 = toUID(root, UUID.randomUUID)
  private def nameBasedUID(name: Bytes, root: String): String = toUID(root, UUID.nameUUIDFromBytes(name.unwrap))
  def createUID(): String                                     = randomUID(uidRoot)
  def createUID(root: String): String                         = randomUID(root)
  def createNameBasedUID(name: Bytes): String                 = nameBasedUID(name, uidRoot)
  def createNameBasedUID(name: Bytes, root: String): String   = nameBasedUID(name, root)

  implicit class RichByteArray(val byteArray: Array[Byte]) extends AnyVal {
    def wrap: Bytes = immutable.ArraySeq.from(byteArray)
  }

  implicit class RichBytes(val bytes: Bytes) extends AnyVal {
    def utf8String: String = if (bytes.isEmpty) "" else new String(bytes.unwrap, StandardCharsets.UTF_8)
    def arrayString: String =
      if (bytes.length > 50)
        bytes.take(25).mkString("[", ", ", " ... ") + bytes.takeRight(25).mkString("", ", ", "]")
      else
        bytes.mkString("[", ", ", "]")
    def unwrap: Array[Byte] = bytes.toArray[Byte]
  }

  implicit class RichString(val s: String) extends AnyVal {
    def utf8Bytes: Bytes = s.getBytes(StandardCharsets.UTF_8).wrap
  }
}
