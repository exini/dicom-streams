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

package com.exini.dicom.data

import java.math.{BigInteger, BigDecimal}
import java.net.URI
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder, SignStyle}
import java.time.temporal.ChronoField._
import java.time._

import akka.util.ByteString
import com.exini.dicom.data.VR._

/**
  * This class describes a DICOM data value
  *
  * @param bytes     the binary data of this value
  */
case class Value private[data](bytes: ByteString) {
  import Value._

  val length: Int = bytes.length

  /**
    * For each component value of this value, return it's string representation
    *
    * @param characterSets Character sets used for string decoding
    * @return a sequence of strings, one for each component of this value
    */
  def toStrings(vr: VR, bigEndian: Boolean = false, characterSets: CharacterSets = defaultCharacterSet): Seq[String] = if (bytes.isEmpty) Seq.empty else
    vr match {
      case AT => parseAT(bytes, bigEndian).map(tagToString)
      case FL => parseFL(bytes, bigEndian).map(_.toString)
      case FD => parseFD(bytes, bigEndian).map(_.toString)
      case SS => parseSS(bytes, bigEndian).map(_.toString)
      case SL => parseSL(bytes, bigEndian).map(_.toString)
      case SV => parseSV(bytes, bigEndian).map(_.toString)
      case US => parseSS(bytes, bigEndian).map(java.lang.Short.toUnsignedInt).map(_.toString)
      case UL => parseSL(bytes, bigEndian).map(java.lang.Integer.toUnsignedString)
      case UV => parseSV(bytes, bigEndian).map(java.lang.Long.toUnsignedString)
      case OB => Seq(bytes.map(byteToHexString).mkString(" "))
      case OW => Seq(split(bytes, 2).map(bytesToShort(_, bigEndian)).map(shortToHexString).mkString(" "))
      case OL => Seq(split(bytes, 4).map(bytesToInt(_, bigEndian)).map(intToHexString).mkString(" "))
      case OV => Seq(split(bytes, 8).map(bytesToLong(_, bigEndian)).map(longToHexString).mkString(" "))
      case OF => Seq(parseFL(bytes, bigEndian).mkString(" "))
      case OD => Seq(parseFD(bytes, bigEndian).mkString(" "))
      case ST | LT | UT | UR => Seq(trimPadding(characterSets.decode(vr, bytes), vr.paddingByte))
      case DA | TM | DT => split(bytes.utf8String).map(trim)
      case UC => split(trimPadding(characterSets.decode(vr, bytes), vr.paddingByte))
      case _ => split(characterSets.decode(vr, bytes)).map(trim)
    }

  /**
    * @return this value as a sequence of shorts. Casting is performed if necessary. If the value has no
    *         short representation, an empty sequence is returned.
    */
  def toShorts(vr: VR, bigEndian: Boolean = false): Seq[Short] = vr match {
    case FL => parseFL(bytes, bigEndian).map(_.toShort)
    case FD => parseFD(bytes, bigEndian).map(_.toShort)
    case SS => parseSS(bytes, bigEndian)
    case SL => parseSL(bytes, bigEndian).map(_.toShort)
    case SV => parseSV(bytes, bigEndian).map(_.toShort)
    case US => parseUS(bytes, bigEndian).map(_.toShort)
    case UL => parseUL(bytes, bigEndian).map(_.toShort)
    case UV => parseUV(bytes, bigEndian).map(_.shortValue)
    case DS => parseDS(bytes).map(_.toShort)
    case IS => parseIS(bytes).map(_.toShort)
    case _ => Seq.empty
  }

  /**
    * @return this value as a sequence of int. Casting is performed if necessary. If the value has no
    *         int representation, an empty sequence is returned.
    */
  def toInts(vr: VR, bigEndian: Boolean = false): Seq[Int] = vr match {
    case AT => parseAT(bytes, bigEndian)
    case FL => parseFL(bytes, bigEndian).map(_.toInt)
    case FD => parseFD(bytes, bigEndian).map(_.toInt)
    case SS => parseSS(bytes, bigEndian).map(_.toInt)
    case SL => parseSL(bytes, bigEndian)
    case SV => parseSV(bytes, bigEndian).map(_.toInt)
    case US => parseUS(bytes, bigEndian)
    case UL => parseUL(bytes, bigEndian).map(_.toInt)
    case UV => parseUV(bytes, bigEndian).map(_.intValue)
    case DS => parseDS(bytes).map(_.toInt)
    case IS => parseIS(bytes).map(_.toInt)
    case _ => Seq.empty
  }

  /**
    * @return this value as a sequence of longs. Casting is performed if necessary. If the value has no
    *         long representation, an empty sequence is returned.
    */
  def toLongs(vr: VR, bigEndian: Boolean = false): Seq[Long] = vr match {
    case FL => parseFL(bytes, bigEndian).map(_.toLong)
    case FD => parseFD(bytes, bigEndian).map(_.toLong)
    case SS => parseSS(bytes, bigEndian).map(_.toLong)
    case SL => parseSL(bytes, bigEndian).map(_.toLong)
    case SV => parseSV(bytes, bigEndian)
    case US => parseUS(bytes, bigEndian).map(_.toLong)
    case UL => parseUL(bytes, bigEndian)
    case UV => parseUV(bytes, bigEndian).map(_.longValue)
    case DS => parseDS(bytes).map(_.toLong)
    case IS => parseIS(bytes)
    case _ => Seq.empty
  }

  /**
    * @return this value as a sequence of big integers. Casting is performed if necessary. If the value has no
    *         big integer representation, an empty sequence is returned.
    */
  def toVeryLongs(vr: VR, bigEndian: Boolean = false): Seq[BigInteger] = vr match {
    case FL => parseFL(bytes, bigEndian).map(_.toDouble).map(BigDecimal.valueOf).map(_.toBigInteger)
    case FD => parseFD(bytes, bigEndian).map(BigDecimal.valueOf).map(_.toBigInteger)
    case SS => parseSS(bytes, bigEndian).map(_.toLong).map(BigInteger.valueOf)
    case SL => parseSL(bytes, bigEndian).map(_.toLong).map(BigInteger.valueOf)
    case SV => parseSV(bytes, bigEndian).map(BigInteger.valueOf)
    case US => parseUS(bytes, bigEndian).map(_.toLong).map(BigInteger.valueOf)
    case UL => parseUL(bytes, bigEndian).map(BigInteger.valueOf)
    case UV => parseUV(bytes, bigEndian)
    case DS => parseDS(bytes).map(BigDecimal.valueOf).map(_.toBigInteger)
    case IS => parseIS(bytes).map(BigInteger.valueOf)
    case _ => Seq.empty
  }

  /**
    * @return this value as a sequence of floats. Casting is performed if necessary. If the value has no
    *         float representation, an empty sequence is returned.
    */
  def toFloats(vr: VR, bigEndian: Boolean = false): Seq[Float] = vr match {
    case FL => parseFL(bytes, bigEndian)
    case FD => parseFD(bytes, bigEndian).map(_.toFloat)
    case SS => parseSS(bytes, bigEndian).map(_.toFloat)
    case SL => parseSL(bytes, bigEndian).map(_.toFloat)
    case SV => parseSV(bytes, bigEndian).map(_.toFloat)
    case US => parseUS(bytes, bigEndian).map(_.toFloat)
    case UL => parseUL(bytes, bigEndian).map(_.toFloat)
    case UV => parseUV(bytes, bigEndian).map(_.floatValue)
    case DS => parseDS(bytes).map(_.toFloat)
    case IS => parseIS(bytes).map(_.toFloat)
    case _ => Seq.empty
  }

  /**
    * @return this value as a sequence of doubles. Casting is performed if necessary. If the value has no
    *         double representation, an empty sequence is returned.
    */
  def toDoubles(vr: VR, bigEndian: Boolean = false): Seq[Double] = vr match {
    case FL => parseFL(bytes, bigEndian).map(_.toDouble)
    case FD => parseFD(bytes, bigEndian)
    case SS => parseSS(bytes, bigEndian).map(_.toDouble)
    case SL => parseSL(bytes, bigEndian).map(_.toDouble)
    case SV => parseSV(bytes, bigEndian).map(_.toDouble)
    case US => parseUS(bytes, bigEndian).map(_.toDouble)
    case UL => parseUL(bytes, bigEndian).map(_.toDouble)
    case UV => parseUV(bytes, bigEndian).map(_.doubleValue)
    case DS => parseDS(bytes)
    case IS => parseIS(bytes).map(_.toDouble)
    case _ => Seq.empty
  }

  /**
    * @return this value as a sequence of `LocalDate`s. Casting is performed if necessary. If the value has no
    *         `LocalDate` representation, an empty sequence is returned.
    */
  def toDates(vr: VR = VR.DA): Seq[LocalDate] = vr match {
    case DA => parseDA(bytes)
    case DT => parseDT(bytes, systemZone).map(_.toLocalDate)
    case _ => Seq.empty
  }

  /**
    * @return this value as a sequence of `LocalTime`s. Casting is performed if necessary. If the value has no
    *         `LocalTime` representation, an empty sequence is returned.
    */
  def toTimes(vr: VR = VR.TM): Seq[LocalTime] = vr match {
    case DT => parseDT(bytes, systemZone).map(_.toLocalTime)
    case TM => parseTM(bytes)
    case _ => Seq.empty
  }

  /**
    * @return this value as a sequence of `ZonedDateTime`s. Casting is performed if necessary. If the value has no
    *         `ZonedDateTime` representation, an empty sequence is returned.
    */
  def toDateTimes(vr: VR = VR.DT, zoneOffset: ZoneOffset = systemZone): Seq[ZonedDateTime] = vr match {
    case DA => parseDA(bytes).map(_.atStartOfDay(zoneOffset))
    case DT => parseDT(bytes, zoneOffset)
    case _ => Seq.empty
  }

  /**
    * @return this value as a sequence of 'PatientName`s. Casting is performed if necessary. If the value has no
    *         `PatientName`` representation, an empty sequence is returned.
    */
  def toPatientNames(vr: VR = VR.PN, characterSets: CharacterSets = defaultCharacterSet): Seq[PatientName] = vr match {
    case PN => parsePN(bytes, characterSets)
    case _ => Seq.empty
  }

  /**
    * @return this value as an option of a `URI`. If the value has no `URI` representation, an empty option is
    *         returned.
    */
  def toURI(vr: VR = VR.UR): Option[URI] = vr match {
    case UR => parseUR(bytes)
    case _ => None
  }

  /**
    * @return the first string representation of this value, if any
    */
  def toString(vr: VR, bigEndian: Boolean = false, characterSets: CharacterSets = defaultCharacterSet): Option[String] =
    toStrings(vr, bigEndian, characterSets).headOption

  /**
    * Get the string representation of each component of this value, joined with the backslash character as separator
    *
    * @param characterSets Character sets used for string decoding
    * @return a string repreentation of all components of this value, if any
    */
  def toSingleString(vr: VR, bigEndian: Boolean = false, characterSets: CharacterSets = defaultCharacterSet): Option[String] = {
    val strings = toStrings(vr, bigEndian, characterSets)
    if (strings.isEmpty) None else Option(strings.mkString(multiValueDelimiter))
  }

  /**
    * @return a direct decoding to UTF8 from the bytes of this value
    */
  def toUtf8String: String = bytes.utf8String

  /**
    * @return the first short representation of this value, if any
    */
  def toShort(vr: VR, bigEndian: Boolean = false): Option[Short] = toShorts(vr, bigEndian).headOption

  /**
    * @return the first int representation of this value, if any
    */
  def toInt(vr: VR, bigEndian: Boolean = false): Option[Int] = toInts(vr, bigEndian).headOption

  /**
    * @return the first long representation of this value, if any
    */
  def toLong(vr: VR, bigEndian: Boolean = false): Option[Long] = toLongs(vr, bigEndian).headOption

  /**
    * @return the first long representation of this value, if any
    */
  def toVeryLong(vr: VR, bigEndian: Boolean = false): Option[BigInteger] = toVeryLongs(vr, bigEndian).headOption

  /**
    * @return the first float representation of this value, if any
    */
  def toFloat(vr: VR, bigEndian: Boolean = false): Option[Float] = toFloats(vr, bigEndian).headOption

  /**
    * @return the first double representation of this value, if any
    */
  def toDouble(vr: VR, bigEndian: Boolean = false): Option[Double] = toDoubles(vr, bigEndian).headOption

  /**
    * @return the first `LocalDate` representation of this value, if any
    */
  def toDate(vr: VR = VR.DA): Option[LocalDate] = toDates(vr).headOption

  /**
    * @return the first `LocalTime` representation of this value, if any
    */
  def toTime(vr: VR = VR.TM): Option[LocalTime] = toTimes(vr).headOption

  /**
    * @return the first `ZonedDateTime` representation of this value, if any
    */
  def toDateTime(vr: VR = VR.DT, zoneOffset: ZoneOffset = systemZone): Option[ZonedDateTime] = toDateTimes(vr, zoneOffset).headOption

  /**
    * @return the first `PatientName` representation of this value, if any
    */
  def toPatientName(vr: VR = VR.PN, characterSets: CharacterSets = defaultCharacterSet): Option[PatientName] = toPatientNames(vr, characterSets).headOption

  override def toString: String =
    s"Value [${bytes.length} bytes]"

  /**
    * @param bytes additional bytes
    * @return a new Value with bytes added
    */
  def ++(bytes: ByteString): Value = copy(bytes = this.bytes ++ bytes)

  /**
    * @return a new Value guaranteed to have even length, padded if necessary with the correct padding byte
    */
  def ensurePadding(vr: VR): Value = copy(bytes = padToEvenLength(bytes, vr))
}

