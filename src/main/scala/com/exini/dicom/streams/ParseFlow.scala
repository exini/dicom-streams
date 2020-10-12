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

package com.exini.dicom.streams

import akka.NotUsed
import akka.stream.javadsl.MergePreferred
import akka.stream.scaladsl.{ Compression, Flow, GraphDSL, Partition }
import akka.stream.stage._
import akka.stream.{ Attributes, FlowShape }
import akka.util.ByteString
import com.exini.dicom.data.DicomParts._
import com.exini.dicom.data._

import scala.util.Try

class ParseFlow private (chunkSize: Int) extends ByteStringParser[DicomPart] {

  import ByteStringParser._

  final val dicomPreambleLength       = 132
  final val transferSyntaxLengthLimit = 1024

  protected class DicomParsingLogic extends ParsingLogic with StageLogging {

    sealed trait HeaderState {
      val bigEndian: Boolean
      val explicitVR: Boolean
    }

    case class DatasetHeaderState(itemIndex: Int, bigEndian: Boolean, explicitVR: Boolean) extends HeaderState

    case class FmiHeaderState(
        tsuid: Option[String],
        bigEndian: Boolean,
        explicitVR: Boolean,
        hasFmi: Boolean,
        pos: Long,
        fmiEndPos: Option[Long]
    ) extends HeaderState

    case class ValueState(bigEndian: Boolean, bytesLeft: Long, nextStep: ParseStep[DicomPart])

    case class FragmentsState(fragmentIndex: Int, bigEndian: Boolean, explicitVR: Boolean) extends HeaderState

    case class DeflatedState(bigEndian: Boolean, nowrap: Boolean)

    abstract class DicomParseStep extends ParseStep[DicomPart] {
      override def onTruncation(reader: ByteReader): Unit = throw new DicomStreamException("DICOM file is truncated")
    }

    case object AtBeginning extends DicomParseStep {
      private case class HeaderInfo(bigEndian: Boolean, explicitVR: Boolean, hasFmi: Boolean)

      private def isPreamble(data: ByteString): Boolean =
        data.length >= dicomPreambleLength && data
          .slice(dicomPreambleLength - 4, dicomPreambleLength) == ByteString('D', 'I', 'C', 'M')

      private def tryReadHeader(data: ByteString): Option[HeaderInfo] =
        tryReadHeader(data, assumeBigEndian = false)
          .orElse(tryReadHeader(data, assumeBigEndian = true))

      private def tryReadHeader(data: ByteString, assumeBigEndian: Boolean): Option[HeaderInfo] = {
        val (tag, vr) = tagVr(data, assumeBigEndian, explicitVr = false)
        if (vr == VR.UN)
          None
        else if (bytesToVR(data.drop(4)) == vr.value)
          Some(HeaderInfo(bigEndian = assumeBigEndian, explicitVR = true, hasFmi = isFileMetaInformation(tag)))
        else if (intToUnsignedLong(bytesToInt(data.drop(4), assumeBigEndian)) >= 0)
          if (assumeBigEndian)
            throw new DicomStreamException("Implicit VR Big Endian encoded DICOM Stream")
          else
            Some(HeaderInfo(bigEndian = false, explicitVR = false, hasFmi = isFileMetaInformation(tag)))
        else
          None
      }

      def parse(reader: ByteReader): ParseResult[DicomPart] = {
        val maybePreamble =
          if (!isUpstreamClosed || reader.remainingSize >= dicomPreambleLength) {
            reader.ensure(dicomPreambleLength)
            if (isPreamble(reader.remainingData.take(dicomPreambleLength)))
              Some(PreamblePart(bytes = reader.take(dicomPreambleLength)))
            else None
          } else None
        if (maybePreamble.isDefined && !reader.hasRemaining && isUpstreamClosed)
          ParseResult(maybePreamble, FinishedParser)
        else {
          reader.ensure(8)
          tryReadHeader(reader.remainingData.take(8))
            .map { info =>
              val nextState =
                if (info.hasFmi) {
                  if (!info.explicitVR)
                    log.warning(s"File meta information uses implicit VR encoding")
                  if (info.bigEndian)
                    log.warning(s"File meta information uses big-endian encoding")
                  InFmiHeader(FmiHeaderState(None, info.bigEndian, info.explicitVR, info.hasFmi, 0, None))
                } else
                  InDatasetHeader(DatasetHeaderState(0, info.bigEndian, info.explicitVR))
              ParseResult(maybePreamble, nextState)
            }
            .getOrElse(throw new DicomStreamException("Not a DICOM stream"))
        }
      }
    }

