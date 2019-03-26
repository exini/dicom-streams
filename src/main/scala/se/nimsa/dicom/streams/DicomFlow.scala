/*
 * Copyright 2018 Lars Edenbrandt
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

package se.nimsa.dicom.streams

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import org.slf4j.{Logger, LoggerFactory}
import se.nimsa.dicom.data.DicomParts._
import se.nimsa.dicom.data.TagPath._
import se.nimsa.dicom.data.{Tag, TagPath, isGroupLength}

/**
  * This class defines events for modular construction of DICOM flows. Events correspond to the DICOM parts commonly
  * encountered in a stream of DICOM data. Subclasses and/or traits add functionality by overriding and implementing
  * events. The stream entering the stage can be modified as well as the mapping from DICOM part to event.
  */
abstract class DicomFlow[Out] {

  def onPreamble(part: PreamblePart): List[Out]
  def onHeader(part: HeaderPart): List[Out]
  def onValueChunk(part: ValueChunk): List[Out]
  def onSequence(part: SequencePart): List[Out]
  def onSequenceDelimitation(part: SequenceDelimitationPart): List[Out]
  def onFragments(part: FragmentsPart): List[Out]
  def onItem(part: ItemPart): List[Out]
  def onItemDelimitation(part: ItemDelimitationPart): List[Out]
  def onDeflatedChunk(part: DeflatedChunk): List[Out]
  def onUnknown(part: UnknownPart): List[Out]
  def onPart(part: DicomPart): List[Out]

  /**
    * @return the flow of `DicomPart`s entering the stage
    */
  def baseFlow: PartFlow = Flow[DicomPart]

  /**
    * This method defines the mapping from `DicomPart` to event used to create the stage, see 'DicomFlowFactory.create'.
    *
    * @param dicomPart the incoming 'DicomPart'
    * @return the `List` of `DicomPart`s
    */
  def handlePart(dicomPart: DicomPart): List[Out] = dicomPart match {
    case part: PreamblePart => onPreamble(part)
    case part: HeaderPart => onHeader(part)
    case part: ValueChunk => onValueChunk(part)
    case part: SequencePart => onSequence(part)
    case part: SequenceDelimitationPart => onSequenceDelimitation(part)
    case part: FragmentsPart => onFragments(part)
    case part: ItemPart => onItem(part)
    case part: ItemDelimitationPart => onItemDelimitation(part)
    case part: DeflatedChunk => onDeflatedChunk(part)
    case part: UnknownPart => onUnknown(part)
    case part => onPart(part)
  }
}

/**
  * Basic implementation of events where DICOM parts are simply passed on downstream. When implementing new flows, this
  * class can be overridden for the appropriate events.
  */
class IdentityFlow extends DicomFlow[DicomPart] {
  def onPreamble(part: PreamblePart): List[DicomPart] = part :: Nil
  def onHeader(part: HeaderPart): List[DicomPart] = part :: Nil
  def onValueChunk(part: ValueChunk): List[DicomPart] = part :: Nil
  def onSequence(part: SequencePart): List[DicomPart] = part :: Nil
  def onSequenceDelimitation(part: SequenceDelimitationPart): List[DicomPart] = part :: Nil
  def onFragments(part: FragmentsPart): List[DicomPart] = part :: Nil
  def onItem(part: ItemPart): List[DicomPart] = part :: Nil
  def onItemDelimitation(part: ItemDelimitationPart): List[DicomPart] = part :: Nil
  def onDeflatedChunk(part: DeflatedChunk): List[DicomPart] = part :: Nil
  def onUnknown(part: UnknownPart): List[DicomPart] = part :: Nil
  def onPart(part: DicomPart): List[DicomPart] = part :: Nil
}

/**
  * Similar implementation to `IdentityFlow` with the difference that all events forward to the `onPart` event. Useful for
  * simple filters which implement a common behavior for all DICOM parts. This implementation is then provided in the
  * `onPart` method.
  */
abstract class DeferToPartFlow[Out] extends DicomFlow[Out] {
  def onPreamble(part: PreamblePart): List[Out] = onPart(part)
  def onHeader(part: HeaderPart): List[Out] = onPart(part)
  def onValueChunk(part: ValueChunk): List[Out] = onPart(part)
  def onSequence(part: SequencePart): List[Out] = onPart(part)
  def onSequenceDelimitation(part: SequenceDelimitationPart): List[Out] = onPart(part)
  def onFragments(part: FragmentsPart): List[Out] = onPart(part)
  def onDeflatedChunk(part: DeflatedChunk): List[Out] = onPart(part)
  def onUnknown(part: UnknownPart): List[Out] = onPart(part)
  def onItem(part: ItemPart): List[Out] = onPart(part)
  def onItemDelimitation(part: ItemDelimitationPart): List[Out] = onPart(part)
}

case object DicomStartMarker extends MetaPart

/**
  * This mixin adds an event marking the start of the DICOM stream. It does not add DICOM parts to the stream.
  */
