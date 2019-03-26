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

import akka.stream.scaladsl.{Flow, Keep, Sink}
import se.nimsa.dicom.data.Elements
import se.nimsa.dicom.data.Elements._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Flow, Sink etc that combine DICOM parts into data element aggregates.
  */
object ElementSink {

  /**
    * Data holder for `elementsSink`
    */
  private case class ElementSinkData(builderStack: Seq[ElementsBuilder] = Seq(Elements.newBuilder()),
                                      sequenceStack: Seq[Sequence] = Seq.empty,
                                      fragments: Option[Fragments] = None) {
    def updated(sequence: Sequence): ElementSinkData = copy(sequenceStack = sequence +: sequenceStack.tail)
    def updated(fragments: Option[Fragments]): ElementSinkData = copy(fragments = fragments)
    def pushBuilder(builder: ElementsBuilder): ElementSinkData = copy(builderStack = builder +: builderStack)
    def pushSequence(sequence: Sequence): ElementSinkData = copy(sequenceStack = sequence +: sequenceStack)
    def popBuilder(): ElementSinkData = copy(builderStack = builderStack.tail)
    def popSequence(): ElementSinkData = copy(sequenceStack = sequenceStack.tail)
    def hasSequence: Boolean = sequenceStack.nonEmpty
    def hasFragments: Boolean = fragments.nonEmpty
  }

  /**
    * A `Sink` that combines data elements into an `Elements` structure. If the `SpecificCharacterSet` element occurs,
    * the character sets of the `Elements` structure is updated accordingly. If the `TimezoneOffsetFromUTC` element
    * occurs, the zone offset is updated accordingly.
    */
  def elementSink(implicit ec: ExecutionContext): Sink[Element, Future[Elements]] = Flow[Element]
    .toMat(
      Sink.fold[ElementSinkData, Element](ElementSinkData()) { case (sinkData, element) =>
        element match {

          case valueElement: ValueElement =>
            val builder = sinkData.builderStack.head
            builder += valueElement
            sinkData

          case fragments: FragmentsElement =>
            sinkData.updated(Some(Fragments.empty(fragments)))

          case fragmentElement: FragmentElement =>
            val updatedFragments = sinkData.fragments.map(_ + Fragment.fromElement(fragmentElement))
            sinkData.updated(updatedFragments)

          case _: SequenceDelimitationElement if sinkData.hasFragments =>
            val fragments = sinkData.fragments.get
            val builder = sinkData.builderStack.head
            builder += fragments
            sinkData.updated(None)

          case sequenceElement: SequenceElement =>
            sinkData.pushSequence(Sequence.empty(sequenceElement))

          case itemElement: ItemElement if sinkData.hasSequence =>
            val builder = sinkData.builderStack.head
            val sequence = sinkData.sequenceStack.head + Item.empty(itemElement)
            sinkData.pushBuilder(Elements.newBuilder(builder.characterSets, builder.zoneOffset)).updated(sequence)

          case _: ItemDelimitationElement if sinkData.hasSequence =>
            val elements = sinkData.builderStack.head.result()
            val sequence = sinkData.sequenceStack.head
            val updatedSequence = sequence.items.lastOption
              .map(item => sequence.copy(items = sequence.items.init :+ item.setElements(elements)))
              .getOrElse(sequence)
            sinkData.popBuilder().updated(updatedSequence)

          case _: SequenceDelimitationElement if sinkData.hasSequence =>
            val sequence = sinkData.sequenceStack.head
            val builder = sinkData.builderStack.head
            builder += sequence
            sinkData.popSequence()

          case _ =>
            sinkData
        }
      }
        .mapMaterializedValue(_.map(_.builderStack.headOption.map(_.result()).getOrElse(Elements.empty())))
    )(Keep.right)
}
