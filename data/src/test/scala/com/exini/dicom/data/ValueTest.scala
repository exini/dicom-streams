package com.exini.dicom.data

import java.math.BigInteger
import java.time.{ LocalDate, LocalTime, ZoneOffset, ZonedDateTime }

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ValueTest extends AnyFlatSpec with Matchers {
  import Value._

  "Formatting bytes into multiple strings" should "return empty sequence for empty byte string" in {
    Value.empty.toStrings(VR.SH) shouldBe Seq.empty
  }

  it should "throw an exception for null input" in {
    intercept[NullPointerException] {
      Value(null).toStrings(VR.SH)
    }
  }

  it should "split a string according to the DICOM multiple value delimiter" in {
    Value("one\\two\\three".utf8Bytes).toStrings(VR.SH) shouldBe Seq("one", "two", "three")
  }

  it should "trim any characters at beginning and end" in {
    Value(bytesi(0x20, 0x20, 0x20, 0x20, 0x41, 0x41, 0x20, 0x20, 0x20)).toStrings(VR.SH) shouldBe Seq("AA")
  }

  it should "trim any characters at or below 0x20 at beginning and end of each value" in {
    Value("  one \\ two \\three  ".utf8Bytes).toStrings(VR.SH) shouldBe Seq("one", "two", "three")
  }

  it should "split and trim strings with multiple character set encodings" in {
    val nameBytes = bytesi(0x20, 0xd4, 0xcf, 0xc0, 0xde, 0x5c, 0x20, 0xc0, 0xdb, 0xb3, 0x3d, 0x1b, 0x24, 0x42, 0x3b,
      0x33, 0x45, 0x44, 0x1b, 0x28, 0x4a, 0x5c, 0x1b, 0x24, 0x42, 0x42, 0x40, 0x4f, 0x3a, 0x1b, 0x28, 0x4a, 0x3d, 0x1b,
      0x24, 0x42, 0x24, 0x64, 0x24, 0x5e, 0x24, 0x40, 0x1b, 0x28, 0x4a, 0x5c, 0x20, 0x1b, 0x24, 0x42, 0x24, 0x3f, 0x24,
      0x6d, 0x24, 0x26, 0x1b, 0x28, 0x4a)
    Value(nameBytes).toStrings(
      VR.SH,
      characterSets = new CharacterSets(Seq("ISO 2022 IR 13", "ISO 2022 IR 87"))
    ) shouldBe Seq("ﾔﾏﾀﾞ", "ﾀﾛｳ=山田", "太郎=やまだ", "たろう")
  }

  "Formatting bytes into a single string" should "return empty string for empty byte string" in {
    Value.empty.toString(VR.SH) shouldBe empty
  }

  it should "not split a string with DICOM multiple value delimiters" in {
    Value("one\\two\\three".utf8Bytes).toSingleString(VR.SH) shouldBe Some("one\\two\\three")
  }

  it should "trim the string components" in {
    Value("   one two  ".utf8Bytes).toSingleString(VR.SH) shouldBe Some("one two")
  }

  "Parsing int values" should "return empty sequence for empty byte string" in {
    Value.empty.toInts(VR.SL) shouldBe Seq.empty
  }

  it should "parse multiple int values" in {
    Value(intToBytesLE(1234) ++ intToBytesLE(1234567890)).toInts(VR.SL) shouldBe Seq(1234, 1234567890)
  }

  it should "return int values for all numerical VRs" in {
    Value(floatToBytes(math.Pi.toFloat)).toInts(VR.FL) shouldBe Seq(3)
    Value(doubleToBytes(math.Pi)).toInts(VR.FD) shouldBe Seq(3)
    Value(shortToBytes(-3)).toInts(VR.SS) shouldBe Seq(-3)
    Value(shortToBytes(-3)).toInts(VR.US) shouldBe Seq((1 << 16) - 3)
    Value(intToBytes(-3)).toInts(VR.SL) shouldBe Seq(-3)
    Value(intToBytes(-3)).toInts(VR.UL) shouldBe Seq(-3)
    Value(longToBytes(-3)).toInts(VR.SV) shouldBe Seq(-3)
    Value(longToBytes(3)).toInts(VR.UV) shouldBe Seq(3)
    Value("3.1415".utf8Bytes).toInts(VR.DS) shouldBe Seq(3)
    Value("-3".utf8Bytes).toInts(VR.IS) shouldBe Seq(-3)
    Value("-3".utf8Bytes).toInts(VR.AT) shouldBe empty
  }

  "Parsing a single int value" should "return the first entry among multiple values" in {
    Value(intToBytesLE(1234) ++ intToBytesLE(1234567890)).toInt(VR.SL) shouldBe Some(1234)
  }

  it should "return None if no entry exists" in {
    Value(emptyBytes).toInt(VR.SL) shouldBe None
  }

  "Parsing long values" should "return empty sequence for empty byte string" in {
    Value(emptyBytes).toLongs(VR.SL) shouldBe Seq.empty
  }

  it should "parse multiple long values" in {
    Value(intToBytesLE(1234) ++ intToBytesLE(1234567890)).toLongs(VR.SL) shouldBe Seq(1234L, 1234567890L)
  }

  it should "return long values for all numerical VRs" in {
    Value(floatToBytes(math.Pi.toFloat)).toLongs(VR.FL) shouldBe Seq(3L)
    Value(doubleToBytes(math.Pi)).toLongs(VR.FD) shouldBe Seq(3L)
    Value(shortToBytes(-3)).toLongs(VR.SS) shouldBe Seq(-3L)
    Value(shortToBytes(-3)).toLongs(VR.US) shouldBe Seq((1 << 16) - 3L)
    Value(intToBytes(-3)).toLongs(VR.SL) shouldBe Seq(-3L)
    Value(intToBytes(-3)).toLongs(VR.UL) shouldBe Seq((1L << 32) - 3L)
    Value(longToBytes(-3)).toLongs(VR.SV) shouldBe Seq(-3L)
    Value(longToBytes(3)).toLongs(VR.UV) shouldBe Seq(3L)
    Value("3.1415".utf8Bytes).toLongs(VR.DS) shouldBe Seq(3L)
    Value("-3".utf8Bytes).toLongs(VR.IS) shouldBe Seq(-3L)
    Value(VR.AT, "-3".utf8Bytes).toLongs(VR.AT) shouldBe empty
  }

  "Parsing a single long value" should "return the first entry among multiple values" in {
    Value(longToBytesLE(1234L) ++ longToBytesLE(1234567890L)).toLong(VR.SL) shouldBe Some(1234L)
  }

  it should "return None if no entry exists" in {
    Value(emptyBytes).toLong(VR.SL) shouldBe None
  }

  "Parsing very long values" should "return empty sequence for empty byte string" in {
    Value(emptyBytes).toLongs(VR.UV) shouldBe Seq.empty
  }

  it should "parse multiple long values" in {
    Value(bytesi(1, 2, 3, 4, 5, 6, 7, 8) ++ bytesi(1, 2, 1, 2, 1, 2, 1, 2))
      .toVeryLongs(VR.UV, bigEndian = true) shouldBe
      Seq(
        new BigInteger(1, bytesi(1, 2, 3, 4, 5, 6, 7, 8).unwrap),
        new BigInteger(1, bytesi(1, 2, 1, 2, 1, 2, 1, 2).unwrap)
      )
  }

  it should "return long values for all numerical VRs" in {
    Value(floatToBytes(math.Pi.toFloat)).toVeryLongs(VR.FL) shouldBe Seq(BigInteger.valueOf(3))
    Value(doubleToBytes(math.Pi)).toVeryLongs(VR.FD) shouldBe Seq(BigInteger.valueOf(3))
    Value(shortToBytes(-3)).toVeryLongs(VR.SS) shouldBe Seq(BigInteger.valueOf(-3))
    Value(shortToBytes(-3)).toVeryLongs(VR.US) shouldBe Seq(BigInteger.valueOf((1 << 16) - 3L))
    Value(intToBytes(-3)).toVeryLongs(VR.SL) shouldBe Seq(BigInteger.valueOf(-3))
    Value(intToBytes(-3)).toVeryLongs(VR.UL) shouldBe Seq(BigInteger.valueOf((1L << 32) - 3L))
    Value(longToBytes(-3)).toVeryLongs(VR.SV) shouldBe Seq(BigInteger.valueOf(-3))
    Value(longToBytes(3)).toVeryLongs(VR.UV) shouldBe Seq(BigInteger.valueOf(3L))
    Value("3.1415".utf8Bytes).toVeryLongs(VR.DS) shouldBe Seq(BigInteger.valueOf(3))
    Value("-3".utf8Bytes).toVeryLongs(VR.IS) shouldBe Seq(BigInteger.valueOf(-3))
    Value(VR.AT, "-3".utf8Bytes).toVeryLongs(VR.AT) shouldBe empty
  }

  "Parsing a single very long value" should "return the first entry among multiple values" in {
    Value(bytesi(1, 2, 3, 4, 5, 6, 7, 8) ++ bytesi(1, 2, 1, 2, 1, 2, 1, 2))
      .toVeryLong(VR.UV, bigEndian = true) shouldBe
      Some(new BigInteger(1, bytesi(1, 2, 3, 4, 5, 6, 7, 8).unwrap))
  }

  it should "return None if no entry exists" in {
    Value(emptyBytes).toVeryLong(VR.UV) shouldBe None
  }

  "Parsing short values" should "return empty sequence for empty byte string" in {
    Value(emptyBytes).toShorts(VR.SS) shouldBe Seq.empty
  }

  it should "parse short values" in {
    Value(shortToBytesLE(1234) ++ shortToBytesLE(12345)).toShorts(VR.SS) shouldBe Seq(1234.toShort, 12345.toShort)
  }

  it should "return short values for all numerical VRs" in {
    Value(floatToBytes(math.Pi.toFloat)).toShorts(VR.FL) shouldBe Seq(3.toShort)
    Value(doubleToBytes(math.Pi)).toShorts(VR.FD) shouldBe Seq(3.toShort)
    Value(shortToBytes(-3)).toShorts(VR.SS) shouldBe Seq(-3.toShort)
    Value(shortToBytes(-3)).toShorts(VR.US) shouldBe Seq(-3.toShort)
    Value(intToBytes(-3)).toShorts(VR.SL) shouldBe Seq(-3.toShort)
    Value(intToBytes(-3)).toShorts(VR.UL) shouldBe Seq(-3.toShort)
    Value(longToBytes(-3)).toShorts(VR.SV) shouldBe Seq(-3.toShort)
    Value(longToBytes(3)).toShorts(VR.UV) shouldBe Seq(3.toShort)
    Value("3.1415".utf8Bytes).toShorts(VR.DS) shouldBe Seq(3.toShort)
    Value("-3".utf8Bytes).toShorts(VR.IS) shouldBe Seq(-3.toShort)
    Value("-3".utf8Bytes).toShorts(VR.AT) shouldBe empty
  }

  "Parsing a single short value" should "return the first entry among multiple values" in {
    Value(shortToBytesLE(1234) ++ shortToBytesLE(12345)).toShort(VR.SS) shouldBe Some(1234.toShort)
  }

  it should "return None if no entry exists" in {
    Value(emptyBytes).toShort(VR.SS) shouldBe None
  }

  "Parsing float values" should "return empty sequence for empty byte string" in {
    Value(emptyBytes).toFloats(VR.FL) shouldBe Seq.empty
  }

  it should "parse float values" in {
    Value(floatToBytes(1234f) ++ floatToBytes(1.234f)).toFloats(VR.FL) shouldBe Seq(1234f, 1.234f)
  }

  it should "return float values for all numerical VRs" in {
    Value(floatToBytes(math.Pi.toFloat)).toFloats(VR.FL) shouldBe Seq(math.Pi.toFloat)
    Value(doubleToBytes(math.Pi)).toFloats(VR.FD) shouldBe Seq(math.Pi.toFloat)
    Value(shortToBytes(-3)).toFloats(VR.SS) shouldBe Seq(-3.toFloat)
    Value(shortToBytes(-3)).toFloats(VR.US) shouldBe Seq(((1 << 16) - 3).toFloat)
    Value(intToBytes(-3)).toFloats(VR.SL) shouldBe Seq(-3.toFloat)
    Value(intToBytes(-3)).toFloats(VR.UL) shouldBe Seq(((1L << 32) - 3).toFloat)
    Value(longToBytes(-3)).toFloats(VR.SV) shouldBe Seq(-3.toFloat)
    Value(longToBytes(3)).toFloats(VR.UV) shouldBe Seq(3.toFloat)
    Value("3.1415".utf8Bytes).toFloats(VR.DS) shouldBe Seq(3.1415.toFloat)
    Value("-3".utf8Bytes).toFloats(VR.IS) shouldBe Seq(-3.toFloat)
    Value("-3".utf8Bytes).toFloats(VR.AT) shouldBe empty
  }

  "Parsing a single float value" should "return the first entry among multiple values" in {
    Value(floatToBytes(1234f) ++ floatToBytes(1.234f)).toFloat(VR.FL) shouldBe Some(1234f)
  }

  it should "return None if no entry exists" in {
    Value(emptyBytes).toFloat(VR.FL) shouldBe None
  }

  "Parsing double values" should "return empty sequence for empty byte string" in {
    Value(emptyBytes).toDoubles(VR.FD) shouldBe Seq.empty
  }

  it should "parse double values" in {
    Value(doubleToBytes(1234) ++ doubleToBytes(1.234)).toDoubles(VR.FD) shouldBe Seq(1234.0, 1.234)
  }

  it should "return double values for all numerical VRs" in {
    Value(floatToBytes(math.Pi.toFloat)).toDoubles(VR.FL) shouldBe Seq(math.Pi.toFloat.toDouble)
    Value(doubleToBytes(math.Pi)).toDoubles(VR.FD) shouldBe Seq(math.Pi)
    Value(shortToBytes(-3)).toDoubles(VR.SS) shouldBe Seq(-3.0)
    Value(shortToBytes(-3)).toDoubles(VR.US) shouldBe Seq((1 << 16) - 3.0)
    Value(intToBytes(-3)).toDoubles(VR.SL) shouldBe Seq(-3.0)
    Value(intToBytes(-3)).toDoubles(VR.UL) shouldBe Seq((1L << 32) - 3.0)
    Value(longToBytes(-3)).toDoubles(VR.SV) shouldBe Seq(-3.0)
    Value(longToBytes(3)).toDoubles(VR.UV) shouldBe Seq(3.0)
    Value("3.1415".utf8Bytes).toDoubles(VR.DS) shouldBe Seq(3.1415)
    Value("-3".utf8Bytes).toDoubles(VR.IS) shouldBe Seq(-3.0)
    Value("-3".utf8Bytes).toDoubles(VR.AT) shouldBe empty
  }

  "Parsing a single double value" should "return the first entry among multiple values" in {
    Value(doubleToBytes(1234) ++ doubleToBytes(1.234)).toDouble(VR.FD) shouldBe Some(1234.0)
  }

  it should "return None if no entry exists" in {
    Value(emptyBytes).toDouble(VR.FD) shouldBe None
  }

  "Parsing date strings" should "return empty sequence for empty byte string" in {
    Value(emptyBytes).toDates(VR.DA) shouldBe Seq.empty
  }

  it should "parse properly formatted date strings" in {
    val date = LocalDate.of(2004, 3, 29)
    Value("20040329\\2004.03.29".utf8Bytes).toDates(VR.DA) shouldBe Seq(date, date)
  }

  it should "ignore improperly formatted entries" in {
    val date = LocalDate.of(2004, 3, 29)
    Value("20040329\\one\\2004.03.29".utf8Bytes).toDates(VR.DA) shouldBe Seq(date, date)
    Value("one".utf8Bytes).toDates(VR.DA) shouldBe Seq.empty
  }

  it should "trim whitespace" in {
    val date = LocalDate.of(2004, 3, 29)
    Value(" 20040329 \\20040329 \\one\\2004.03.29  ".utf8Bytes)
      .toDates(VR.DA) shouldBe Seq(date, date, date)
  }

  "Parsing a single date string" should "return the first valid entry among multiple values" in {
    val date = LocalDate.of(2004, 3, 29)
    Value("one\\20040329\\20050401".utf8Bytes).toDate(VR.DA) shouldBe Some(date)
  }

  "Parsing time strings" should "return empty sequence for empty byte string" in {
    Value(emptyBytes).toTimes(VR.TM) shouldBe Seq.empty
  }

  it should "parse partial time strings" in {
    val hh      = LocalTime.of(1, 0)
    val hhmm    = LocalTime.of(1, 2)
    val hhmmss  = LocalTime.of(1, 2, 3)
    val hhmmssS = LocalTime.of(1, 2, 3, 400000000)
    Value("01\\0102\\010203\\010203.400000".utf8Bytes).toTimes(VR.TM) shouldBe Seq(hh, hhmm, hhmmss, hhmmssS)
  }

  it should "parse properly formatted time strings" in {
    val time = LocalTime.of(10, 9, 8, 765432000)
    Value("100908.765432\\10:09:08.765432".utf8Bytes).toTimes(VR.TM) shouldBe Seq(time, time)
  }

  it should "ignore improperly formatted entries" in {
    val time = LocalTime.of(10, 9, 8, 765432000)
    Value("100908.765432\\one\\10:09:08.765432".utf8Bytes).toTimes(VR.TM) shouldBe Seq(time, time)
    Value("one".utf8Bytes).toTimes(VR.TM) shouldBe Seq.empty
  }

  it should "trim whitespace" in {
    val time = LocalTime.of(10, 9, 8, 765432000)
    Value(" 100908.765432 \\100908.765432 \\one\\10:09:08.765432  ".utf8Bytes)
      .toTimes(VR.TM) shouldBe Seq(time, time, time)
  }

  "Parsing a single time string" should "return the first valid entry among multiple values" in {
    val time = LocalTime.of(10, 9, 8, 765432000)
    Value("one\\100908.765432\\100908.765432".utf8Bytes).toTime(VR.TM) shouldBe Some(time)
  }

  "Parsing date time strings" should "return empty sequence for empty byte string" in {
    Value(emptyBytes).toDateTimes(VR.DT) shouldBe Seq.empty
  }

  it should "parse partial date time strings" in {
    val zone             = ZonedDateTime.now().getOffset
    val yyyy             = ZonedDateTime.of(2004, 1, 1, 0, 0, 0, 0, zone)
    val yyyyMM           = ZonedDateTime.of(2004, 3, 1, 0, 0, 0, 0, zone)
    val yyyyMMdd         = ZonedDateTime.of(2004, 3, 29, 0, 0, 0, 0, zone)
    val yyyyMMddHH       = ZonedDateTime.of(2004, 3, 29, 11, 0, 0, 0, zone)
    val yyyyMMddHHmm     = ZonedDateTime.of(2004, 3, 29, 11, 59, 0, 0, zone)
    val yyyyMMddHHmmss   = ZonedDateTime.of(2004, 3, 29, 11, 59, 35, 0, zone)
    val yyyyMMddHHmmssS  = ZonedDateTime.of(2004, 3, 29, 11, 59, 35, 123456000, zone)
    val yyyyMMddHHmmssSZ = ZonedDateTime.of(2004, 3, 29, 11, 59, 35, 123456000, ZoneOffset.UTC)
    Value(
      "2004\\200403\\20040329\\2004032911\\200403291159\\20040329115935\\20040329115935.123456\\20040329115935.123456+0000\\20040329115935.123456-0000".utf8Bytes
    ).toDateTimes(VR.DT) shouldBe Seq(
      yyyy,
      yyyyMM,
      yyyyMMdd,
      yyyyMMddHH,
      yyyyMMddHHmm,
      yyyyMMddHHmmss,
      yyyyMMddHHmmssS,
      yyyyMMddHHmmssSZ,
      yyyyMMddHHmmssSZ
    )
  }

  it should "ignore improperly formatted entries" in {
    Value(
      "200\\2004ab\\20040\\2004032\\200403291\\20040329115\\2004032911593\\200403291159356\\20040329115935.1234567\\20040329115935.12345+000\\20040329115935.123456+00000".utf8Bytes
    ).toDateTimes(VR.DT) shouldBe empty
  }

  it should "allow time zone also with null components" in {
    val dateTime = ZonedDateTime.of(2004, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+0500"))
    Value("2004+0500".utf8Bytes).toDateTime(VR.DT) shouldBe Some(dateTime)
  }

  it should "trim whitespace" in {
    val dateTime = ZonedDateTime.of(2004, 3, 29, 5, 35, 59, 12345000, ZoneOffset.UTC)
    Value(
      " 20040329053559.012345+0000 \\20040329053559.012345+0000 \\one\\20040329053559.012345+0000  ".utf8Bytes
    ).toDateTimes(VR.DT) shouldBe Seq(dateTime, dateTime, dateTime)
  }

  it should "parse time zones" in {
    val dateTime = ZonedDateTime.of(2004, 3, 29, 5, 35, 59, 12345000, ZoneOffset.ofHours(3))
    Value("20040329053559.012345+0300".utf8Bytes).toDateTime(VR.DT) shouldBe Some(dateTime)
  }

  "Parsing a single date time string" should "return the first valid entry among multiple values" in {
    val dateTime = ZonedDateTime.of(2004, 3, 29, 5, 35, 59, 12345000, ZoneOffset.UTC)
    Value("one\\20040329053559.012345+0000\\20050329053559.012345+0000".utf8Bytes)
      .toDateTime(VR.DT) shouldBe Some(
      dateTime
    )
  }

  "String representations of elements" should "format OW values" in {
    Value(bytesi(1, 2, 3, 4, 5, 6, 7, 8)).toString(VR.OW) shouldBe Some("0201 0403 0605 0807")
    Value(bytesi(1, 2, 3, 4, 5, 6, 7, 8)).toString(VR.OW, bigEndian = true) shouldBe Some(
      "0102 0304 0506 0708"
    )
  }

  it should "format OB values" in {
    Value(bytesi(1, 2, 3, 4, 5, 6, 7, 8)).toString(VR.OB) shouldBe Some(
      "01 02 03 04 05 06 07 08"
    )
  }

  it should "format OL values" in {
    Value(bytesi(1, 2, 3, 4, 5, 6, 7, 8)).toString(VR.OL) shouldBe Some("04030201 08070605")
    Value(bytesi(1, 2, 3, 4, 5, 6, 7, 8)).toString(VR.OL, bigEndian = true) shouldBe Some(
      "01020304 05060708"
    )
  }

  it should "format OV values" in {
    Value(bytesi(1, 2, 3, 4, 5, 6, 7, 8)).toString(VR.OV) shouldBe Some("0807060504030201")
    Value(bytesi(1, 2, 3, 4, 5, 6, 7, 8)).toString(VR.OV, bigEndian = true) shouldBe Some(
      "0102030405060708"
    )
  }

  it should "format OF values" in {
    Value(intToBytesLE(java.lang.Float.floatToIntBits(1.2f)) ++ intToBytesLE(java.lang.Float.floatToIntBits(56.78f)))
      .toString(VR.OF) shouldBe Some("1.2 56.78")
  }

  it should "format OD values" in {
    Value(
      longToBytesLE(java.lang.Double.doubleToLongBits(1.2)) ++ longToBytesLE(java.lang.Double.doubleToLongBits(56.78))
    ).toString(VR.OD) shouldBe Some("1.2 56.78")
  }

  it should "format AT values" in {
    Value(bytesi(1, 2, 3, 4)).toString(VR.AT) shouldBe Some("(0201,0403)")
  }

  it should "format US values" in {
    Value(bytesi(1, 2)).toString(VR.US) shouldBe Some(0x0201.toString)
    Value(bytesi(255, 255)).toString(VR.US) shouldBe Some(0xffff.toString)
  }

  it should "format UL values" in {
    Value(bytesi(1, 2, 3, 4)).toString(VR.UL) shouldBe Some(0x04030201.toString)
    Value(bytesi(255, 255, 255, 255)).toString(VR.UL) shouldBe Some(0xffffffffL.toString)
  }

  it should "format UV values" in {
    Value(bytesi(1, 2, 3, 4, 5, 6, 7, 8)).toString(VR.UV) shouldBe Some(
      0x0807060504030201L.toString
    )
    Value(bytesi(255, 255, 255, 255, 255, 255, 255, 255)).toString(VR.UV) shouldBe Some(
      "18446744073709551615"
    )
  }

  it should "format SS values" in {
    Value(bytesi(1, 2)).toString(VR.SS) shouldBe Some(0x0201.toString)
    Value(bytesi(255, 255)).toString(VR.SS) shouldBe Some("-1")
  }

  it should "format SL values" in {
    Value(bytesi(1, 2, 3, 4)).toString(VR.SL) shouldBe Some(0x04030201.toString)
    Value(bytesi(255, 255, 255, 255)).toString(VR.SL) shouldBe Some("-1")
  }

  it should "format SV values" in {
    Value(bytesi(1, 2, 3, 4, 5, 6, 7, 8)).toString(VR.SV) shouldBe Some(
      0x0807060504030201L.toString
    )
    Value(bytesi(255, 255, 255, 255, 255, 255, 255, 255)).toString(VR.SV) shouldBe Some("-1")
  }

  it should "format FL values" in {
    Value(intToBytesLE(java.lang.Float.floatToIntBits(math.Pi.toFloat))).toString(VR.FL) shouldBe Some(
      math.Pi.toFloat.toString
    )
  }

  it should "format FD values" in {
    Value(longToBytesLE(java.lang.Double.doubleToLongBits(math.Pi))).toString(VR.FD) shouldBe Some(math.Pi.toString)
  }

  it should "format ST values" in {
    val e = Value("   Short text   \\   and some more   ".utf8Bytes)
    e.toStrings(VR.ST) should have length 1
    e.toSingleString(VR.ST) shouldBe Some("   Short text   \\   and some more")
  }

  it should "format DT values" in {
    val dt = "20040329053559.012345+0300"
    Value(dt.utf8Bytes).toString(VR.DT) shouldBe Some(dt)
  }

  "Parsing a patient name" should "divide into parts and components" in {
    Value(
      "aFamily^aGiven^aMiddle^aPrefix^aSuffix=iFamily^iGiven^iMiddle^iPrefix^iSuffix=pFamily^pGiven^pMiddle^pPrefix^pSuffix".utf8Bytes
    ).toPersonNames() shouldBe Seq(
      PersonName(
        ComponentGroup("aFamily", "iFamily", "pFamily"),
        ComponentGroup("aGiven", "iGiven", "pGiven"),
        ComponentGroup("aMiddle", "iMiddle", "pMiddle"),
        ComponentGroup("aPrefix", "iPrefix", "pPrefix"),
        ComponentGroup("aSuffix", "iSuffix", "pSuffix")
      )
    )
  }

  it should "handle null components" in {
    Value("^^aMiddle^aPrefix^=iFamily^^^^=pFamily^^^pPrefix^pSuffix".utf8Bytes)
      .toPersonNames() shouldBe Seq(
      PersonName(
        ComponentGroup("", "iFamily", "pFamily"),
        ComponentGroup("", "", ""),
        ComponentGroup("aMiddle", "", ""),
        ComponentGroup("aPrefix", "", "pPrefix"),
        ComponentGroup("", "", "pSuffix")
      )
    )

    Value("aFamily^^aMiddle=iFamily".utf8Bytes)
      .toPersonNames() shouldBe Seq(
      PersonName(
        ComponentGroup("aFamily", "iFamily", ""),
        ComponentGroup("", "", ""),
        ComponentGroup("aMiddle", "", ""),
        ComponentGroup("", "", ""),
        ComponentGroup("", "", "")
      )
    )
  }

  it should "trim whitespace within each component" in {
    Value("   aFamily   ^^    aMiddle    =   iFamily".utf8Bytes)
      .toPersonNames() shouldBe Seq(
      PersonName(
        ComponentGroup("aFamily", "iFamily", ""),
        ComponentGroup("", "", ""),
        ComponentGroup("aMiddle", "", ""),
        ComponentGroup("", "", ""),
        ComponentGroup("", "", "")
      )
    )
  }

  "Parsing a URI" should "work for valid URI strings" in {
    val uri = Value("https://example.com:8080/path?q1=45&q2=46".utf8Bytes).toURI()
    uri shouldBe defined
    uri.get.getScheme shouldBe "https"
    uri.get.getHost shouldBe "example.com"
    uri.get.getPort shouldBe 8080
    uri.get.getPath shouldBe "/path"
    uri.get.getQuery shouldBe "q1=45&q2=46"
  }

  it should "not parse invalid URIs" in {
    val uri = Value("not < a > uri".utf8Bytes).toURI()
    uri shouldBe empty
  }

  "An element" should "update its value bytes" in {
    val updated = Value.empty ++ "ABC".utf8Bytes
    updated.bytes shouldBe "ABC".utf8Bytes // not compliant
    updated.ensurePadding(VR.SH).bytes shouldBe "ABC ".utf8Bytes
  }

  "Creating an element" should "produce the expected bytes from string(s)" in {
    Value.fromString(VR.PN, "John^Doe").toString(VR.PN) shouldBe Some("John^Doe")
    Value.fromString(VR.PN, "John^Doe", bigEndian = true).toString(VR.PN) shouldBe Some("John^Doe")
    Value.fromString(VR.PN, "John^Doe").toString(VR.PN) shouldBe Some("John^Doe")
    Value.fromStrings(VR.PN, Seq("John^Doe", "Jane^Doe")).toStrings(VR.PN) shouldBe Seq("John^Doe", "Jane^Doe")

    Value.fromString(VR.AT, "00A01234").toInt(VR.AT).get shouldBe 0x00a01234
    Value.fromString(VR.FL, "3.1415").toFloat(VR.FL).get shouldBe 3.1415f
    Value.fromString(VR.FD, "3.1415").toDouble(VR.FD).get shouldBe 3.1415
    Value.fromString(VR.SV, "-1024").toInt(VR.SV).get shouldBe -1024
    Value.fromString(VR.SL, "-1024").toInt(VR.SL).get shouldBe -1024
    Value.fromString(VR.SS, "-1024").toShort(VR.SS).get shouldBe -1024.toShort
    Value.fromString(VR.UV, "4294967295").toLong(VR.UV).get shouldBe 4294967295L
    Value.fromString(VR.UL, "4294967295").toLong(VR.UL).get shouldBe 4294967295L
    Value.fromString(VR.US, "65535").toInt(VR.US).get shouldBe 65535
  }

  it should "produce the expected bytes from short(s)" in {
    Value.fromShort(VR.US, 512.toShort).toShort(VR.US).get shouldBe 512.toShort
    Value.fromShort(VR.US, 512.toShort, bigEndian = true).toShort(VR.US, bigEndian = true).get shouldBe 512.toShort
    Value.fromShort(VR.US, 512.toShort).toShort(VR.US).get shouldBe 512.toShort
    Value.fromShorts(VR.US, Seq(512, 256).map(_.toShort)).toShorts(VR.US) shouldBe Seq(512.toShort, 256.toShort)

    Value.fromShort(VR.FL, 3.1415.toShort).toFloat(VR.FL).get shouldBe 3.0f
    Value.fromShort(VR.FD, 3.1415.toShort).toDouble(VR.FD).get shouldBe 3.0
    Value.fromShort(VR.SV, -1024.toShort).toInt(VR.SV).get shouldBe -1024
    Value.fromShort(VR.SL, -1024.toShort).toInt(VR.SL).get shouldBe -1024
    Value.fromShort(VR.SS, -1024.toShort).toShort(VR.SS).get shouldBe -1024.toShort
    Value.fromShort(VR.UV, 42.toShort).toLong(VR.UV).get shouldBe 42L
    Value.fromShort(VR.UL, 42.toShort).toLong(VR.UL).get shouldBe 42L
    Value.fromShort(VR.US, 42.toShort).toInt(VR.US).get shouldBe 42
  }

  it should "produce the expected bytes from int(s)" in {
    Value.fromInt(VR.UL, 1234).toInt(VR.UL).get shouldBe 1234
    Value.fromInt(VR.UL, 1234, bigEndian = true).toInt(VR.UL, bigEndian = true).get shouldBe 1234
    Value.fromInt(VR.UL, 1234).toInt(VR.UL).get shouldBe 1234
    Value.fromInts(VR.UL, Seq(512, 256)).toInts(VR.UL) shouldBe Seq(512, 256)

    Value.fromInt(VR.AT, 0x00a01234).toInt(VR.AT).get shouldBe 0x00a01234
    Value.fromInt(VR.FL, 3.1415.toInt).toFloat(VR.FL).get shouldBe 3.0f
    Value.fromInt(VR.FD, 3.1415.toInt).toDouble(VR.FD).get shouldBe 3.0
    Value.fromInt(VR.SV, -1024).toInt(VR.SV).get shouldBe -1024
    Value.fromInt(VR.SL, -1024).toInt(VR.SL).get shouldBe -1024
    Value.fromInt(VR.SS, -1024).toShort(VR.SS).get shouldBe -1024.toShort
    Value.fromInt(VR.UV, 42).toLong(VR.UV).get shouldBe 42L
    Value.fromInt(VR.UL, 42).toLong(VR.UL).get shouldBe 42L
    Value.fromInt(VR.US, 65535).toInt(VR.US).get shouldBe 65535
  }

  it should "produce the expected bytes from long(s)" in {
    Value.fromLong(VR.UL, 1234L).toLong(VR.UL).get shouldBe 1234L
    Value.fromLong(VR.UL, 1234L, bigEndian = true).toLong(VR.UL, bigEndian = true).get shouldBe 1234L
    Value.fromLong(VR.UL, 1234L).toLong(VR.UL).get shouldBe 1234L
    Value.fromLongs(VR.UL, Seq(512L, 256L)).toLongs(VR.UL) shouldBe Seq(512L, 256L)

    Value.fromLong(VR.AT, 0x00a01234L).toInt(VR.AT).get shouldBe 0x00a01234
    Value.fromLong(VR.FL, 3.1415.toLong).toFloat(VR.FL).get shouldBe 3.0f
    Value.fromLong(VR.FD, 3.1415.toLong).toDouble(VR.FD).get shouldBe 3.0
    Value.fromLong(VR.SV, -1024L).toInt(VR.SV).get shouldBe -1024
    Value.fromLong(VR.SL, -1024L).toInt(VR.SL).get shouldBe -1024
    Value.fromLong(VR.SS, -1024L).toShort(VR.SS).get shouldBe -1024.toShort
    Value.fromLong(VR.UV, 4294967295L).toLong(VR.UV).get shouldBe 4294967295L
    Value.fromLong(VR.UL, 4294967295L).toLong(VR.UL).get shouldBe 4294967295L
    Value.fromLong(VR.US, 65535L).toInt(VR.US).get shouldBe 65535
  }

  it should "produce the expected bytes from very long(s)" in {
    Value.fromVeryLong(VR.UV, BigInteger.valueOf(1234)).toVeryLong(VR.UV).get shouldBe BigInteger.valueOf(1234)
    Value
      .fromVeryLong(VR.UV, BigInteger.valueOf(1234), bigEndian = true)
      .toVeryLong(VR.UV, bigEndian = true)
      .get shouldBe BigInteger.valueOf(1234)
    Value.fromVeryLong(VR.UV, BigInteger.valueOf(1234)).toVeryLong(VR.UV).get shouldBe BigInteger.valueOf(1234)
    Value.fromVeryLongs(VR.UV, Seq(BigInteger.valueOf(512), BigInteger.valueOf(256))).toVeryLongs(VR.UV) shouldBe Seq(
      BigInteger.valueOf(512),
      BigInteger.valueOf(256)
    )

    Value.fromVeryLong(VR.AT, BigInteger.valueOf(0x00a01234)).toInt(VR.AT).get shouldBe 0x00a01234
    Value.fromVeryLong(VR.FL, BigInteger.valueOf(3.1415.toLong)).toFloat(VR.FL).get shouldBe 3.0f
    Value.fromVeryLong(VR.FD, BigInteger.valueOf(3.1415.toLong)).toDouble(VR.FD).get shouldBe 3.0
    Value.fromVeryLong(VR.SV, BigInteger.valueOf(-1024L)).toInt(VR.SV).get shouldBe -1024
    Value.fromVeryLong(VR.SL, BigInteger.valueOf(-1024L)).toInt(VR.SL).get shouldBe -1024
    Value.fromVeryLong(VR.SS, BigInteger.valueOf(-1024L)).toShort(VR.SS).get shouldBe -1024.toShort
    Value.fromVeryLong(VR.UV, BigInteger.valueOf(4294967295L)).toLong(VR.UV).get shouldBe 4294967295L
    Value.fromVeryLong(VR.UL, BigInteger.valueOf(4294967295L)).toLong(VR.UL).get shouldBe 4294967295L
    Value.fromVeryLong(VR.US, BigInteger.valueOf(65535L)).toInt(VR.US).get shouldBe 65535
  }

  it should "produce the expected bytes from float(s)" in {
    Value.fromFloat(VR.FL, 3.14f).toFloat(VR.FL).get shouldBe 3.14f
    Value.fromFloat(VR.FL, 3.14f, bigEndian = true).toFloat(VR.FL, bigEndian = true).get shouldBe 3.14f
    Value.fromFloat(VR.FL, 3.14f).toFloat(VR.FL).get shouldBe 3.14f
    Value.fromFloats(VR.FL, Seq(512f, 256f)).toFloats(VR.FL) shouldBe Seq(512f, 256f)

    Value.fromFloat(VR.AT, 0x00a01234.toFloat).toInt(VR.AT).get shouldBe 0x00a01234
    Value.fromFloat(VR.FL, 3.1415f).toFloat(VR.FL).get shouldBe 3.1415f
    Value.fromFloat(VR.FD, 3.1415f).toDouble(VR.FD).get.toFloat shouldBe 3.1415f
    Value.fromFloat(VR.SV, -1024f).toInt(VR.SV).get shouldBe -1024
    Value.fromFloat(VR.SL, -1024f).toInt(VR.SL).get shouldBe -1024
    Value.fromFloat(VR.SS, -1024f).toShort(VR.SS).get shouldBe -1024.toShort
    Value.fromFloat(VR.UV, 42.0f).toLong(VR.UV).get shouldBe 42L
    Value.fromFloat(VR.UL, 42.0f).toLong(VR.UL).get shouldBe 42L
    Value.fromFloat(VR.US, 65535f).toInt(VR.US).get shouldBe 65535
  }

  it should "produce the expected bytes from double(s)" in {
    Value.fromDouble(VR.FD, 3.14).toDouble(VR.FD).get.toFloat shouldBe 3.14f
    Value.fromDouble(VR.FD, 3.14, bigEndian = true).toDouble(VR.FD, bigEndian = true).get.toFloat shouldBe 3.14f
    Value.fromDouble(VR.FD, 3.14).toDouble(VR.FD).get.toFloat shouldBe 3.14f
    Value.fromDoubles(VR.FD, Seq(512.0, 256.0)).toDoubles(VR.FD) shouldBe Seq(512.0, 256.0)

    Value.fromDouble(VR.AT, 0x00a01234.toDouble).toInt(VR.AT).get shouldBe 0x00a01234
    Value.fromDouble(VR.FL, 3.1415).toFloat(VR.FL).get shouldBe 3.1415f
    Value.fromDouble(VR.FD, 3.1415).toDouble(VR.FD).get.toFloat shouldBe 3.1415f
    Value.fromDouble(VR.SV, -1024.0).toInt(VR.SV).get shouldBe -1024
    Value.fromDouble(VR.SL, -1024.0).toInt(VR.SL).get shouldBe -1024
    Value.fromDouble(VR.SS, -1024.0).toShort(VR.SS).get shouldBe -1024.toShort
    Value.fromDouble(VR.UV, 42.0).toLong(VR.UV).get shouldBe 42L
    Value.fromDouble(VR.UL, 42.0).toLong(VR.UL).get shouldBe 42L
    Value.fromDouble(VR.US, 65535.0).toInt(VR.US).get shouldBe 65535
  }

  it should "produce the expected bytes from date(s)" in {
    val date1 = LocalDate.of(2004, 3, 29)
    val date2 = LocalDate.of(2004, 3, 30)
    Value.fromDate(VR.DA, date1).toDate().get shouldBe date1
    Value.fromDates(VR.DA, Seq(date1, date2)).toDates() shouldBe Seq(date1, date2)

    Value.fromDate(VR.DT, date1).toDate().get shouldBe date1
    Value.fromDate(VR.LT, date1).toString(VR.LT) shouldBe Some("20040329")
  }

  it should "produce the expected bytes from time(s)" in {
    val dt1 = LocalTime.of(11, 59, 35, 123456000)
    val dt2 = LocalTime.of(11, 59, 36, 123456000)
    Value.fromTime(VR.TM, dt1).toTime().get shouldBe dt1
    Value.fromTimes(VR.TM, Seq(dt1, dt2)).toTimes() shouldBe Seq(dt1, dt2)

    Value.fromTime(VR.LT, dt1).toString(VR.LT) shouldBe Some("115935.123456")
  }

  it should "produce the expected bytes from date-time(s)" in {
    val dt1 = ZonedDateTime.of(2004, 3, 29, 11, 59, 35, 123456000, ZoneOffset.UTC)
    val dt2 = ZonedDateTime.of(2004, 3, 29, 11, 59, 36, 123456000, ZoneOffset.UTC)
    Value.fromDateTime(VR.DT, dt1).toDateTime().get shouldBe dt1
    Value.fromDateTimes(VR.DT, Seq(dt1, dt2)).toDateTimes() shouldBe Seq(dt1, dt2)

    Value.fromDateTime(VR.LT, dt1).toString(VR.LT) shouldBe Some("20040329115935.123456+0000")
  }

  it should "produce the expected bytes from patient name(s)" in {
    val pn1 = PersonName(
      ComponentGroup("family", "i", "p"),
      ComponentGroup("given", "i", "p"),
      ComponentGroup("middle", "i", "p"),
      ComponentGroup("prefix", "i", "p"),
      ComponentGroup("suffix", "i", "p")
    )
    val pn2 = pn1.copy(familyName = ComponentGroup("otherfamily", "i", "p"))
    Value.fromPersonName(VR.PN, pn1).toPersonName().get shouldBe pn1
    Value.fromPersonNames(VR.PN, Seq(pn1, pn2)).toPersonNames() shouldBe Seq(pn1, pn2)

    Value.fromPersonName(VR.PN, pn1).toString(VR.PN) shouldBe Some(
      "family^given^middle^prefix^suffix=i^i^i^i^i=p^p^p^p^p"
    )
  }

  "PatientName" should "be parsed from strings" in {
    val pns = parsePN("John Doe")
    pns should have length 1
    pns.head.familyName.alphabetic shouldBe "John Doe"
    pns.head.toString shouldBe "John Doe"
  }

  it should "parse into family, middle and given names" in {
    val pns = parsePN("Family^Given^Middle^Prefix^Suffix")
    pns should have length 1
    pns.head.familyName.alphabetic shouldBe "Family"
    pns.head.givenName.alphabetic shouldBe "Given"
    pns.head.middleName.alphabetic shouldBe "Middle"
    pns.head.prefix.alphabetic shouldBe "Prefix"
    pns.head.suffix.alphabetic shouldBe "Suffix"
  }

  it should "parse empty components" in {
    val pns = parsePN("Family^Given^^Prefix^")
    pns should have length 1
    pns.head.familyName.alphabetic shouldBe "Family"
    pns.head.givenName.alphabetic shouldBe "Given"
    pns.head.middleName.alphabetic shouldBe empty
    pns.head.prefix.alphabetic shouldBe "Prefix"
    pns.head.suffix.alphabetic shouldBe empty
  }

  it should "parse components into alphabetic, ideographic and phonetic elements" in {
    val pns = parsePN("F-Alphabetic^Given^^P-Alphabetic^=F-Ideographic^^^^=F-Phonetic^^M-Phonetic^P-Phonetic^")
    pns should have length 1
    pns.head.familyName.alphabetic shouldBe "F-Alphabetic"
    pns.head.familyName.ideographic shouldBe "F-Ideographic"
    pns.head.familyName.phonetic shouldBe "F-Phonetic"

    pns.head.givenName.alphabetic shouldBe "Given"
    pns.head.givenName.ideographic shouldBe empty
    pns.head.givenName.phonetic shouldBe empty

    pns.head.middleName.alphabetic shouldBe empty
    pns.head.middleName.ideographic shouldBe empty
    pns.head.middleName.phonetic shouldBe "M-Phonetic"

    pns.head.prefix.alphabetic shouldBe "P-Alphabetic"
    pns.head.prefix.ideographic shouldBe empty
    pns.head.prefix.phonetic shouldBe "P-Phonetic"

    pns.head.suffix.alphabetic shouldBe empty
    pns.head.suffix.ideographic shouldBe empty
    pns.head.suffix.phonetic shouldBe empty
  }

  it should "parse multiple patient names" in {
    val pns = parsePN("""Doe^John\Doe^Jane""")
    pns should have length 2
    pns.head.givenName.alphabetic shouldBe "John"
    pns(1).givenName.alphabetic shouldBe "Jane"
  }

  "A URI" should "be parsed from strings" in {
    val uri = parseUR("https://example.com:8080/path")
    uri shouldBe defined
    uri.get.getHost shouldBe "example.com"
    uri.get.getPort shouldBe 8080
    uri.get.getPath shouldBe "/path"
    uri.get.getScheme shouldBe "https"
  }

  it should "not accept malformed URIs" in {
    val uri = parseUR("not < a > uri")
    uri shouldBe empty
  }
}