trait StartEvent[Out] extends DicomFlow[Out] {
  def onStart(): List[Out] = Nil

  override def baseFlow: PartFlow =
    super.baseFlow.prepend(Source.single(DicomStartMarker)) // add marker to start of stream

  override def handlePart(dicomPart: DicomPart): List[Out] = dicomPart match {
    case DicomStartMarker => onStart() // call event, do not emit marker
    case part => super.handlePart(part)
  }
}

case object DicomEndMarker extends MetaPart

/**
  * This mixin adds an event marking the end of the DICOM stream. It does not add DICOM parts to the stream.
  */
trait EndEvent[Out] extends DicomFlow[Out] {
  def onEnd(): List[Out] = Nil

  override def baseFlow: PartFlow =
    super.baseFlow.concat(Source.single(DicomEndMarker)) // add marker to end of stream

  override def handlePart(dicomPart: DicomPart): List[Out] = dicomPart match {
    case DicomEndMarker => onEnd() // call event, do not emit marker
    case part => super.handlePart(part)
  }
}

trait InFragments[Out] extends DicomFlow[Out] {
  protected var inFragments: Boolean = false

  abstract override def onFragments(part: FragmentsPart): List[Out] = {
    inFragments = true
    super.onFragments(part)
  }

  abstract override def onSequenceDelimitation(part: SequenceDelimitationPart): List[Out] = {
    inFragments = false
    super.onSequenceDelimitation(part)
  }
}

trait InSequence[Out] extends DicomFlow[Out] with GuaranteedDelimitationEvents[Out] {
  protected var sequenceDepth = 0

  def inSequence: Boolean = sequenceDepth > 0

  abstract override def onSequence(part: SequencePart): List[Out] = {
    sequenceDepth += 1
    super.onSequence(part)
  }

  abstract override def onSequenceDelimitation(part: SequenceDelimitationPart): List[Out] = {
    sequenceDepth -= 1
    super.onSequenceDelimitation(part)
  }
}

object ValueChunkMarker extends ValueChunk(bigEndian = false, ByteString.empty, last = true)

/**
  * This mixin makes sure the `onValueChunk` event is called also for empty elements. This special case requires
  * special handling since empty elements consist of a `HeaderPart`, but is not followed by a `ValueChunk`.
  */
trait GuaranteedValueEvent[Out] extends InFragments[Out] {

  abstract override def onHeader(part: HeaderPart): List[Out] =
    if (part.length == 0)
      super.onHeader(part) ::: onValueChunk(ValueChunkMarker)
    else
      super.onHeader(part)

  abstract override def onItem(part: ItemPart): List[Out] =
    if (inFragments && part.length == 0)
      super.onItem(part) ::: onValueChunk(ValueChunkMarker)
    else
      super.onItem(part)

  abstract override def onValueChunk(part: ValueChunk): List[Out] =
    super.onValueChunk(part).filterNot(_ == ValueChunkMarker)
}

object SequenceDelimitationPartMarker extends SequenceDelimitationPart(bigEndian = false, ByteString.empty)

class ItemDelimitationPartMarker(item: Int) extends ItemDelimitationPart(item, bigEndian = false, ByteString.empty)

/**
  * By mixing in this trait, sequences and items with determinate length will be concluded by delimitation events, just
  * as is the case with sequences and items with indeterminate length and which are concluded by delimitation parts. This
  * makes it easier to create DICOM flows that react to the beginning and ending of sequences and items, as no special
  * care has to be taken for DICOM data with determinate length sequences and items.
  */
trait GuaranteedDelimitationEvents[Out] extends InFragments[Out] {
  protected var partStack: List[(LengthPart, Long)] = Nil

  def subtractLength(part: DicomPart): List[(LengthPart, Long)] = partStack.map { case (p, bytesLeft) => (p, bytesLeft - part.bytes.length) }

  def maybeDelimit(): List[Out] = {
    val delimits: List[DicomPart] = partStack
      .filter(_._2 <= 0) // find items and sequences that have ended
      .map { // create delimiters for those
      case (item: ItemPart, _) => new ItemDelimitationPartMarker(item.index)
      case (_, _) => SequenceDelimitationPartMarker
    }
    partStack = partStack.filter(_._2 > 0) // only keep items and sequences with bytes left to subtract
    delimits.flatMap { // call events, any items will be inserted in stream
      case d: SequenceDelimitationPart => onSequenceDelimitation(d)
      case d: ItemDelimitationPart => onItemDelimitation(d)
    }
  }

  def subtractAndEmit[A <: DicomPart](part: A, handle: A => List[Out]): List[Out] = {
    partStack = subtractLength(part)
    handle(part) ::: maybeDelimit()
  }

  abstract override def onSequence(part: SequencePart): List[Out] =
    if (!part.indeterminate) {
      partStack = (part, part.length) +: subtractLength(part)
      super.onSequence(part) ::: maybeDelimit()
    } else subtractAndEmit(part, super.onSequence)