object Value {

  final val multiValueDelimiterRegex = """\\"""

  private def combine(vr: VR, values: Seq[ByteString]): ByteString = vr match {
    case AT | FL | FD | SS | SL | SV | US | UL | UV | OB | OW | OL | OV | OF | OD => values.reduce(_ ++ _)
    case _ => if (values.isEmpty) ByteString.empty else values.tail.foldLeft(values.head)((bytes, b) => bytes ++ ByteString('\\') ++ b)
  }

  /**
    * A Value with empty value
    */
  val empty: Value = Value(ByteString.empty)

  /**
    * Create a new Value, padding the input if necessary to ensure even length
    *
    * @param bytes     value bytes
    * @return a new Value
    */
  def apply(vr: VR, bytes: ByteString): Value = Value(padToEvenLength(bytes, vr))

  private def toBytes(bi: BigInteger, length: Int, bigEndian: Boolean): ByteString = {
    val bytes = ByteString(bi.toByteArray)
    val out = ByteString(new Array[Byte](Math.max(0, length - bytes.length))) ++ bytes.takeRight(length)
    if (bigEndian) out else out.reverse
  }

  private def stringBytes(vr: VR, value: String, bigEndian: Boolean): ByteString = vr match {
    case AT => tagToBytes(Integer.parseInt(value, 16), bigEndian)
    case FL => floatToBytes(java.lang.Float.parseFloat(value), bigEndian)
    case FD => doubleToBytes(java.lang.Double.parseDouble(value), bigEndian)
    case SS => shortToBytes(java.lang.Short.parseShort(value), bigEndian)
    case SL => intToBytes(Integer.parseInt(value), bigEndian)
    case SV => longToBytes(java.lang.Long.parseLong(value), bigEndian)
    case US => truncate(2, intToBytes(java.lang.Integer.parseUnsignedInt(value), bigEndian), bigEndian)
    case UL => truncate(4, longToBytes(java.lang.Long.parseUnsignedLong(value), bigEndian), bigEndian)
    case UV => toBytes(new BigInteger(value), 8, bigEndian)
    case OB | OW | OL | OV | OF | OD => throw new IllegalArgumentException("Cannot create binary array from string")
    case _ => ByteString(value)
  }
  def fromString(vr: VR, value: String, bigEndian: Boolean = false): Value = apply(vr, stringBytes(vr, value, bigEndian))
  def fromStrings(vr: VR, values: Seq[String], bigEndian: Boolean = false): Value = apply(vr, combine(vr, values.map(stringBytes(vr, _, bigEndian))))

