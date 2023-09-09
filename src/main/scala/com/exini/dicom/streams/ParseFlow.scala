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
import com.exini.dicom.data.Parsing._
import com.exini.dicom.data._

import scala.util.Try

class ParseFlow private (chunkSize: Int) extends ByteParserFlow[DicomPart] {

  import ByteParser._

  protected class DicomParsingLogic extends ParsingLogic with StageLogging {

    case class DatasetHeaderState(override val maySwitchTs: Boolean, bigEndian: Boolean, explicitVR: Boolean) extends ParseState

    case class FmiHeaderState(
        tsuid: Option[String],
        bigEndian: Boolean,
        explicitVR: Boolean,
        hasFmi: Boolean,
        pos: Long,
        fmiEndPos: Option[Long]
    ) extends ParseState

    case class ValueState(bigEndian: Boolean, bytesLeft: Long, nextStep: ParseStep[DicomPart])

    case class FragmentsState(bigEndian: Boolean, explicitVR: Boolean) extends ParseState

    case class DeflatedState(bigEndian: Boolean, nowrap: Boolean)

    abstract class DicomParseStep extends ParseStep[DicomPart] {
      override def onTruncation(reader: ByteReader): Unit = throw new DicomStreamException("DICOM file is truncated")
    }

    case object AtBeginning extends DicomParseStep {

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
                  InDatasetHeader(DatasetHeaderState(maySwitchTs = false, info.bigEndian, info.explicitVR))
              ParseResult(maybePreamble, nextState)
            }
            .getOrElse(throw new DicomStreamException("Not a DICOM stream"))
        }
      }
    }

    case class InFmiHeader(state: FmiHeaderState) extends DicomParseStep {

      final val transferSyntaxLengthLimit = 1024

      private def toDatasetStep(firstTwoBytes: ByteString, state: FmiHeaderState): DicomParseStep = {
        val tsuid = state.tsuid.getOrElse {
          log.warning("Missing Transfer Syntax (0002,0010) - assume Explicit VR Little Endian")
          UID.ExplicitVRLittleEndian
        }

        val bigEndian  = tsuid == UID.ExplicitVRBigEndianRetired
        val explicitVR = tsuid != UID.ImplicitVRLittleEndian

        if (isDeflated(tsuid))
          if (hasZLIBHeader(firstTwoBytes))
            InDeflatedData(DeflatedState(state.bigEndian, nowrap = false))
          else
            InDeflatedData(DeflatedState(state.bigEndian, nowrap = true))
        else
          InDatasetHeader(DatasetHeaderState(maySwitchTs = true, bigEndian, explicitVR))
      }

      def parse(reader: ByteReader): ParseResult[DicomPart] = {
        val header = readHeader(reader, state)
        warnIfOdd(header.tag, header.vr, header.valueLength, log)
        if (groupNumber(header.tag) != 2) {
          log.warning("Missing or wrong File Meta Information Group Length (0002,0000)")
          ParseResult(None, toDatasetStep(ByteString(0, 0), state))
        } else {
          // no meta elements can lead to vr = null
          val updatedVr  = if (header.vr == VR.UN) Lookup.vrOf(header.tag) else header.vr
          val bytes      = reader.take(header.headerLength)
          val updatedPos = state.pos + header.headerLength + header.valueLength
          val updatedState = header.tag match {
            case Tag.FileMetaInformationGroupLength =>
              reader.ensure(4)
              val valueBytes = reader.remainingData.take(4)
              state.copy(
                pos = updatedPos,
                fmiEndPos = Some(updatedPos + intToUnsignedLong(bytesToInt(valueBytes, state.bigEndian)))
              )
            case Tag.TransferSyntaxUID =>
              if (header.valueLength < transferSyntaxLengthLimit) {
                reader.ensure(header.valueLength.toInt)
                val valueBytes = reader.remainingData.take(header.valueLength.toInt)
                state.copy(tsuid = Some(valueBytes.utf8String.trim), pos = updatedPos)
              } else {
                log.warning("Transfer syntax data is very large, skipping")
                state.copy(pos = updatedPos)
              }
            case _ =>
              state.copy(pos = updatedPos)
          }
          val part = Some(
            HeaderPart(
              header.tag,
              updatedVr,
              header.valueLength,
              isFmi = true,
              state.bigEndian,
              state.explicitVR,
              bytes
            )
          )
          val nextStep = updatedState.fmiEndPos.filter(_ <= updatedPos) match {
            case Some(_) =>
              reader.ensure(header.valueLength.toInt)
              if (reader.remainingSize == header.valueLength && isUpstreamClosed)
                FinishedParser
              else {
                reader.ensure(header.valueLength.toInt + 2)
                val firstTwoBytes = reader.remainingData.drop(header.valueLength.toInt).take(2)
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
            InValue(ValueState(updatedState.bigEndian, header.valueLength, nextStep)),
            acceptNoMoreDataAvailable = false
          )
        }
      }
    }

    case class InDatasetHeader(datasetHeaderState: DatasetHeaderState) extends DicomParseStep {
      def maybeSwitchTs(reader: ByteReader, state: DatasetHeaderState): DatasetHeaderState = {
        reader.ensure(8)
        val data       = reader.remainingData.take(8)
        val tag        = bytesToTag(data, state.bigEndian)
        val explicitVR = Try(VR.withValue(bytesToVR(data.drop(4)))).getOrElse(null)
        if (isSpecial(tag))
          state.copy(maySwitchTs = false)
        else if (state.explicitVR && explicitVR == null) {
          log.info("Implicit VR attributes detected in explicit VR dataset")
          state.copy(maySwitchTs = false, explicitVR = false)
        } else if (!state.explicitVR && explicitVR != null)
          state.copy(maySwitchTs = false, explicitVR = true)
        else
          state.copy(maySwitchTs = false)
      }

      private def readDatasetHeader(reader: ByteReader, state: DatasetHeaderState): DicomPart = {
        val header = readHeader(reader, state)
        warnIfOdd(header.tag, header.vr, header.valueLength, log)
        if (header.vr != null) {
          val bytes = reader.take(header.headerLength)
          if (header.vr == VR.SQ || header.vr == VR.UN && header.valueLength == indeterminateLength)
            SequencePart(header.tag, header.valueLength, state.bigEndian, state.explicitVR, bytes)
          else if (header.valueLength == indeterminateLength)
            FragmentsPart(header.tag, header.valueLength, header.vr, state.bigEndian, state.explicitVR, bytes)
          else
            HeaderPart(
              header.tag,
              header.vr,
              header.valueLength,
              isFmi = false,
              state.bigEndian,
              state.explicitVR,
              bytes
            )
        } else
          header.tag match {
            case 0xfffee000 => ItemPart(header.valueLength, state.bigEndian, reader.take(8))
            case 0xfffee00d => ItemDelimitationPart(state.bigEndian, reader.take(8))
            case 0xfffee0dd => SequenceDelimitationPart(state.bigEndian, reader.take(8))
            case _          => UnknownPart(state.bigEndian, reader.take(header.headerLength))
          }
      }

      def parse(reader: ByteReader): ParseResult[DicomPart] = {
        val state =
          if (datasetHeaderState.maySwitchTs) maybeSwitchTs(reader, datasetHeaderState) else datasetHeaderState
        val part = readDatasetHeader(reader, state)
        val nextState = part match {
          case HeaderPart(_, _, length, _, bigEndian, _, _) =>
            if (length > 0)
              InValue(ValueState(bigEndian, length, InDatasetHeader(state)))
            else
              InDatasetHeader(state)
          case FragmentsPart(_, _, _, bigEndian, _, _) =>
            InFragments(FragmentsState(bigEndian, state.explicitVR))
          case SequencePart(_, _, _, _, _)    => InDatasetHeader(state)
          case ItemPart(_, _, _)              => InDatasetHeader(state.copy(maySwitchTs = true))
          case ItemDelimitationPart(_, _)     => InDatasetHeader(state)
          case SequenceDelimitationPart(_, _) => InDatasetHeader(state.copy(maySwitchTs = true))
          case _                              => InDatasetHeader(state)
        }
        ParseResult(Some(part), nextState, acceptNoMoreDataAvailable = !nextState.isInstanceOf[InValue])
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
        val header = readHeader(reader, state)
        header.tag match {

          case 0xfffee000 => // begin fragment
            val nextState =
              if (header.valueLength > 0)
                InValue(ValueState(state.bigEndian, header.valueLength, this))
              else
                this
            ParseResult(
              Some(ItemPart(header.valueLength, state.bigEndian, reader.take(header.headerLength))),
              nextState
            )

          case 0xfffee0dd => // end fragments
            if (header.valueLength != 0)
              log.warning(s"Unexpected fragments delimitation length ${header.valueLength}")
            ParseResult(
              Some(SequenceDelimitationPart(state.bigEndian, reader.take(header.headerLength))),
              InDatasetHeader(DatasetHeaderState(maySwitchTs = false, state.bigEndian, state.explicitVR))
            )

          case _ =>
            log.warning(
              s"Unexpected element (${tagToString(header.tag)}) in fragments with length=${header.valueLength}"
            )
            ParseResult(
              Some(UnknownPart(state.bigEndian, reader.take(header.headerLength + header.valueLength.toInt))),
              this
            )
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

    startWith(AtBeginning)
  }

  override def createLogic(attr: Attributes) = new DicomParsingLogic()

}

object ParseFlow {

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
        val inflater1 = Compression.inflate(maxBytesPerChunk = chunkSize, nowrap = true)
        val inflater2 = Compression.inflate(maxBytesPerChunk = chunkSize, nowrap = false)
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
