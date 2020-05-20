package com.exini.dicom.data

import java.time.ZoneOffset

import com.exini.dicom.data.DicomElements._
import com.exini.dicom.data.Elements._
import org.slf4j.{ Logger, LoggerFactory }

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class ElementAndLength(element: Element, var bytesLeft: Long)

class ElementsBuilder() {

  protected val log: Logger = LoggerFactory.getLogger("ElementsBuilderLogger")

  private val builderStack: mutable.Stack[DatasetBuilder] =
    mutable.Stack(new DatasetBuilder(defaultCharacterSet, systemZone))
  private val sequenceStack: mutable.Stack[Sequence]       = mutable.Stack.empty
  private val lengthStack: mutable.Stack[ElementAndLength] = mutable.Stack.empty
  private var fragments: Option[Fragments]                 = None

  /**
    * Add the input element to the build
    * @param element input element
    * @return this builder
    */
  def +=(element: Element): ElementsBuilder =
    element match {

      case e: ValueElement =>
        subtractLength(e.length + e.vr.headerLength)
        val builder = builderStack.top
        builder += e
        maybeDelimit()

      case e: FragmentsElement =>
        subtractLength(e.vr.headerLength)
        updateFragments(Some(Fragments.empty(e.tag, e.vr, e.bigEndian, e.explicitVR)))
        maybeDelimit()

      case e: FragmentElement =>
        subtractLength(8 + e.length)
        updateFragments(fragments.map(_ + new Fragment(e.length, e.value, e.bigEndian)))
        maybeDelimit()

      case _: SequenceDelimitationElement if hasFragments =>
        subtractLength(8)
        val builder = builderStack.top
        builder += fragments.get
        updateFragments(None)
        maybeDelimit()

      case e: SequenceElement =>
        subtractLength(12)
        if (!e.indeterminate)
          pushLength(e, e.length)
        pushSequence(Sequence.empty(e.tag, if (e.indeterminate) e.length else 0, e.bigEndian, e.explicitVR))
        maybeDelimit()

      case e: ItemElement if hasSequence =>
        subtractLength(8)
        val builder  = builderStack.top
        val sequence = sequenceStack.top + Item.empty(if (e.indeterminate) e.length else 0, e.bigEndian)
        if (!e.indeterminate)
          pushLength(e, e.length)
        pushBuilder(new DatasetBuilder(builder.characterSets, builder.zoneOffset))
        updateSequence(sequence)
        maybeDelimit()

      case _: ItemDelimitationElement if hasSequence =>
        subtractLength(8)
        endItem()
        maybeDelimit()

      case _: SequenceDelimitationElement if hasSequence =>
        subtractLength(8)
        endSequence()
        maybeDelimit()

      case e =>
        log.warn(s"Unexpected element $e")
        subtractLength(e.toBytes.length)
        maybeDelimit()
    }

  /**
    * Let the builder know the input element was encountered on the stream but should do not add it. This function is
    * necessary for bookkeeping in sequences of determinate length
    * @param element encountered Element
    * @return this builder
    */
  def !!(element: Element): ElementsBuilder = {
    subtractLength(element.toBytes.length)
    maybeDelimit()
  }

  def currentDepth: Int = sequenceStack.length

  def build(): Elements = builderStack.headOption.map(_.build()).getOrElse(Elements.empty())

  private def updateSequence(sequence: Sequence): Unit = {
    if (sequenceStack.nonEmpty) sequenceStack.pop()
    sequenceStack.push(sequence)
  }

  private def updateFragments(fragments: Option[Fragments]): Unit = this.fragments = fragments

  private def subtractLength(length: Long): Unit               = lengthStack.foreach(l => l.bytesLeft -= length)
  private def pushBuilder(builder: DatasetBuilder): Unit       = builderStack.push(builder)
  private def pushSequence(sequence: Sequence): Unit           = sequenceStack.push(sequence)
  private def pushLength(element: Element, length: Long): Unit = lengthStack.push(ElementAndLength(element, length))
  private def popBuilder(): Unit                               = builderStack.pop()
  private def popSequence(): Unit                              = sequenceStack.pop()
  private def hasSequence: Boolean                             = sequenceStack.nonEmpty
  private def hasFragments: Boolean                            = fragments.isDefined
  private def endItem(): Unit = {
    val builder  = builderStack.top
    val sequence = sequenceStack.top
    val elements = builder.build()
    val items    = sequence.items
    if (items.nonEmpty) {
      val updatedSequence = sequence.setItem(items.length, sequence.item(items.length).get.setElements(elements))
      popBuilder()
      updateSequence(updatedSequence)
    }
  }
  private def endSequence(): Unit = {
    val seq        = sequenceStack.top
    val seqLength  = if (seq.indeterminate) seq.length else seq.toBytes.length - 12
    val updatedSeq = new Sequence(seq.tag, seqLength, seq.items, seq.bigEndian, seq.explicitVR)
    val builder    = builderStack.top
    builder += updatedSeq
    popSequence()
  }
  private def maybeDelimit(): ElementsBuilder = {
    lengthStack.popWhile(_.bytesLeft <= 0).map(_.element).foreach {
      case _: ItemElement => endItem()
      case _              => endSequence()
    }
    this
  }
}

class DatasetBuilder(var characterSets: CharacterSets, var zoneOffset: ZoneOffset) {
  private val data: ArrayBuffer[ElementSet] = ArrayBuffer.empty

  def +=(elementSet: ElementSet): DatasetBuilder = {
    elementSet match {
      case e: ValueElement if e.tag == Tag.SpecificCharacterSet =>
        characterSets = CharacterSets(e.value.bytes)
      case e: ValueElement if e.tag == Tag.TimezoneOffsetFromUTC =>
        for {
          timeString <- e.value.toString(VR.SH, e.bigEndian, characterSets)
          zoneOffset <- parseZoneOffset(timeString)
        } yield this.zoneOffset = zoneOffset
      case _ =>
    }
    data += elementSet
    this
  }

  def build(): Elements = new Elements(characterSets, zoneOffset, data.toVector)
}
