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

import java.util.zip.Deflater

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import com.exini.dicom.data.CharacterSets.utf8Charset
import com.exini.dicom.data.DicomParts._
import com.exini.dicom.data.Elements.ValueElement
import com.exini.dicom.data.TagPath.EmptyTagPath
import com.exini.dicom.data.VR.VR
import com.exini.dicom.data._
import com.exini.dicom.streams.CollectFlow._
import com.exini.dicom.streams.ModifyFlow._

/**
  * Various flows for transforming data of <code>DicomPart</code>s.
  */
object DicomFlows {

  case class ValidationContext(sopClassUID: String, transferSyntax: String)


  /**
    * Print each element and then pass it on unchanged.
    *
    * @return the associated flow
    */
  def printFlow[A]: Flow[A, A, NotUsed] = Flow.fromFunction { a =>
    println(a)
    a
  }

  /**
    * Filter a stream of dicom parts such that all elements except those with tags in the white list are discarded.
    *
    * Note that it is up to the user of this function to make sure the modified DICOM data is valid.
    *
    * @param whitelist        list of tag paths to keep.
    * @param defaultCondition determines whether to keep or discard elements with no tag path such as the preamble and
    *                         synthetic DICOM parts inserted to hold state.
    * @return the associated filter Flow
    */
  def whitelistFilter(whitelist: Set[_ <: TagTree], defaultCondition: DicomPart => Boolean = _ => true, logGroupLengthWarnings: Boolean = true): PartFlow =
    tagFilter(currentPath => whitelist.exists(t => t.hasTrunk(currentPath) || t.isTrunkOf(currentPath)), defaultCondition, logGroupLengthWarnings)

  /**
    * Filter a stream of dicom parts such that elements with tag paths in the black list are discarded. Tag paths in
    * the blacklist are removed in the root dataset as well as any sequences, and entire sequences or items in sequences
    * can be removed.
    *
    * Note that it is up to the user of this function to make sure the modified DICOM data is valid.
    *
    * @param blacklist        list of tag paths to discard.
    * @param defaultCondition determines whether to keep or discard elements with no tag path such as the preamble and
    *                         synthetic DICOM parts inserted to hold state.
    * @return the associated filter Flow
    */
  def blacklistFilter(blacklist: Set[_ <: TagTree], defaultCondition: DicomPart => Boolean = _ => true, logGroupLengthWarnings: Boolean = true): PartFlow =
    tagFilter(currentPath => !blacklist.exists(_.isTrunkOf(currentPath)), defaultCondition, logGroupLengthWarnings)

  /**
    * Filter a stream of dicom parts such that all elements that are group length elements except
    * file meta information group length, will be discarded. Group Length (gggg,0000) Standard Data Elements
    * have been retired in the standard.
    *
    * Note that it is up to the user of this function to make sure the modified DICOM data is valid.
    *
    * @return the associated filter Flow
    */
  def groupLengthDiscardFilter: PartFlow =
    tagFilter(tagPath => !isGroupLength(tagPath.tag) || tagPath.tag == Tag.FileMetaInformationGroupLength, _ => true, logGroupLengthWarnings = false)

  /**
    * Discards the file meta information.
    *
    * @return the associated filter Flow
    */
  def fmiDiscardFilter: PartFlow =
    tagFilter(tagPath => !isFileMetaInformation(tagPath.tag), {
      case _: PreamblePart => false
      case _ => true
    }, logGroupLengthWarnings = false)