    case class InFmiHeader(state: FmiHeaderState) extends DicomParseStep {
      private def toDatasetStep(firstTwoBytes: ByteString, state: FmiHeaderState): DicomParseStep = {
        val tsuid = state.tsuid.getOrElse {
          log.warning("Missing Transfer Syntax (0002,0010) - assume Explicit VR Little Endian")
          UID.ExplicitVRLittleEndian
        }

        val bigEndian  = tsuid == UID.ExplicitVRBigEndianRetired
        val explicitVR = tsuid != UID.ImplicitVRLittleEndian
        val isDeflated = tsuid == UID.DeflatedExplicitVRLittleEndian || tsuid == UID.JPIPReferencedDeflate

        if (isDeflated)
          if (hasZLIBHeader(firstTwoBytes))
            InDeflatedData(DeflatedState(state.bigEndian, nowrap = false))
          else
            InDeflatedData(DeflatedState(state.bigEndian, nowrap = true))
        else
          InDatasetHeader(DatasetHeaderState(0, bigEndian, explicitVR))
      }

      private def hasZLIBHeader(firstTwoBytes: ByteString): Boolean = bytesToUShortBE(firstTwoBytes) == 0x789c

      def parse(reader: ByteReader): ParseResult[DicomPart] = {
        val (tag, vr, headerLength, valueLength) = readHeader(reader, state)
        if (valueLength % 2 > 0)
          log.warning(s"Element ${tagToString(tag)} has odd length")
        if (groupNumber(tag) != 2) {
          log.warning("Missing or wrong File Meta Information Group Length (0002,0000)")
          ParseResult(None, toDatasetStep(ByteString(0, 0), state))
        } else {
          // no meta elements can lead to vr = null
          val updatedVr  = if (vr == VR.UN) Lookup.vrOf(tag) else vr
          val bytes      = reader.take(headerLength)
          val updatedPos = state.pos + headerLength + valueLength
          val updatedState = tag match {
            case Tag.FileMetaInformationGroupLength =>
              reader.ensure(4)
              val valueBytes = reader.remainingData.take(4)
              state.copy(
                pos = updatedPos,
                fmiEndPos = Some(updatedPos + intToUnsignedLong(bytesToInt(valueBytes, state.bigEndian)))
              )
            case Tag.TransferSyntaxUID =>
              if (valueLength < transferSyntaxLengthLimit) {
                reader.ensure(valueLength.toInt)
                val valueBytes = reader.remainingData.take(valueLength.toInt)
                state.copy(tsuid = Some(valueBytes.utf8String.trim), pos = updatedPos)
              } else {
                log.warning("Transfer syntax data is very large, skipping")
                state.copy(pos = updatedPos)
              }
            case _ =>
              state.copy(pos = updatedPos)
          }
          val part = Some(
            HeaderPart(tag, updatedVr, valueLength, isFmi = true, state.bigEndian, state.explicitVR, bytes)
          )
          val nextStep = updatedState.fmiEndPos.filter(_ <= updatedPos) match {
            case Some(_) =>
              reader.ensure(valueLength.toInt)
              if (reader.remainingSize == valueLength && isUpstreamClosed)
                FinishedParser
              else {
                reader.ensure(valueLength.toInt + 2)
                val firstTwoBytes = reader.remainingData.drop(valueLength.toInt).take(2)
                if (
                  !state.tsuid
                    .contains(UID.DeflatedExplicitVRLittleEndian) && bytesToShort(firstTwoBytes, state.bigEndian) == 2
                ) {
                  log.warning("Wrong File Meta Information Group Length (0002,0000)")
                  InFmiHeader(updatedState)
                } else {
                  if (updatedState.fmiEndPos.exists(_ != updatedPos))
                    log.warning(s"Wrong File Meta Information Group Length (0002,0000)")
                  toDatasetStep(firstTwoBytes, updatedState)
                }
              }
            case None =>
              InFmiHeader(updatedState)
          }
          ParseResult(
            part,
            InValue(ValueState(updatedState.bigEndian, valueLength, nextStep)),
            acceptUpstreamFinish = false
          )
        }
      }
    }

