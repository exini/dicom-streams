package com.exini.dicom.data

import akka.util.ByteString
import com.exini.dicom.data.ByteParser._
import com.exini.dicom.data.DicomElements._
import com.exini.dicom.data.Parser._
import com.exini.dicom.data.Parsing._
import org.slf4j.{ Logger, LoggerFactory }

import java.util.zip.Inflater

class Parser(val stop: Option[(AttributeInfo, Int) => Boolean] = None) {

  private val builder         = new ElementsBuilder()
  private var canMakeProgress = true
  private var isLastChunk     = false

  private val byteParserTarget: ByteParserTarget[Element] = new ByteParserTarget[Element] {
    override def next(element: Element): Unit = {
      builder += element
      ()
    }

    override def needMoreData(
        current: ParseStep[Element],
        reader: ByteReader,
        acceptNoMoreDataAvailable: Boolean
    ): Unit =
      if (isLastChunk)
        if (acceptNoMoreDataAvailable) complete() else current.onTruncation(reader)
      else
        canMakeProgress = false

    override def fail(ex: Throwable): Unit = {
      canMakeProgress = false
      throw ex
    }

    override def complete(): Unit =
      canMakeProgress = false
  }

  private val byteParser: ByteParser[Element] = new ByteParser[Element](byteParserTarget) {
    override def ++=(chunk: ByteString): Unit = {
      canMakeProgress |= chunk.nonEmpty
      current match {
        case step: DicomParseStep =>
          step.state match {
            case Some(state: MaybeDeflatedState) =>
              state.inflater match {
                case Some(inflater) =>
                  super.++=(Compression.decompress(chunk, inflater))
                case _ => super.++=(chunk)
              }
            case _ => super.++=(chunk)
          }
        case _ => super.++=(chunk)
      }
    }
  }

  byteParser.startWith(
    new AtBeginning(
      stop
        .map(s => (attributeInfo: AttributeInfo) => s(attributeInfo, builder.currentDepth))
        .getOrElse(_ => false)
    )
  )

  /**
    * Parse the input binary data, producing DICOM elements that are added to the internal builder. An elements results
    * structure can be fetched from the builder at any time.
    * @param chunk the (ppssibly partial) DICOM binary data
    * @param last lets the parser know whether this chunk of data is the last or if there is more to come
    */
  def parse(chunk: ByteString, last: Boolean = true): Unit = {
    isLastChunk = last
    byteParser ++= chunk
    while (canMakeProgress) byteParser.parse()
  }

  /**
    * Get the current elements as represented by the builder
    */
  def result(): Elements = builder.build()
}

object Parser {

  case class FmiAttributeState(
      tsuid: Option[String],
      bigEndian: Boolean,
      explicitVR: Boolean,
      hasFmi: Boolean,
      pos: Int,
      fmiEndPos: Option[Int]
  ) extends ParseState

  trait MaybeDeflatedState extends ParseState {
    val inflater: Option[Inflater]
  }

  case class AttributeState(
      override val maySwitchTs: Boolean,
      bigEndian: Boolean,
      explicitVR: Boolean,
      override val inflater: Option[Inflater]
  ) extends MaybeDeflatedState

  case class FragmentsState(
      bigEndian: Boolean,
      explicitVR: Boolean,
      override val inflater: Option[Inflater]
  ) extends MaybeDeflatedState

  abstract class DicomParseStep(val state: Option[ParseState], val stop: AttributeInfo => Boolean)
      extends ParseStep[Element]

  class AtBeginning(stop: AttributeInfo => Boolean) extends DicomParseStep(None, stop) {
    override def parse(reader: ByteReader): ParseResult[Element] = {
      if (isPreamble(reader.remainingData))
        reader.take(dicomPreambleLength)
      if (reader.remainingSize == 0)
        ParseResult(None, FinishedParser)
      else {
        reader.ensure(8)
        if (reader.remainingData.forall(_ == 0))
          reader.ensure(dicomPreambleLength)
        tryReadHeader(reader.remainingData)
          .map { info =>
            val nextState =
              if (info.hasFmi)
                new InFmiAttribute(
                  FmiAttributeState(None, info.bigEndian, info.explicitVR, info.hasFmi, 0, None),
                  stop
                )
              else new InAttribute(AttributeState(maySwitchTs = false, info.bigEndian, info.explicitVR, None), stop)
            ParseResult(None, nextState);
          }
          .getOrElse(throw new ParseException("Not a DICOM file"))
      }
    }
  }