  private def shortBytes(vr: VR, value: Short, bigEndian: Boolean): ByteString = vr match {
    case FL => floatToBytes(value.toFloat, bigEndian)
    case FD => doubleToBytes(value.toDouble, bigEndian)
    case SS => shortToBytes(value, bigEndian)
    case SL => intToBytes(value.toInt, bigEndian)
    case SV => longToBytes(value.toLong, bigEndian)
    case US => truncate(2, intToBytes(java.lang.Short.toUnsignedInt(value), bigEndian), bigEndian)
    case UL => intToBytes(java.lang.Short.toUnsignedInt(value), bigEndian)
    case UV => longToBytes(java.lang.Short.toUnsignedInt(value).toLong, bigEndian)
    case OB | OW | OL | OV | OF | OD | AT => throw new IllegalArgumentException(s"Cannot create value of VR $vr from short")
    case _ => ByteString(value.toString)
  }
  def fromShort(vr: VR, value: Short, bigEndian: Boolean = false): Value = apply(vr, shortBytes(vr, value, bigEndian))
  def fromShorts(vr: VR, values: Seq[Short], bigEndian: Boolean = false): Value = apply(vr, combine(vr, values.map(shortBytes(vr, _, bigEndian))))

  private def intBytes(vr: VR, value: Int, bigEndian: Boolean): ByteString = vr match {
    case AT => tagToBytes(value, bigEndian)
    case FL => floatToBytes(value.toFloat, bigEndian)
    case FD => doubleToBytes(value.toDouble, bigEndian)
    case SS => shortToBytes(value.toShort, bigEndian)
    case SL => intToBytes(value, bigEndian)
    case SV => longToBytes(value.toLong, bigEndian)
    case US => truncate(6, longToBytes(Integer.toUnsignedLong(value), bigEndian), bigEndian)
    case UL => truncate(4, longToBytes(Integer.toUnsignedLong(value), bigEndian), bigEndian)
    case UV => longToBytes(Integer.toUnsignedLong(value), bigEndian)
    case OB | OW | OL | OV | OF | OD => throw new IllegalArgumentException(s"Cannot create value of VR $vr from int")
    case _ => ByteString(value.toString)
  }
  def fromInt(vr: VR, value: Int, bigEndian: Boolean = false): Value = apply(vr, intBytes(vr, value, bigEndian))
  def fromInts(vr: VR, values: Seq[Int], bigEndian: Boolean = false): Value = apply(vr, combine(vr, values.map(intBytes(vr, _, bigEndian))))

