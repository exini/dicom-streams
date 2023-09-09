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

import akka.util.ByteString
import com.exini.dicom.data.DicomParts._

object DicomElements {

  /**
    * A complete DICOM element, e.g. a standard value element, a sequence start element, a sequence delimitation element
    * or a fragments start element.
    */
  trait Element {
    val bigEndian: Boolean
    def toBytes: ByteString
    def toParts: List[DicomPart]
  }

  /**
    * A DICOM tag and all its associated data: a standard value element, a (complete) sequence or a (complete) fragments
    */
  sealed trait ElementSet {
    val tag: Int
    val vr: VR
    val bigEndian: Boolean
    val explicitVR: Boolean
    def toBytes: ByteString
    def toElements: List[Element]
  }

  case object PreambleElement extends Element {
    override val bigEndian: Boolean       = false
    override def toBytes: ByteString      = ByteString.fromArray(new Array[Byte](128)) ++ ByteString("DICM")
    override def toString: String         = "PreambleElement(0, ..., 0, D, I, C, M)"
    override def toParts: List[DicomPart] = PreamblePart(toBytes) :: Nil
  }

  case class ValueElement(tag: Int, vr: VR, value: Value, bigEndian: Boolean, explicitVR: Boolean)
      extends Element
      with ElementSet {
    val length: Long                         = value.length.toLong
    def setValue(value: Value): ValueElement = copy(value = value.ensurePadding(vr))
    override def toBytes: ByteString         = toParts.map(_.bytes).reduce(_ ++ _)
    override def toParts: List[DicomPart] =
      if (length > 0)
        HeaderPart(tag, vr, length, isFileMetaInformation(tag), bigEndian, explicitVR) :: ValueChunk(
          bigEndian,
          value.bytes,
          last = true
        ) :: Nil
      else
        HeaderPart(tag, vr, length, isFileMetaInformation(tag), bigEndian, explicitVR) :: Nil
    override def toElements: List[Element] = this :: Nil
    override def toString: String = {
      val strings = value.toStrings(vr, bigEndian, defaultCharacterSet)
      val s       = strings.mkString(multiValueDelimiter)
      val vm      = strings.length.toString
      s"ValueElement(${tagToString(tag)} $vr [$s] # $length, $vm ${Lookup.keywordOf(tag).getOrElse("")})"
    }
  }

  object ValueElement {
    def apply(tag: Int, value: Value, bigEndian: Boolean = false, explicitVR: Boolean = true): ValueElement =
      ValueElement(tag, Lookup.vrOf(tag), value, bigEndian, explicitVR)
    def fromBytes(tag: Int, bytes: ByteString, bigEndian: Boolean = false, explicitVR: Boolean = true): ValueElement =
      apply(tag, Value(bytes), bigEndian, explicitVR)
    def fromString(tag: Int, string: String, bigEndian: Boolean = false, explicitVR: Boolean = true): ValueElement =
      apply(tag, Value.fromString(Lookup.vrOf(tag), string, bigEndian), bigEndian, explicitVR)
    def empty(tag: Int, vr: VR, bigEndian: Boolean = false, explicitVR: Boolean = true): ValueElement =
      ValueElement(tag, vr, Value.empty, bigEndian, explicitVR)
  }

  case class SequenceElement(tag: Int, length: Long, bigEndian: Boolean = false, explicitVR: Boolean = true)
      extends Element {
    def indeterminate: Boolean            = length == indeterminateLength
    override def toBytes: ByteString      = HeaderPart(tag, VR.SQ, length, isFmi = false, bigEndian, explicitVR).bytes
    override def toParts: List[DicomPart] = SequencePart(tag, length, bigEndian, explicitVR, toBytes) :: Nil
    override def toString: String =
      s"SequenceElement(${tagToString(tag)} SQ # $length ${Lookup.keywordOf(tag).getOrElse("")})"
  }

  case class FragmentsElement(tag: Int, vr: VR, bigEndian: Boolean = false, explicitVR: Boolean = true)
      extends Element {
    override def toBytes: ByteString = toParts.head.bytes
    override def toParts: List[DicomPart] =
      FragmentsPart(
        tag,
        indeterminateLength,
        vr,
        bigEndian,
        explicitVR,
        HeaderPart(this.tag, this.vr, indeterminateLength, isFmi = false, this.bigEndian, this.explicitVR).bytes
      ) :: Nil
    override def toString: String =
      s"FragmentsElement(${tagToString(tag)} $vr # ${Lookup.keywordOf(tag).getOrElse("")})"
  }

  case class FragmentElement(length: Long, value: Value, bigEndian: Boolean = false) extends Element {
    override def toBytes: ByteString = toParts.map(_.bytes).reduce(_ ++ _)
    override def toParts: List[DicomPart] =
      if (value.length > 0)
        ItemElement(value.length.toLong, bigEndian).toParts ::: ValueChunk(bigEndian, value.bytes, last = true) :: Nil
      else
        ItemElement(value.length.toLong, bigEndian).toParts ::: Nil
    override def toString: String = s"FragmentElement(length = $length)"
  }