  class InFmiAttribute(state: FmiAttributeState, stop: AttributeInfo => Boolean)
      extends DicomParseStep(Some(state), stop) {
    private val log: Logger = LoggerFactory.getLogger("ParserInFmiAttributeLogger")

    def parse(reader: ByteReader): ParseResult[Element] = {
      val header = readHeader(reader, state)
      if (stop(header))
        ParseResult(None, FinishedParser)
      else if (groupNumber(header.tag) != 2) {
        log.warn("Missing or wrong File Meta Information Group Length(0002, 0000)")
        ParseResult(None, toDatasetStep(reader, state))
      } else {
        val updatedVr  = if (header.vr == VR.UN) Lookup.vrOf(header.tag) else header.vr
        val bytes      = reader.take(header.headerLength + header.valueLength.toInt)
        val valueBytes = bytes.drop(header.headerLength)
        val state1     = state.copy(pos = state.pos + header.headerLength + header.valueLength.toInt)
        val state2 =
          if (header.tag == Tag.FileMetaInformationGroupLength)
            state1.copy(fmiEndPos = Some(state1.pos + bytesToInt(valueBytes, state1.bigEndian)))
          else if (header.tag == Tag.TransferSyntaxUID)
            state1.copy(tsuid = Some(valueBytes.utf8String.trim))
          else
            state1
        val nextStep =
          if (state2.fmiEndPos.exists(_ <= state2.pos)) toDatasetStep(reader, state2)
          else new InFmiAttribute(state2, stop)
        ParseResult(
          Some(ValueElement(header.tag, updatedVr, Value(valueBytes), state2.bigEndian, state2.explicitVR)),
          nextStep
        )
      }
    }

    private def toDatasetStep(reader: ByteReader, state: FmiAttributeState): InAttribute = {
      val tsuid = state.tsuid.getOrElse {
        log.warn("Missing Transfer Syntax (0002,0010) - assume Explicit VR Little Endian")
        UID.ExplicitVRLittleEndian
      }

      val bigEndian  = tsuid == UID.ExplicitVRBigEndianRetired
      val explicitVR = tsuid != UID.ImplicitVRLittleEndian

      if (isDeflated(tsuid)) {
        reader.ensure(2)
        val firstTwoBytes = reader.remainingData.take(2)

        val inflater =
          if (hasZLIBHeader(firstTwoBytes)) {
            log.warn("Deflated DICOM Stream with ZLIB Header")
            new Inflater(false)
          } else
            new Inflater(true)

        reader.setInput(Compression.decompress(reader.remainingData, inflater))
        new InAttribute(
          AttributeState(maySwitchTs = true, bigEndian = bigEndian, explicitVR = explicitVR, Some(inflater)),
          stop
        )
      } else
        new InAttribute(AttributeState(maySwitchTs = true, bigEndian = bigEndian, explicitVR = explicitVR, None), stop)
    }
  }

  class InAttribute(state: AttributeState, stop: AttributeInfo => Boolean) extends DicomParseStep(Some(state), stop) {
    private val log: Logger = LoggerFactory.getLogger("ParserInAttributeLogger")

    private def maybeSwitchTs(reader: ByteReader, state: AttributeState): AttributeState = {
      reader.ensure(8)
      val data = reader.remainingData.take(8)
      val tag  = bytesToTag(data, state.bigEndian)
      val explicitVR =
        try {
          VR.withValue(bytesToVR(data.drop(4)))
          true
        } catch {
          case _: Throwable => false
        }
      if (isSpecial(tag))
        AttributeState(maySwitchTs = false, bigEndian = state.bigEndian, explicitVR = state.explicitVR, state.inflater)
      else if (state.explicitVR && !explicitVR) {
        log.warn("Implicit VR attributes detected in explicit VR dataset")
        AttributeState(maySwitchTs = false, bigEndian = state.bigEndian, explicitVR = false, state.inflater)
      } else if (!state.explicitVR && explicitVR)
        AttributeState(maySwitchTs = false, bigEndian = state.bigEndian, explicitVR = true, state.inflater)
      else
        AttributeState(maySwitchTs = false, bigEndian = state.bigEndian, explicitVR = state.explicitVR, state.inflater)
    }