  abstract override def onItem(part: ItemPart): List[Out] =
    if (!inFragments && !part.indeterminate) {
      partStack = (part, part.length) +: subtractLength(part)
      super.onItem(part) ::: maybeDelimit()
    } else subtractAndEmit(part, super.onItem)

  abstract override def onSequenceDelimitation(part: SequenceDelimitationPart): List[Out] =
    subtractAndEmit(part, (p: SequenceDelimitationPart) =>
      super.onSequenceDelimitation(p).filterNot(_ == SequenceDelimitationPartMarker))

  abstract override def onItemDelimitation(part: ItemDelimitationPart): List[Out] =
    subtractAndEmit(part, (p: ItemDelimitationPart) =>
      super.onItemDelimitation(p).filterNot(_.isInstanceOf[ItemDelimitationPartMarker]))

  abstract override def onHeader(part: HeaderPart): List[Out] = subtractAndEmit(part, super.onHeader)
  abstract override def onValueChunk(part: ValueChunk): List[Out] = subtractAndEmit(part, super.onValueChunk)
  abstract override def onFragments(part: FragmentsPart): List[Out] = subtractAndEmit(part, super.onFragments)
}

/**
  * This mixin keeps track of the current tag path as the stream moves through attributes, sequences and fragments. The
  * tag path state is updated in the event callbacks. This means that implementations of events using this mixin must
  * remember to call the corresponding super method for the tag path to update.
  */
trait TagPathTracking[Out] extends DicomFlow[Out] with GuaranteedValueEvent[Out] with GuaranteedDelimitationEvents[Out] {

  protected var tagPath: TagPath = EmptyTagPath

  abstract override def onHeader(part: HeaderPart): List[Out] = {
    tagPath = tagPath match {
      case t: TagPathItem => t.thenTag(part.tag)
      case t => t.previous.thenTag(part.tag)
    }
    super.onHeader(part)
  }

  abstract override def onFragments(part: FragmentsPart): List[Out] = {
    tagPath = tagPath match {
      case t: TagPathItem => t.thenTag(part.tag)
      case t => t.previous.thenTag(part.tag)
    }
    super.onFragments(part)
  }

  abstract override def onSequence(part: SequencePart): List[Out] = {
    tagPath = tagPath match {
      case t: TagPathItem => t.thenSequence(part.tag)
      case t => t.previous.thenSequence(part.tag)
    }
    super.onSequence(part)
  }

  abstract override def onSequenceDelimitation(part: SequenceDelimitationPart): List[Out] = {
    if (!inFragments) tagPath = tagPath.previous.thenSequenceEnd(tagPath.tag)
    super.onSequenceDelimitation(part)
  }

  abstract override def onItem(part: ItemPart): List[Out] = {
    if (!inFragments) tagPath = tagPath.previous.thenItem(tagPath.tag, part.index)
    super.onItem(part)
  }

  abstract override def onItemDelimitation(part: ItemDelimitationPart): List[Out] = {
    tagPath = tagPath match {
      case t: TagPathItem => t.previous.thenItemEnd(t.tag, t.item)
      case t => t.previous match {
        case ti: TagPathItem => ti.previous.thenItemEnd(ti.tag, ti.item)
        case _ => tagPath // should never get delimitation when not inside item
      }
    }
    super.onItemDelimitation(part)
  }

}

/**
  * This mixin will log warnings when group length tags are encountered (except the file meta information group length
  * tag), or when sequences or items with determinate length are encountered. This is useful in flows which alter the
  * DICOM information such that these length attributes may no longer be correct. This reminds the user to re-encode the
  * stream to indeterminate length sequences and items, and to remove group length attributes.
  */
trait GroupLengthWarnings[Out] extends DicomFlow[Out] with InFragments[Out] {
  protected val log: Logger = LoggerFactory.getLogger("GroupLengthWarningsLogger")
  protected var silent = false // opt out of warnings

  abstract override def onHeader(part: HeaderPart): List[Out] = {
    if (!silent && isGroupLength(part.tag) && part.tag != Tag.FileMetaInformationGroupLength)
      log.warn("Group length attribute detected, consider removing group lengths to maintain valid DICOM information")
    super.onHeader(part)
  }

  abstract override def onSequence(part: SequencePart): List[Out] = {
    if (!silent && !part.indeterminate && part.length > 0)
      log.warn("Determinate length sequence detected, consider re-encoding sequences to indeterminate length to maintain valid DICOM information")
    super.onSequence(part)
  }

  abstract override def onItem(part: ItemPart): List[Out] = {
    if (!silent && !inFragments && !part.indeterminate && part.length > 0)
      log.warn("Determinate length item detected, consider re-encoding items to indeterminate length to maintain valid DICOM information")
    super.onItem(part)
  }

}

/**
  * Provides factory methods for creating `DicomFlow`s
  */
object DicomFlowFactory {

  def create[Out](flow: DicomFlow[Out]): Flow[DicomPart, Out, NotUsed] = flow.baseFlow.mapConcat(flow.handlePart)

}