  /**
    * Filter a stream of DICOM parts leaving only those for which the supplied tag condition is `true`. As the stream of
    * dicom parts is flowing, a `TagPath` state is updated. For each such update, the tag condition is evaluated. If it
    * renders `false`, parts are discarded until it renders `true` again.
    *
    * Note that it is up to the user of this function to make sure the modified DICOM data is valid. When filtering
    * items from a sequence, item indices are preserved (i.e. not updated).
    *
    * @param keepCondition          function that determines if DICOM parts should be discarded (condition false) based on the
    *                               current tag path
    * @param defaultCondition       determines whether to keep or discard elements with no tag path such as the preamble and
    *                               synthetic DICOM parts inserted to hold state.
    * @param logGroupLengthWarnings determines whether to log a warning when group length tags, or sequences or items
    *                               with determinate length are encountered
    * @return the filtered flow
    */
  def tagFilter(keepCondition: TagPath => Boolean, defaultCondition: DicomPart => Boolean = _ => true, logGroupLengthWarnings: Boolean = true): PartFlow =
    DicomFlowFactory.create(new DeferToPartFlow[DicomPart] with TagPathTracking[DicomPart] with GroupLengthWarnings[DicomPart] {
      silent = !logGroupLengthWarnings

      override def onPart(part: DicomPart): List[DicomPart] = {
        val keeping = tagPath match {
          case EmptyTagPath => defaultCondition(part)
          case path => keepCondition(path)
        }
        if (keeping) part :: Nil else Nil
      }
    })

  /**
    * Filter a stream of DICOM elements based on its element header and the associated keep condition. All other
    * parts are not filtered out (preamble, sequences, items, delimitations etc).
    *
    * @param keepCondition          function that determines if an element should be discarded (condition false) based on the
    *                               most recently encountered element header
    * @param logGroupLengthWarnings determines whether to log a warning when group length tags, or sequences or items
    *                               with determinate length are encountered
    * @return the filtered flow
    */
  def headerFilter(keepCondition: HeaderPart => Boolean, logGroupLengthWarnings: Boolean = true): PartFlow =
    DicomFlowFactory.create(new DeferToPartFlow[DicomPart] with GroupLengthWarnings[DicomPart] {
      silent = !logGroupLengthWarnings
      var keeping = true

      override def onPart(part: DicomPart): List[DicomPart] = part match {
        case p: HeaderPart =>
          keeping = keepCondition(p)
          if (keeping) p :: Nil else Nil
        case p: ValueChunk =>
          if (keeping) p :: Nil else Nil
        case p =>
          keeping = true
          p :: Nil
      }
    })

  def tagHeaderFilter(keepCondition: (TagPath, HeaderPart) => Boolean, logGroupLengthWarnings: Boolean = true): PartFlow =
    DicomFlowFactory.create(new DeferToPartFlow[DicomPart] with TagPathTracking[DicomPart] with GroupLengthWarnings[DicomPart] {
      silent = !logGroupLengthWarnings
      var keeping = true

      override def onPart(part: DicomPart): List[DicomPart] = part match {
        case p: HeaderPart =>
          keeping = keepCondition(tagPath, p)
          if (keeping) p :: Nil else Nil
        case p: ValueChunk =>
          if (keeping) p :: Nil else Nil
        case p =>
          keeping = true
          p :: Nil
      }
    })

  /**
    * A flow which passes on the input parts unchanged, but fails for DICOM files which has a presentation context
    * (combination of Media Storage SOP Class UID and Transfer Syntax UID) that is not present in the input sequence of
    * allowed contexts.
    *
    * @param contexts valid contexts
    * @return the flow unchanged unless interrupted by failing the context check
    */
  def validateContextFlow(contexts: Seq[ValidationContext]): PartFlow =
    collectFlow(Set(TagPath.fromTag(Tag.MediaStorageSOPClassUID), TagPath.fromTag(Tag.TransferSyntaxUID), TagPath.fromTag(Tag.SOPClassUID)), "validatecontext")
      .mapConcat {
        case e: ElementsPart if e.label == "validatecontext" =>
          val scuid = e.elements.getString(Tag.MediaStorageSOPClassUID).orElse(e.elements.getString(Tag.SOPClassUID)).getOrElse("<empty>")
          val tsuid = e.elements.getString(Tag.TransferSyntaxUID).getOrElse("<empty>")
          if (contexts.contains(ValidationContext(scuid, tsuid)))
            Nil
          else
            throw new DicomStreamException(s"The presentation context [SOPClassUID = $scuid, TransferSyntaxUID = $tsuid] is not supported")
        case p =>
          p :: Nil
      }