  object FragmentElement {
    def empty(length: Long, bigEndian: Boolean = false): FragmentElement =
      FragmentElement(length, Value.empty, bigEndian)
  }

  case class ItemElement(length: Long, bigEndian: Boolean = false) extends Element {
    def indeterminate: Boolean            = length == indeterminateLength
    override def toBytes: ByteString      = tagToBytes(Tag.Item, bigEndian) ++ intToBytes(length.toInt, bigEndian)
    override def toParts: List[DicomPart] = ItemPart(length, bigEndian, toBytes) :: Nil
    override def toString: String         = s"ItemElement(length = $length)"
  }

  case class ItemDelimitationElement(bigEndian: Boolean = false) extends Element {
    override def toBytes: ByteString      = tagToBytes(Tag.ItemDelimitationItem, bigEndian) ++ ByteString(0, 0, 0, 0)
    override def toParts: List[DicomPart] = ItemDelimitationPart(bigEndian, toBytes) :: Nil
    override def toString: String         = s"ItemDelimitationElement"
  }

  case class SequenceDelimitationElement(bigEndian: Boolean = false) extends Element {
    override def toBytes: ByteString      = tagToBytes(Tag.SequenceDelimitationItem, bigEndian) ++ ByteString(0, 0, 0, 0)
    override def toParts: List[DicomPart] = SequenceDelimitationPart(bigEndian, toBytes) :: Nil
    override def toString: String         = s"SequenceDelimitationElement"
  }

  case class Sequence(tag: Int, length: Long, items: List[Item], bigEndian: Boolean = false, explicitVR: Boolean = true)
      extends ElementSet {
    val vr: VR                 = VR.SQ
    val indeterminate: Boolean = length == indeterminateLength
    def item(index: Int): Option[Item] =
      try Option(items(index - 1))
      catch {
        case _: Throwable => None
      }
    def +(item: Item): Sequence =
      if (indeterminate)
        copy(items = items :+ item)
      else
        copy(length = length + item.toBytes.length, items = items :+ item)
    def removeItem(index: Int): Sequence =
      if (indeterminate)
        copy(items = items.patch(index - 1, Nil, 1))
      else
        copy(length = length - item(index).map(_.toBytes.length).getOrElse(0), items = items.patch(index - 1, Nil, 1))
    override def toBytes: ByteString = toElements.map(_.toBytes).reduce(_ ++ _)
    override def toElements: List[Element] =
      SequenceElement(tag, length, bigEndian, explicitVR) ::
        items.flatMap(_.toElements) ::: (if (indeterminate) SequenceDelimitationElement(bigEndian) :: Nil else Nil)
    def size: Int                                 = items.length
    def setItem(index: Int, item: Item): Sequence = copy(items = items.updated(index - 1, item))
    override def toString: String =
      s"Sequence(${tagToString(tag)} SQ # $length ${items.length} ${Lookup.keywordOf(tag).getOrElse("")})"
  }

  object Sequence {
    def empty(
        tag: Int,
        length: Long = indeterminateLength,
        bigEndian: Boolean = false,
        explicitVR: Boolean = true
    ): Sequence = Sequence(tag, length, Nil, bigEndian, explicitVR)
    def empty(element: SequenceElement): Sequence =
      empty(element.tag, element.length, element.bigEndian, element.explicitVR)
    def fromItems(
        tag: Int,
        items: List[Item],
        length: Long = indeterminateLength,
        bigEndian: Boolean = false,
        explicitVR: Boolean = true
    ): Sequence =
      Sequence(tag, length, items, bigEndian, explicitVR)
    def fromElements(
        tag: Int,
        elements: List[Elements],
        bigEndian: Boolean = false,
        explicitVR: Boolean = true
    ): Sequence =
      Sequence(
        tag,
        indeterminateLength,
        elements.map(Item.fromElements(_, indeterminateLength, bigEndian)),
        bigEndian,
        explicitVR
      )
  }

  case class Item(elements: Elements, length: Long = indeterminateLength, bigEndian: Boolean = false) {
    val indeterminate: Boolean = length == indeterminateLength
    def toElements: List[Element] =
      ItemElement(length, bigEndian) :: elements.toElements(false) :::
        (if (indeterminate) ItemDelimitationElement(bigEndian) :: Nil else Nil)
    def toBytes: ByteString = toElements.map(_.toBytes).reduce(_ ++ _)
    def setElements(elements: Elements): Item = {
      val newLength = if (this.indeterminate) indeterminateLength else elements.toBytes(withPreamble = false).length
      copy(elements = elements, length = newLength.toLong)
    }
    override def toString: String = s"Item(length = $length, elements size = ${elements.size})"
  }