  private def longBytes(vr: VR, value: Long, bigEndian: Boolean): ByteString = vr match {
    case AT => tagToBytes(value.toInt, bigEndian)
    case FL => floatToBytes(value.toFloat, bigEndian)
    case FD => doubleToBytes(value.toDouble, bigEndian)
    case SS => shortToBytes(value.toShort, bigEndian)
    case SL => intToBytes(value.toInt, bigEndian)
    case SV => longToBytes(value, bigEndian)
    case US => truncate(6, longToBytes(value, bigEndian), bigEndian)
    case UL => truncate(4, longToBytes(value, bigEndian), bigEndian)
    case UV => toBytes(BigInteger.valueOf(value), 8, bigEndian)
    case OB | OW | OL | OF | OD => throw new IllegalArgumentException(s"Cannot create value of VR $vr from long")
    case _ => ByteString(value.toString)
  }
  def fromLong(vr: VR, value: Long, bigEndian: Boolean = false): Value = apply(vr, longBytes(vr, value, bigEndian))
  def fromLongs(vr: VR, values: Seq[Long], bigEndian: Boolean = false): Value = apply(vr, combine(vr, values.map(longBytes(vr, _, bigEndian))))

  private def veryLongBytes(vr: VR, value: BigInteger, bigEndian: Boolean): ByteString = vr match {
    case AT => tagToBytes(value.intValue, bigEndian)
    case FL => floatToBytes(value.floatValue, bigEndian)
    case FD => doubleToBytes(value.doubleValue, bigEndian)
    case SS => shortToBytes(value.shortValue, bigEndian)
    case SL => intToBytes(value.intValue, bigEndian)
    case SV => longToBytes(value.longValue, bigEndian)
    case US => toBytes(value, 2, bigEndian)
    case UL => toBytes(value, 4, bigEndian)
    case UV => toBytes(value, 8, bigEndian)
    case OB | OW | OL | OF | OD => throw new IllegalArgumentException(s"Cannot create value of VR $vr from long")
    case _ => ByteString(value.toString)
  }
  def fromVeryLong(vr: VR, value: BigInteger, bigEndian: Boolean = false): Value = apply(vr, veryLongBytes(vr, value, bigEndian))
  def fromVeryLongs(vr: VR, values: Seq[BigInteger], bigEndian: Boolean = false): Value = apply(vr, combine(vr, values.map(veryLongBytes(vr, _, bigEndian))))