  /**
    * A flow which deflates the dataset but leaves the meta information intact. Useful when the dicom parsing in `DicomParseFlow`
    * has inflated a deflated (`1.2.840.10008.1.2.1.99 or 1.2.840.10008.1.2.4.95`) file, and analyzed and possibly transformed its
    * elements. At that stage, in order to maintain valid DICOM information, one can either change the transfer syntax to
    * an appropriate value for non-deflated data, or deflate the data again. This flow helps with the latter.
    *
    * This flow may produce deflated data also when the dataset or the entire stream is empty as the deflate stage may
    * output data on finalization.
    *
    * @return the associated `DicomPart` `Flow`
    */
  val deflateDatasetFlow: PartFlow =
    Flow[DicomPart]
      .concat(Source.single(DicomEndMarker))
      .statefulMapConcat {
        () =>
          var inFmi = false
          var collectingTs = false
          var tsBytes = ByteString.empty
          var shouldDeflate = false
          val buffer = new Array[Byte](8192)
          val deflater = new Deflater(-1, true)

          def deflate(dicomPart: DicomPart) = {
            val input = dicomPart.bytes
            deflater.setInput(input.toArray)
            var output = ByteString.empty
            while (!deflater.needsInput) {
              val bytesDeflated = deflater.deflate(buffer)
              output = output ++ ByteString(buffer.take(bytesDeflated))
            }
            if (output.isEmpty) Nil else DeflatedChunk(dicomPart.bigEndian, output, nowrap = true) :: Nil
          }

          def finishDeflating() = {
            deflater.finish()
            var output = ByteString.empty
            var done = false
            while (!done) {
              val bytesDeflated = deflater.deflate(buffer)
              if (bytesDeflated == 0)
                done = true
              else
                output = output ++ ByteString(buffer.take(bytesDeflated))
            }
            deflater.end()
            if (output.isEmpty) Nil else DeflatedChunk(bigEndian = false, output, nowrap = true) :: Nil
          }

        {
          case DicomEndMarker => // end of stream, make sure deflater writes final bytes if deflating has occurred
            if (shouldDeflate && deflater.getBytesRead > 0) finishDeflating() else Nil
          case header: HeaderPart if header.isFmi => // FMI, do not deflate and remember we are in FMI
            inFmi = true
            collectingTs = header.tag == Tag.TransferSyntaxUID
            header :: Nil
          case value: ValueChunk if collectingTs => // collect transfer syntax bytes so we can check if deflated
            tsBytes = tsBytes ++ value.bytes
            value :: Nil
          case header: HeaderPart => // dataset header, remember we are no longer in FMI, deflate
            inFmi = false
            collectingTs = false
            shouldDeflate = tsBytes.utf8String.trim == UID.DeflatedExplicitVRLittleEndian
            if (shouldDeflate) deflate(header) else header :: Nil
          case part if inFmi => // for any dicom part within FMI, do not deflate
            part :: Nil
          case deflatedChunk: DeflatedChunk => // already deflated, pass as-is
            deflatedChunk :: Nil
          case part => // for all other cases, deflate if we should deflate
            if (shouldDeflate) deflate(part) else part :: Nil
        }
      }

