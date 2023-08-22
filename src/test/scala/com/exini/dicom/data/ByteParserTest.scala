package com.exini.dicom.data

import akka.util.ByteString
import com.exini.dicom.data.ByteParser._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ArrayBuffer

class ByteParserTest extends AnyFlatSpec with Matchers {

  import ByteParserTest._

  "Parsing a string of bytes" should "produce the correct values for valid byte sequence" in new Fixture(
    Seq(ByteString("STARTBoat,Car,Airplane"))
  ) {
    parse()
    result shouldBe Seq("Boat", "Car", "Airplane")
    isCompleted shouldBe true
  }

  it should "throw an error for invalid byte sequence" in new Fixture(Seq(ByteString("Car,Boat"))) {
    assertThrows[DicomParseException] {
      parse()
    }
  }

  it should "accept data in chunks" in new Fixture(
    Seq(ByteString("STARTBoat,Car"), ByteString("Airplane"))
  ) {
    parse()
    result shouldBe Seq("Boat", "Car", "Airplane")
  }
}

object ByteParserTest {

  class Fixture(chunks: Seq[ByteString]) extends ByteParserTarget[String] {
    val chunksIterator: Iterator[ByteString] = chunks.iterator
    var isCompleted                          = false

    val parser: ByteParser[String] = new ByteParser[String](this)
    val result: ArrayBuffer[String]   = ArrayBuffer.empty[String]

    case object AtBeginning extends ParseStep[String] {
      override def parse(reader: ByteReader): ParseResult[String] = {
        val magic = reader.take(5)
        if (magic.utf8String == "START")
          ParseResult(None, InWords)
        else
          throw new RuntimeException("Input data must begin with the magic word START")
      }
    }

    case object InWords extends ParseStep[String] {
      override def parse(reader: ByteReader): ParseResult[String] = {
        if (reader.remainingData.headOption.contains(','))
          reader.take(1)
        val nextWord = reader.remainingData.utf8String.takeWhile(_ != ',')
        reader.take(nextWord.length)
        ParseResult(Option.when(nextWord.nonEmpty)(nextWord), InWords)
      }
    }

    parser.startWith(AtBeginning)

    override def next(word: String): Unit =
      result += word

    override def needNoreData(
        current: ParseStep[String],
        reader: ByteReader,
        acceptNoMoreDataAvailable: Boolean
    ): Unit =
      if (chunksIterator.hasNext)
        parser.parse(chunksIterator.next)
      else if (!acceptNoMoreDataAvailable)
        current.onTruncation(reader)
      else
        complete()

    override def fail(ex: Throwable): Unit = throw ex

    override def complete(): Unit = {
      isCompleted = true
    }

    def parse(): Unit =
      while (!isCompleted)
        parser.doParse()
  }
}