  private def floatBytes(vr: VR, value: Float, bigEndian: Boolean): ByteString = vr match {
    case AT => tagToBytes(value.toInt, bigEndian)
    case FL => floatToBytes(value, bigEndian)
    case FD => doubleToBytes(value.toDouble, bigEndian)
    case SS => shortToBytes(value.toShort, bigEndian)
    case SL => intToBytes(value.toInt, bigEndian)
    case SV => longToBytes(value.toLong, bigEndian)
    case US => truncate(6, longToBytes(value.toLong, bigEndian), bigEndian)
    case UL => truncate(4, longToBytes(value.toLong, bigEndian), bigEndian)
    case UV => toBytes(BigDecimal.valueOf(value).toBigInteger, 8, bigEndian)
    case OB | OW | OL | OF | OD => throw new IllegalArgumentException(s"Cannot create value of VR $vr from float")
    case _ => ByteString(value.toString)
  }
  def fromFloat(vr: VR, value: Float, bigEndian: Boolean = false): Value = apply(vr, floatBytes(vr, value, bigEndian))
  def fromFloats(vr: VR, values: Seq[Float], bigEndian: Boolean = false): Value = apply(vr, combine(vr, values.map(floatBytes(vr, _, bigEndian))))

  private def doubleBytes(vr: VR, value: Double, bigEndian: Boolean): ByteString = vr match {
    case AT => tagToBytes(value.toInt, bigEndian)
    case FL => floatToBytes(value.toFloat, bigEndian)
    case FD => doubleToBytes(value, bigEndian)
    case SS => shortToBytes(value.toShort, bigEndian)
    case SL => intToBytes(value.toInt, bigEndian)
    case SV => longToBytes(value.toLong, bigEndian)
    case US => truncate(6, longToBytes(value.toLong, bigEndian), bigEndian)
    case UL => truncate(4, longToBytes(value.toLong, bigEndian), bigEndian)
    case UV => toBytes(BigDecimal.valueOf(value).toBigInteger, 8, bigEndian)
    case OB | OW | OL | OF | OD => throw new IllegalArgumentException(s"Cannot create value of VR $vr from double")
    case _ => ByteString(value.toString)
  }
  def fromDouble(vr: VR, value: Double, bigEndian: Boolean = false): Value = apply(vr, doubleBytes(vr, value, bigEndian))
  def fromDoubles(vr: VR, values: Seq[Double], bigEndian: Boolean = false): Value = apply(vr, combine(vr, values.map(doubleBytes(vr, _, bigEndian))))