  /**
    * Remove elements from stream that may contain large quantities of data (bulk data)
    *
    * Rules ported from [[https://github.com/dcm4che/dcm4che/blob/3.3.8/dcm4che-core/src/main/java/org/dcm4che3/io/BulkDataDescriptor.java#L58 dcm4che]].
    * Defined [[http://dicom.nema.org/medical/dicom/current/output/html/part04.html#table_Z.1-1 here in the DICOM standard]].
    *
    * Note that it is up to the user of this function to make sure the modified DICOM data is valid.
    *
    * @return the associated DicomPart Flow
    */
  val bulkDataFilter: PartFlow =
    Flow[DicomPart]
      .statefulMapConcat {

        def normalizeRepeatingGroup(tag: Int) = {
          val gg000000 = tag & 0xffe00000
          if (gg000000 == 0x50000000 || gg000000 == 0x60000000) tag & 0xffe0ffff else tag
        }

        () =>
          var sequenceStack = Seq.empty[SequencePart]
          var discarding = false

        {
          case sq: SequencePart =>
            sequenceStack = sq +: sequenceStack
            sq :: Nil
          case sqd: SequenceDelimitationPart =>
            sequenceStack = sequenceStack.drop(1)
            sqd :: Nil
          case dh: HeaderPart =>
            discarding =
              normalizeRepeatingGroup(dh.tag) match {
                case Tag.PixelDataProviderURL => true
                case Tag.AudioSampleData => true
                case Tag.CurveData => true
                case Tag.SpectroscopyData => true
                case Tag.OverlayData => true
                case Tag.EncapsulatedDocument => true
                case Tag.FloatPixelData => true
                case Tag.DoubleFloatPixelData => true
                case Tag.PixelData => sequenceStack.isEmpty
                case Tag.WaveformData => sequenceStack.length == 1 && sequenceStack.head.tag == Tag.WaveformSequence
                case _ => false
              }
            if (discarding) Nil else dh :: Nil
          case dvc: ValueChunk =>
            if (discarding) Nil else dvc :: Nil
          case p: DicomPart =>
            discarding = false
            p :: Nil
        }
      }

  /**
    * Buffers all file meta information elements and calculates their lengths, then emits the correct file meta
    * information group length attribute followed by remaining FMI.
    */
  def fmiGroupLengthFlow: PartFlow = Flow[DicomPart]
    .via(collectFlow(tagPath => tagPath.isRoot && isFileMetaInformation(tagPath.tag), tagPath => !isFileMetaInformation(tagPath.tag), "fmigrouplength", 0))
    .via(tagFilter(tagPath => !isFileMetaInformation(tagPath.tag), _ => true, logGroupLengthWarnings = false))
    .via(DicomFlowFactory.create(new DeferToPartFlow[DicomPart] with EndEvent[DicomPart] {
      var fmi = List.empty[DicomPart]
      var hasEmitted = false

      override def onEnd(): List[DicomPart] =
        if (this.hasEmitted) Nil else fmi

      override def onPart(part: DicomPart): List[DicomPart] = part match {

        case fmiElements: ElementsPart if fmiElements.label == "fmigrouplength" =>
          val elements = fmiElements.elements
          if (elements.data.nonEmpty) {
            val bigEndian = elements.data.headOption.exists(_.bigEndian)
            val explicitVR = elements.data.headOption.forall(_.explicitVR)
            val fmiElementsNoLength = elements.filter(_.tag != Tag.FileMetaInformationGroupLength)
            val length = fmiElementsNoLength.data.map(_.toBytes.length).sum
            val lengthHeader = HeaderPart(Tag.FileMetaInformationGroupLength, VR.UL, 4, isFmi = true, bigEndian, explicitVR)
            val lengthChunk = ValueChunk(bigEndian, intToBytes(length, bigEndian), last = true)
            fmi = lengthHeader :: lengthChunk :: fmiElementsNoLength.toParts
          }
          Nil

        case p if !hasEmitted && p.bytes.nonEmpty =>
            hasEmitted = true
            p match {
              case preamble: PreamblePart =>
                preamble :: fmi
              case _ =>
                fmi ::: p :: Nil
            }

        case p =>
          p :: Nil
      }
    }))

  /**
    * Remove all DICOM parts that do not contribute to file bytes
    */
  val emptyPartsFilter: PartFlow = Flow[DicomPart].filter(_.bytes.nonEmpty)