    case class InDatasetHeader(state: DatasetHeaderState) extends DicomParseStep {
      private def readDatasetHeader(reader: ByteReader, state: DatasetHeaderState): Option[DicomPart] = {
        val (tag, vr, headerLength, valueLength) = readHeader(reader, state)
        if (valueLength % 2 > 0)
          log.warning(s"Element ${tagToString(tag)} has odd length")
        if (vr != null) {
          val bytes = reader.take(headerLength)
          if (vr == VR.SQ || vr == VR.UN && valueLength == indeterminateLength)
            Some(SequencePart(tag, valueLength, state.bigEndian, state.explicitVR, bytes))
          else if (valueLength == indeterminateLength)
            Some(FragmentsPart(tag, valueLength, vr, state.bigEndian, state.explicitVR, bytes))
          else
            Some(HeaderPart(tag, vr, valueLength, isFmi = false, state.bigEndian, state.explicitVR, bytes))
        } else
          tag match {
            case 0xfffee000 => Some(ItemPart(state.itemIndex + 1, valueLength, state.bigEndian, reader.take(8)))
            case 0xfffee00d => Some(ItemDelimitationPart(state.itemIndex, state.bigEndian, reader.take(8)))
            case 0xfffee0dd => Some(SequenceDelimitationPart(state.bigEndian, reader.take(8)))
            case _          => Some(UnknownPart(state.bigEndian, reader.take(headerLength)))
          }
      }

      def parse(reader: ByteReader): ParseResult[DicomPart] = {
        val part = readDatasetHeader(reader, state)
        val nextState = part
          .map {
            case HeaderPart(_, _, length, _, bigEndian, _, _) =>
              if (length > 0)
                InValue(ValueState(bigEndian, length, InDatasetHeader(state)))
              else
                InDatasetHeader(state)
            case FragmentsPart(_, _, _, bigEndian, _, _) =>
              InFragments(FragmentsState(fragmentIndex = 0, bigEndian, state.explicitVR))
            case SequencePart(_, _, _, _, _)       => InDatasetHeader(state.copy(itemIndex = 0))
            case ItemPart(index, _, _, _)          => InDatasetHeader(state.copy(itemIndex = index))
            case ItemDelimitationPart(index, _, _) => InDatasetHeader(state.copy(itemIndex = index))
            case _                                 => InDatasetHeader(state)
          }
          .getOrElse(FinishedParser)
        ParseResult(part, nextState, acceptUpstreamFinish = !nextState.isInstanceOf[InValue])
      }
    }

    case class InValue(state: ValueState) extends DicomParseStep {
      def parse(reader: ByteReader): ParseResult[DicomPart] =
        if (state.bytesLeft <= chunkSize)
          ParseResult(
            Some(ValueChunk(state.bigEndian, reader.take(state.bytesLeft.toInt), last = true)),
            state.nextStep
          )
        else
          ParseResult(
            Some(ValueChunk(state.bigEndian, reader.take(chunkSize), last = false)),
            InValue(state.copy(bytesLeft = state.bytesLeft - chunkSize))
          )

      override def onTruncation(reader: ByteReader): Unit =
        if (reader.hasRemaining)
          super.onTruncation(reader)
        else {
          emit(objOut, ValueChunk(state.bigEndian, ByteString.empty, last = true))
          completeStage()
        }
    }

    case class InFragments(state: FragmentsState) extends DicomParseStep {
      def parse(reader: ByteReader): ParseResult[DicomPart] = {
        val (tag, _, headerLength, valueLength) = readHeader(reader, state)
        if (valueLength % 2 > 0)
          log.warning(s"Element ${tagToString(tag)} has odd length")
        tag match {

          case 0xfffee000 => // begin fragment
            val nextState =
              if (valueLength > 0)
                InValue(
                  ValueState(
                    state.bigEndian,
                    valueLength,
                    this.copy(state = state.copy(fragmentIndex = state.fragmentIndex + 1))
                  )
                )
              else
                this.copy(state = state.copy(fragmentIndex = state.fragmentIndex + 1))
            ParseResult(
              Some(ItemPart(state.fragmentIndex + 1, valueLength, state.bigEndian, reader.take(headerLength))),
              nextState
            )

          case 0xfffee0dd => // end fragments
            if (valueLength != 0)
              log.warning(s"Unexpected fragments delimitation length $valueLength")
            ParseResult(
              Some(SequenceDelimitationPart(state.bigEndian, reader.take(headerLength))),
              InDatasetHeader(DatasetHeaderState(0, state.bigEndian, state.explicitVR))
            )

          case _ =>
            log.warning(s"Unexpected element (${tagToString(tag)}) in fragments with length=$valueLength")
            ParseResult(Some(UnknownPart(state.bigEndian, reader.take(headerLength + valueLength.toInt))), this)
        }
      }
    }