  private def dateBytes(vr: VR, value: LocalDate): ByteString = vr match {
    case AT | FL | FD | SS | SL | SV | US | UL | UV | OB | OW | OL | OV | OF | OD => throw new IllegalArgumentException(s"Cannot create value of VR $vr from date")
    case _ => ByteString(formatDate(value))
  }
  def fromDate(vr: VR, value: LocalDate): Value = apply(vr, dateBytes(vr, value))
  def fromDates(vr: VR, values: Seq[LocalDate]): Value = apply(vr, combine(vr, values.map(dateBytes(vr, _))))

  private def timeBytes(vr: VR, value: LocalTime): ByteString = vr match {
    case AT | FL | FD | SS | SL | SV | US | UL | UV | OB | OW | OL | OV | OF | OD => throw new IllegalArgumentException(s"Cannot create value of VR $vr from time")
    case _ => ByteString(formatTime(value))
  }
  def fromTime(vr: VR, value: LocalTime): Value = apply(vr, timeBytes(vr, value))
  def fromTimes(vr: VR, values: Seq[LocalTime]): Value = apply(vr, combine(vr, values.map(timeBytes(vr, _))))

  private def dateTimeBytes(vr: VR, value: ZonedDateTime): ByteString = vr match {
    case AT | FL | FD | SS | SL | SV | US | UL | UV | OB | OW | OL | OV | OF | OD => throw new IllegalArgumentException(s"Cannot create value of VR $vr from date-time")
    case _ => ByteString(formatDateTime(value))
  }
  def fromDateTime(vr: VR, value: ZonedDateTime): Value = apply(vr, dateTimeBytes(vr, value))
  def fromDateTimes(vr: VR, values: Seq[ZonedDateTime]): Value = apply(vr, combine(vr, values.map(dateTimeBytes(vr, _))))