  /**
    * Sets any sequences and/or items with known length to indeterminate length and inserts delimiters.
    */
  def toIndeterminateLengthSequences: PartFlow =
    DicomFlowFactory.create(new IdentityFlow with GuaranteedDelimitationEvents[DicomPart] { // map to indeterminate length
      val indeterminateBytes = ByteString(0xFF, 0xFF, 0xFF, 0xFF)

      override def onSequence(part: SequencePart): List[DicomPart] =
        super.onSequence(part).map {
          case s: SequencePart if !s.indeterminate => part.copy(length = indeterminateLength, bytes = part.bytes.dropRight(4) ++ indeterminateBytes)
          case p => p
        }

      override def onSequenceDelimitation(part: SequenceDelimitationPart): List[DicomPart] =
        super.onSequenceDelimitation(part) ::: (
          if (part.bytes.isEmpty)
            SequenceDelimitationPart(part.bigEndian, sequenceDelimitation(part.bigEndian)) :: Nil
          else
            Nil)

      override def onItem(part: ItemPart): List[DicomPart] =
        super.onItem(part).map {
          case i: ItemPart if !inFragments && !i.indeterminate => part.copy(length = indeterminateLength, bytes = part.bytes.dropRight(4) ++ indeterminateBytes)
          case p => p
        }

      override def onItemDelimitation(part: ItemDelimitationPart): List[DicomPart] =
        super.onItemDelimitation(part) ::: (
          if (part.bytes.isEmpty)
            ItemDelimitationPart(part.index, part.bigEndian, itemDelimitation(part.bigEndian)) :: Nil
          else
            Nil)
    })

  /**
    * Convert all string values to UTF-8 corresponding to the DICOM character set ISO_IR 192. First collects the
    * SpecificCharacterSet element. Any subsequent elements of VR type LO, LT, PN, SH, ST or UT will be decoded
    * using the character set(s) of the dataset, and replaced by a value encoded using UTF-8.
    *
    * Changing element values will change and update the length of individual elements, but does not update group
    * length elements and sequences and items with specified length. The recommended approach is to remove group
    * length elements and make sure sequences and items have indeterminate length using appropriate flows.
    *
    * @return the associated DicomPart Flow
    */
  def toUtf8Flow: PartFlow = Flow[DicomPart]
    .via(collectFlow(Set(TagPath.fromTag(Tag.SpecificCharacterSet)), "toutf8"))
    .via(modifyFlow(insertions = Seq(TagInsertion(TagPath.fromTag(Tag.SpecificCharacterSet), _ => ByteString("ISO_IR 192")))))
    .statefulMapConcat {

      () =>
        var characterSets: CharacterSets = defaultCharacterSet
        var currentHeader: Option[HeaderPart] = None
        var currentValue = ByteString.empty

      {
        case attr: ElementsPart if attr.label == "toutf8" =>
          characterSets = attr.elements(Tag.SpecificCharacterSet).map {
            case e: ValueElement => CharacterSets(e)
            case _ => characterSets
          }.getOrElse(characterSets)
          Nil
        case header: HeaderPart =>
          if (header.length > 0 && CharacterSets.isVrAffectedBySpecificCharacterSet(header.vr)) {
            currentHeader = Some(header)
            currentValue = ByteString.empty
            Nil
          } else {
            currentHeader = None
            header :: Nil
          }
        case value: ValueChunk if currentHeader.isDefined =>
          currentValue = currentValue ++ value.bytes
          if (value.last) {
            val newValue = currentHeader
              .map(h => characterSets.decode(h.vr, currentValue).getBytes(utf8Charset))
              .map(ByteString.apply)
            val newLength = newValue.map(_.length)
            val newElement = for {
              h <- currentHeader
              v <- newValue
              l <- newLength
            } yield h.withUpdatedLength(l) :: ValueChunk(h.bigEndian, v, last = true) :: Nil
            newElement.getOrElse(Nil)
          } else Nil
        case p: DicomPart =>
          p :: Nil
      }
    }