    override def parse(reader: ByteReader): ParseResult[Element] = {
      val updatedState = if (state.maySwitchTs) maybeSwitchTs(reader, state) else state
      val header       = readHeader(reader, updatedState)
      reader.take(header.headerLength)
      if (header.vr != null)
        if (stop(header))
          ParseResult(None, FinishedParser)
        else if (header.vr == VR.SQ || (header.vr == VR.UN && header.valueLength == indeterminateLength))
          ParseResult(
            Some(SequenceElement(header.tag, header.valueLength, updatedState.bigEndian, updatedState.explicitVR)),
            new InAttribute(
              AttributeState(
                maySwitchTs = false,
                bigEndian = updatedState.bigEndian,
                explicitVR = updatedState.explicitVR,
                updatedState.inflater
              ),
              stop
            )
          )
        else if (header.valueLength == indeterminateLength)
          ParseResult(
            Some(FragmentsElement(header.tag, header.vr, updatedState.bigEndian, updatedState.explicitVR)),
            new InFragments(
              FragmentsState(updatedState.bigEndian, updatedState.explicitVR, updatedState.inflater),
              stop
            )
          )
        else
          ParseResult(
            Some(
              ValueElement(
                header.tag,
                header.vr,
                Value(reader.take(header.valueLength.toInt)),
                updatedState.bigEndian,
                updatedState.explicitVR
              )
            ),
            this
          )
      else
        header.tag match {
          case 0xfffee000 =>
            ParseResult(
              Some(ItemElement(header.valueLength, updatedState.bigEndian)),
              new InAttribute(
                AttributeState(
                  maySwitchTs = true,
                  bigEndian = updatedState.bigEndian,
                  explicitVR = updatedState.explicitVR,
                  updatedState.inflater
                ),
                stop
              )
            )
          case 0xfffee00d =>
            ParseResult(
              Some(ItemDelimitationElement(updatedState.bigEndian)),
              new InAttribute(
                AttributeState(
                  maySwitchTs = false,
                  bigEndian = updatedState.bigEndian,
                  explicitVR = updatedState.explicitVR,
                  updatedState.inflater
                ),
                stop
              )
            )
          case 0xfffee0dd =>
            ParseResult(
              Some(SequenceDelimitationElement(updatedState.bigEndian)),
              new InAttribute(
                AttributeState(
                  maySwitchTs = true,
                  bigEndian = updatedState.bigEndian,
                  explicitVR = updatedState.explicitVR,
                  updatedState.inflater
                ),
                stop
              )
            )
          case _ =>
            ParseResult(None, this)
        }
    }
  }

  class InFragments(state: FragmentsState, stop: AttributeInfo => Boolean) extends DicomParseStep(Some(state), stop) {
    private val log: Logger = LoggerFactory.getLogger("ParserInFragmentsLogger")

    def parse(reader: ByteReader): ParseResult[Element] = {
      val header = readHeader(reader, state)
      reader.take(header.headerLength)
      if (header.tag == 0xfffee000) {
        // begin fragment
        val valueBytes = reader.take(header.valueLength.toInt)
        ParseResult(
          Some(FragmentElement(header.valueLength, new Value(valueBytes), state.bigEndian)),
          new InFragments(FragmentsState(state.bigEndian, state.explicitVR, state.inflater), stop)
        )
      } else if (header.tag == 0xfffee0dd) {
        // end fragments
        if (header.valueLength != 0)
          log.warn("Unexpected fragments delimitation length " + header.valueLength)
        ParseResult(
          Some(SequenceDelimitationElement(state.bigEndian)),
          new InAttribute(
            AttributeState(
              maySwitchTs = false,
              bigEndian = state.bigEndian,
              explicitVR = state.explicitVR,
              state.inflater
            ),
            stop
          )
        )
      } else {
        reader.take(header.valueLength.toInt)
        log.warn("Unexpected element (" + tagToString(header.tag) + ") in fragments with length " + header.valueLength)
        ParseResult(None, this)
      }
    }
  }
}