    case class InDeflatedData(state: DeflatedState) extends DicomParseStep {
      def parse(reader: ByteReader): ParseResult[DicomPart] =
        ParseResult(
          Some(DeflatedChunk(state.bigEndian, reader.take(math.min(chunkSize, reader.remainingSize)), state.nowrap)),
          this
        )

      override def onTruncation(reader: ByteReader): Unit = {
        emit(objOut, DeflatedChunk(state.bigEndian, reader.takeAll(), state.nowrap))
        completeStage()
      }
    }

    def tagVr(data: ByteString, bigEndian: Boolean, explicitVr: Boolean): (Int, VR) = {
      val tag = bytesToTag(data, bigEndian)
      if (tag == 0xfffee000 || tag == 0xfffee00d || tag == 0xfffee0dd)
        (tag, null)
      else if (explicitVr) {
        val vr = Try(VR.withValue(bytesToVR(data.drop(4)))).getOrElse(null)
        (tag, vr)
      } else
        (tag, Lookup.vrOf(tag))
    }

    private def readHeader(reader: ByteReader, state: HeaderState): (Int, VR, Int, Long) = {
      reader.ensure(8)
      val tagVrBytes = reader.remainingData.take(8)
      val (tag, vr)  = tagVr(tagVrBytes, state.bigEndian, state.explicitVR)
      if (vr == null)
        (tag, vr, 8, lengthToLong(bytesToInt(tagVrBytes.drop(4), state.bigEndian)))
      else if (state.explicitVR)
        if (vr.headerLength == 8)
          (tag, vr, 8, lengthToLong(bytesToUShort(tagVrBytes.drop(6), state.bigEndian)))
        else {
          reader.ensure(12)
          (tag, vr, 12, lengthToLong(bytesToInt(reader.remainingData.drop(8), state.bigEndian)))
        }
      else
        (tag, vr, 8, lengthToLong(bytesToInt(tagVrBytes.drop(4), state.bigEndian)))
    }

    startWith(AtBeginning)
  }

  override def createLogic(attr: Attributes) = new DicomParsingLogic()

}

object ParseFlow {

  private def inflateNowrap(maxBytesPerChunk: Int): Flow[ByteString, ByteString, NotUsed] =
    Flow[ByteString].via(Compression.inflate(maxBytesPerChunk, nowrap = true))

  /**
    * Flow which ingests a stream of bytes and outputs a stream of DICOM data parts as specified by the <code>DicomPart</code>
    * trait. Example DICOM parts are the preamble, headers (tag, VR, length), value chunks (the data in an element divided into chunks),
    * items, sequences and fragments.
    *
    * @param chunkSize the maximum size of a DICOM element data chunk
    * @param inflate   indicates whether deflated DICOM data should be deflated and parsed or passed on as deflated data chunks.
    */
  def apply(chunkSize: Int = 8192, inflate: Boolean = true): Flow[ByteString, DicomPart, NotUsed] =
    if (inflate)
      Flow.fromGraph(GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val parser1 = builder.add(new ParseFlow(chunkSize))
        val parser2 = new ParseFlow(chunkSize)

        val decider = builder.add(
          partFlow
            .statefulMapConcat { () =>
              var route = 0

              {
                case part: DeflatedChunk if route == 0 =>
                  if (part.nowrap) route = 1 else route = 2
                  (part, route) :: Nil
                case part => (part, route) :: Nil
              }
            }
        )
        val partition = builder.add(Partition[(DicomPart, Int)](3, _._2))
        val toPart    = Flow.fromFunction[(DicomPart, Int), DicomPart](_._1)
        val toBytes   = Flow.fromFunction[(DicomPart, Int), ByteString](_._1.bytes)
        val inflater1 = inflateNowrap(maxBytesPerChunk = chunkSize)
        val inflater2 = Compression.inflate(maxBytesPerChunk = chunkSize)
        val merge     = builder.add(MergePreferred.create[DicomPart](2))

        parser1 ~> decider ~> partition
        partition.out(0) ~> toPart ~> merge.preferred
        partition.out(1) ~> toBytes ~> inflater1 ~> parser2 ~> merge.in(0)
        partition.out(2) ~> toBytes ~> inflater2 ~> parser2 ~> merge.in(1)
        FlowShape(parser1.in, merge.out)
      })
    else
      Flow[ByteString].via(new ParseFlow(chunkSize))

  val parseFlow: Flow[ByteString, DicomPart, NotUsed] = apply()

}
