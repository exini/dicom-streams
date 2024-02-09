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

import com.exini.dicom.data.ByteParser._

import scala.annotation.tailrec
import scala.util.control.{ NoStackTrace, NonFatal }

/**
  * This class is borrowed (with modifications) from the
  * <a href="https://github.com/apache/incubator-pekko/blob/main/stream/src/main/scala/org/apache/pekko/stream/impl/io/ByteStringParser.scala">Pekko internal API</a>.
  * It provides a stateful parser from byte chunks to objects of type <code>T</code>.
  *
  * @param target target
  * @tparam T the type created by this parser
  */
class ByteParser[T](target: ByteParserTarget[T]) {

  protected var current: ParseStep[T]     = FinishedParser
  protected var reader                    = new ByteReader(emptyBytes)
  protected var acceptNoMoreDataAvailable = true
  protected var buffer: Bytes             = emptyBytes

  def startWith(step: ParseStep[T]): Unit =
    current = step

  protected def recursionLimit: Int = 1000

  protected def complete(): Unit = {
    buffer = emptyBytes
    reader = null
    target.complete()
  }

  protected def fail(ex: Throwable): Unit = {
    buffer = emptyBytes
    reader = new ByteReader(emptyBytes)
    target.fail(ex)
  }

  /**
    * doParse() is the main driver for the parser.
    * The general logic is that invocation of this method either results in an emitted parsed element, or an indication
    * that there is more data needed.
    *
    * On completion there are various cases:
    *  buffer is empty: parser accepts completion or fails.
    *  buffer is non-empty, we wait for more data. This can lead to two conditions:
    *     - drained, empty buffer. This is either accepted completion (acceptNoMoreDataAvailable) or a truncation.
    *     - parser demands more data than in buffer. This is always a truncation.
    *
    * If the return value is true the method must be called another time to continue processing.
    */
  private def doParseInner(): Boolean =
    if (buffer.nonEmpty) {
      reader.setInput(buffer)
      try {
        val parseResult = current.parse(reader)
        acceptNoMoreDataAvailable = parseResult.acceptNoMoreDataAvailable
        parseResult.result.foreach(target.next)

        if (parseResult.nextStep == FinishedParser) {
          complete()
          DontRecurse
        } else {
          buffer = reader.remainingData
          current = parseResult.nextStep

          // If this step didn't produce a result, continue parsing.
          if (parseResult.result.isEmpty)
            Recurse
          else
            DontRecurse
        }
      } catch {
        case NeedMoreData =>
          acceptNoMoreDataAvailable = false
          target.needMoreData(current, reader, acceptNoMoreDataAvailable)
          DontRecurse
        case NonFatal(ex) =>
          fail(new ParseException(s"Parsing failed in step $current: ${ex.getMessage}", ex))
          DontRecurse
      }
    } else {
      target.needMoreData(current, reader, acceptNoMoreDataAvailable)
      DontRecurse
    }

  @tailrec final private def doParse(remainingRecursions: Int): Unit =
    if (remainingRecursions == 0)
      fail(new ParseException(s"Parsing logic didn't produce result after $recursionLimit steps."))
    else {
      val recurse = doParseInner()
      if (recurse) doParse(remainingRecursions - 1)
    }

  /**
    * Append the input data
    * @param chunk input data
    */
  def ++=(chunk: Bytes): Unit = if (chunk.nonEmpty) buffer ++= chunk

  /**
    * Trigger a parse cycle which, if successful, will emit a single element
    */
  def parse(): Unit = this.doParse(recursionLimit)
}

object ByteParser {

  val CompactionThreshold = 16

  private final val Recurse     = true
  private final val DontRecurse = false

  /**
    * @param result parser can return some element or return None if no element was generated in this
    *               step and parsing should immediately continue with the next step.
    * @param nextStep next parser
    */
  case class ParseResult[+T](result: Option[T], nextStep: ParseStep[T], acceptNoMoreDataAvailable: Boolean = true)

  trait ParseStep[+T] {
    def parse(reader: ByteReader): ParseResult[T]

    def onTruncation(reader: ByteReader): Unit = throw new ParseException("truncated data")
  }

  object FinishedParser extends ParseStep[Nothing] {
    override def parse(reader: ByteReader) =
      throw new ParseException("no initial parser installed: you must use startWith(...)")
  }

  val NeedMoreData = new Exception with NoStackTrace

  class ByteReader(private var input: Bytes) {

    private[this] var off = 0

    def setInput(input: Bytes): Unit = {
      this.input = input
      off = 0
    }

    def hasRemaining: Boolean = off < input.size

    def remainingSize: Int = input.size - off

    def currentOffset: Int = off

    def remainingData: Bytes = input.drop(off)

    def fromStartToHere: Bytes = input.take(off)

    def ensure(n: Int): Unit = if (remainingSize < n) throw NeedMoreData

    def take(n: Int): Bytes =
      if (off + n <= input.length) {
        val o = off
        off = o + n
        input.slice(o, off)
      } else throw NeedMoreData

    def takeAll(): Bytes = {
      val ret = remainingData
      off = input.size
      ret
    }

    def skip(numBytes: Int): Unit =
      if (off + numBytes <= input.length) off += numBytes
      else throw NeedMoreData
  }

  trait ByteParserTarget[T] {
    def next(result: T): Unit
    def needMoreData(current: ParseStep[T], reader: ByteReader, acceptNoMoreDataAvailable: Boolean): Unit
    def fail(ex: Throwable): Unit
    def complete(): Unit
  }

}