  /**
    * Ensure that the data has transfer syntax explicit VR little endian. Changes the TransferSyntaxUID, if present,
    * to 1.2.840.10008.1.2.1 to reflect this.
    *
    * @return the associated DicomPart Flow
    */
  def toExplicitVrLittleEndianFlow: PartFlow =
    modifyFlow(modifications = Seq(TagModification.equals(TagPath.fromTag(Tag.TransferSyntaxUID), _ => padToEvenLength(ByteString(UID.ExplicitVRLittleEndian), VR.UI))))
      .via(fmiGroupLengthFlow)
      .statefulMapConcat {

        case class SwapResult(bytes: ByteString, carry: ByteString)

        def swap(k: Int, b: ByteString): SwapResult =
          SwapResult(b.grouped(k).map(_.reverse).reduce(_ ++ _), b.takeRight(b.length % k))

        () =>
          var currentVr: Option[VR] = None
          var carryBytes = ByteString.empty

          def updatedValue(swapResult: SwapResult, last: Boolean): ValueChunk = {
            carryBytes = swapResult.carry
            if (last && carryBytes.nonEmpty)
              throw new DicomStreamException("Dicom value length does not match length specified in header")
            ValueChunk(bigEndian = false, swapResult.bytes, last = last)
          }

        {
          case h: HeaderPart if h.bigEndian || !h.explicitVR =>
            if (h.bigEndian) {
              carryBytes = ByteString.empty
              currentVr = Some(h.vr)
            } else currentVr = None
            HeaderPart(h.tag, h.vr, h.length, h.isFmi) :: Nil

          case v: ValueChunk if currentVr.isDefined && v.bigEndian =>
            currentVr match {
              case Some(vr) if vr == VR.US || vr == VR.SS || vr == VR.OW || vr == VR.AT => // 2 byte
                updatedValue(swap(2, carryBytes ++ v.bytes), v.last) :: Nil
              case Some(vr) if vr == VR.OF || vr == VR.UL || vr == VR.SL || vr == VR.FL => // 4 bytes
                updatedValue(swap(4, carryBytes ++ v.bytes), v.last) :: Nil
              case Some(vr) if vr == VR.OD || vr == VR.FD => // 8 bytes
                updatedValue(swap(8, carryBytes ++ v.bytes), v.last) :: Nil
              case _ => v :: Nil
            }

          case p: DicomPart if !p.bigEndian => p :: Nil

          case s: SequencePart =>
            SequencePart(s.tag, s.length, bigEndian = false, explicitVR = true,
              tagToBytesLE(s.tag) ++ ByteString('S', 'Q', 0, 0) ++ s.bytes.takeRight(4).reverse) :: Nil

          case _: SequenceDelimitationPart =>
            SequenceDelimitationPart(bigEndian = false,
              tagToBytesLE(Tag.SequenceDelimitationItem) ++ ByteString(0, 0, 0, 0)) :: Nil

          case i: ItemPart =>
            ItemPart(i.index, i.length, bigEndian = false,
              tagToBytesLE(Tag.Item) ++ i.bytes.takeRight(4).reverse) :: Nil

          case i: ItemDelimitationPart =>
            ItemDelimitationPart(i.index, bigEndian = false,
              tagToBytesLE(Tag.ItemDelimitationItem) ++ ByteString(0, 0, 0, 0)) :: Nil

          case f: FragmentsPart =>
            if (f.bigEndian) {
              carryBytes = ByteString.empty
              currentVr = Some(f.vr)
            } else currentVr = None
            FragmentsPart(f.tag, f.length, f.vr, bigEndian = false, explicitVR = true,
              tagToBytesLE(f.tag) ++ f.bytes.drop(4).take(4) ++ f.bytes.takeRight(4).reverse) :: Nil

          case p => p :: Nil
        }
      }
}