  object Item {
    def empty(length: Long = indeterminateLength, bigEndian: Boolean = false): Item =
      Item(Elements.empty(), length, bigEndian)
    def empty(element: ItemElement): Item = empty(element.length, element.bigEndian)
    def fromElements(elements: Elements, length: Long = indeterminateLength, bigEndian: Boolean = false): Item =
      Item(elements, length, bigEndian)
  }

  case class Fragment(length: Long, value: Value, bigEndian: Boolean = false) {
    def toElement: FragmentElement = FragmentElement(length, value, bigEndian)
    override def toString: String  = s"Fragment(length = $length, value length = ${value.length})"
  }

  object Fragment {
    def fromElement(fragmentElement: FragmentElement): Fragment =
      Fragment(fragmentElement.length, fragmentElement.value, fragmentElement.bigEndian)
  }

  /**
    * Encapsulated (pixel) data holder
    *
    * @param tag        tag
    * @param vr         vr
    * @param offsets    list of frame offsets. No list (None) means fragments is empty and contains no items. An empty
    *                   list means offsets item is present but empty. Subsequent items hold frame data
    * @param fragments  frame data. Note that frames may span several fragments and fragments may contain more than one
    *                   frame
    * @param bigEndian  `true` if big endian encoded (should be false)
    * @param explicitVR `true` if explicit VR is used (should be true)
    */
  case class Fragments(
      tag: Int,
      vr: VR,
      offsets: Option[List[Long]],
      fragments: List[Fragment],
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ) extends ElementSet {
    def fragment(index: Int): Option[Fragment] =
      try Option(fragments(index - 1))
      catch {
        case _: Throwable => None
      }

    /**
      * @return the number of frames encapsulated in this `Fragments`
      */
    def frameCount: Int = if (offsets.isEmpty && fragments.isEmpty) 0 else if (offsets.isEmpty) 1 else offsets.size

    /**
      * @return an `Iterator[ByteString]` over the frames encoded in this `Fragments`
      */
    def frameIterator: Iterator[ByteString] =
      new Iterator[ByteString] {
        val totalLength: Long = fragments.map(_.length).sum
        val frameOffsets: List[Long] =
          if (totalLength <= 0) List(0L) else offsets.filter(_.nonEmpty).getOrElse(List(0L)) :+ totalLength
        val fragmentIterator: Iterator[Fragment] = fragments.iterator
        var offsetIndex: Int                     = 0
        var bytes: ByteString                    = ByteString.empty

        override def hasNext: Boolean = offsetIndex < frameOffsets.length - 1
        override def next(): ByteString = {
          val frameLength = (frameOffsets(offsetIndex + 1) - frameOffsets(offsetIndex)).toInt
          while (fragmentIterator.hasNext && bytes.length < frameLength)
            bytes = bytes ++ fragmentIterator.next().value.bytes
          val (frame, rest) = bytes.splitAt(frameLength)
          bytes = rest
          offsetIndex += 1
          frame
        }
      }

    def +(fragment: Fragment): Fragments =
      if (fragments.isEmpty && offsets.isEmpty)
        copy(offsets =
          Option(
            fragment.value.bytes
              .grouped(4)
              .map(bytes => intToUnsignedLong(bytesToInt(bytes, fragment.bigEndian)))
              .toList
          )
        )
      else
        copy(fragments = fragments :+ fragment)
    def toBytes: ByteString = toElements.map(_.toBytes).reduce(_ ++ _)
    def size: Int           = fragments.length
    override def toElements: List[Element] =
      FragmentsElement(tag, vr, bigEndian, explicitVR) ::
        offsets
          .map(o =>
            FragmentElement(
              4L * o.length,
              Value(
                o.map(offset => truncate(4, longToBytes(offset, bigEndian), bigEndian))
                  .foldLeft(ByteString.empty)(_ ++ _)
              ),
              bigEndian
            ) :: Nil
          )
          .getOrElse(Nil) ::: fragments.map(_.toElement) ::: SequenceDelimitationElement(bigEndian) :: Nil
    def setFragment(index: Int, fragment: Fragment): Fragments =
      copy(fragments = fragments.updated(index - 1, fragment))
    override def toString: String =
      s"Fragments(${tagToString(tag)} $vr # ${fragments.length} ${Lookup.keywordOf(tag).getOrElse("")})"
  }

  object Fragments {
    def empty(tag: Int, vr: VR, bigEndian: Boolean = false, explicitVR: Boolean = true): Fragments =
      Fragments(tag, vr, None, Nil, bigEndian, explicitVR)
    def empty(element: FragmentsElement): Fragments =
      empty(element.tag, element.vr, element.bigEndian, element.explicitVR)
  }

}