  private def patientNameBytes(vr: VR, value: PatientName): ByteString = vr match {
    case PN => ByteString(value.toString)
    case _ => throw new IllegalArgumentException(s"Cannot create value of VR $vr from date-time")
  }
  def fromPatientName(vr: VR, value: PatientName): Value = apply(vr, patientNameBytes(vr, value))
  def fromPatientNames(vr: VR, values: Seq[PatientName]): Value = apply(vr, combine(vr, values.map(patientNameBytes(vr, _))))

  private def uriBytes(vr: VR, value: URI): ByteString = vr match {
    case UR => ByteString(value.toString)
    case _ => throw new IllegalArgumentException(s"Cannot create value of VR $vr from URI")
  }
  def fromURI(vr: VR, value: URI): Value = apply(vr, uriBytes(vr, value))

  // parsing of value bytes for various value representations to higher types

  def split(bytes: ByteString, size: Int): Seq[ByteString] = bytes.grouped(size).filter(_.length == size).toSeq
  def split(s: String): Seq[String] = s.split(multiValueDelimiterRegex)

  def trim(s: String): String = s.trim
  def trimPadding(s: String, paddingByte: Byte): String = {
    var index = s.length - 1
    while (index >= 0 && s(index) <= paddingByte)
      index -= 1
    val n = s.length - 1 - index
    if (n > 0) s.dropRight(n) else s
  }

  def parseAT(value: ByteString, bigEndian: Boolean): Seq[Int] = split(value, 4).map(b => bytesToTag(b, bigEndian))
  def parseSL(value: ByteString, bigEndian: Boolean): Seq[Int] = split(value, 4).map(bytesToInt(_, bigEndian))
  def parseSV(value: ByteString, bigEndian: Boolean): Seq[Long] = split(value, 8).map(bytesToLong(_, bigEndian))
  def parseUL(value: ByteString, bigEndian: Boolean): Seq[Long] = parseSL(value, bigEndian).map(intToUnsignedLong)
  def parseUV(value: ByteString, bigEndian: Boolean): Seq[BigInteger] = split(value, 8).map(bytes => new BigInteger(1, if (bigEndian) bytes.toArray else bytes.reverse.toArray))
  def parseSS(value: ByteString, bigEndian: Boolean): Seq[Short] = split(value, 2).map(bytesToShort(_, bigEndian))
  def parseUS(value: ByteString, bigEndian: Boolean): Seq[Int] = parseSS(value, bigEndian).map(shortToUnsignedInt)
  def parseFL(value: ByteString, bigEndian: Boolean): Seq[Float] = split(value, 4).map(bytesToFloat(_, bigEndian))
  def parseFD(value: ByteString, bigEndian: Boolean): Seq[Double] = split(value, 8).map(bytesToDouble(_, bigEndian))
  def parseDS(value: ByteString): Seq[Double] = split(value.utf8String).map(trim).flatMap(s => try Option(java.lang.Double.parseDouble(s)) catch {
    case _: Throwable => None
  })
  def parseIS(value: ByteString): Seq[Long] = split(value.utf8String).map(trim).flatMap(s => try Option(java.lang.Long.parseLong(s)) catch {
    case _: Throwable => None
  })
  def parseDA(value: ByteString): Seq[LocalDate] = split(value.utf8String).flatMap(parseDate)
  def parseTM(value: ByteString): Seq[LocalTime] = split(value.utf8String).flatMap(parseTime)
  def parseDT(value: ByteString, zoneOffset: ZoneOffset): Seq[ZonedDateTime] = split(value.utf8String).flatMap(parseDateTime(_, zoneOffset))
  def parsePN(string: String): Seq[PatientName] = split(string).map(trimPadding(_, VR.PN.paddingByte)).flatMap(parsePatientName)
  def parsePN(value: ByteString, characterSets: CharacterSets): Seq[PatientName] = parsePN(characterSets.decode(VR.PN, value))
  def parseUR(string: String): Option[URI] = parseURI(string)
  def parseUR(value: ByteString): Option[URI] = parseUR(trimPadding(value.utf8String, VR.UR.paddingByte))

  // parsing of strings to more specific types

  final val dateFormat = new DateTimeFormatterBuilder()
    .appendValue(YEAR, 4, 4, SignStyle.EXCEEDS_PAD)
    .appendPattern("['.']MM['.']dd")
    .toFormatter

  final val timeFormat = new DateTimeFormatterBuilder()
    .appendValue(HOUR_OF_DAY, 2, 2, SignStyle.EXCEEDS_PAD)
    .appendPattern("[[':']mm[[':']ss[")
    .appendFraction(MICRO_OF_SECOND, 1, 6, true)
    .appendPattern("]]]")
    .parseDefaulting(MINUTE_OF_HOUR, 0)
    .parseDefaulting(SECOND_OF_MINUTE, 0)
    .parseDefaulting(MICRO_OF_SECOND, 0)
    .toFormatter

  final val dateTimeFormat = new DateTimeFormatterBuilder()
    .appendValue(YEAR, 4, 4, SignStyle.EXCEEDS_PAD)
    .appendPattern("[MM[dd[HH[mm[ss[")
    .appendFraction(MICRO_OF_SECOND, 1, 6, true)
    .appendPattern("]]]]]]")
    .parseDefaulting(MONTH_OF_YEAR, 1)
    .parseDefaulting(DAY_OF_MONTH, 1)
    .parseDefaulting(HOUR_OF_DAY, 0)
    .parseDefaulting(MINUTE_OF_HOUR, 0)
    .parseDefaulting(SECOND_OF_MINUTE, 0)
    .parseDefaulting(MICRO_OF_SECOND, 0)
    .toFormatter

  final val dateTimeZoneFormat = new DateTimeFormatterBuilder()
    .append(dateTimeFormat)
    .appendPattern("[Z]")
    .toFormatter

  final val dateFormatForEncoding = DateTimeFormatter.ofPattern("uuuuMMdd")
  final val timeFormatForEncoding = DateTimeFormatter.ofPattern("HHmmss'.'SSSSSS")

  def formatDate(date: LocalDate): String = date.format(dateFormatForEncoding)
  def formatTime(time: LocalTime): String = time.format(timeFormatForEncoding)
  def formatDateTime(dateTime: ZonedDateTime): String = dateTime.format(dateTimeZoneFormat)

  def parseDate(s: String): Option[LocalDate] =
    try Option(LocalDate.parse(s.trim, dateFormat)) catch {
      case _: Throwable => None
    }

  def parseTime(s: String): Option[LocalTime] =
    try Option(LocalTime.parse(s.trim, timeFormat)) catch {
      case _: Throwable => None
    }

  def parseDateTime(s: String, zoneOffset: ZoneOffset): Option[ZonedDateTime] = {
    val trimmed = s.trim
    try Option(ZonedDateTime.parse(trimmed, dateTimeZoneFormat)) catch {
      case _: Throwable => try Option(LocalDateTime.parse(trimmed, dateTimeFormat).atZone(zoneOffset)) catch {
        case _: Throwable => None
      }
    }
  }

  def parsePatientName(s: String): Option[PatientName] = {
    def ensureLength(ss: Seq[String], n: Int) = ss ++ Seq.fill(math.max(0, n - ss.length))("")

    val comps = ensureLength(s.split("""\^"""), 5)
      .map(s => ensureLength(s.split("="), 3).map(trim))
      .map(c => ComponentGroup(c.head, c(1), c(2)))

    Option(PatientName(comps.head, comps(1), comps(2), comps(3), comps(4)))
  }

  def parseURI(s: String): Option[URI] = try Option(new URI(s)) catch { case _: Throwable => None }
}
