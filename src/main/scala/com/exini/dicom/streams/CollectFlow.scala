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

import akka.stream.Attributes
import akka.stream.scaladsl.Flow
import akka.stream.stage.GraphStageLogic
import akka.util.ByteString
import com.exini.dicom.data.DicomElements._
import com.exini.dicom.data.DicomParts._
import com.exini.dicom.data.{Elements, _}

object CollectFlow {

  /**
    * Collect the data elements specified by the input set of tags while buffering all elements of the stream. When the
    * stream has moved past the last element to collect, a ElementsPart element is emitted containing a list of
    * CollectedElement-parts with the collected information, followed by all buffered elements. Remaining elements in the
    * stream are immediately emitted downstream without buffering.
    *
    * This flow is used when there is a need to "look ahead" for certain information in the stream so that streamed
    * elements can be processed correctly according to this information. As an example, an implementation may have
    * different graph paths for different modalities and the modality must be known before any elements are processed.
    *
    * @param whitelist     tag paths of data elements to collect. Collection (and hence buffering) will end when the
    *                      stream moves past the highest tag number
    * @param label         a tag for the resulting ElementsPart to separate this from other such elements in the same
    *                      flow
    * @param maxBufferSize the maximum allowed size of the buffer (to avoid running out of memory). The flow will fail
    *                      if this limit is exceed. Set to 0 for an unlimited buffer size
    * @return A DicomPart Flow which will begin with a ElementsPart part followed by other parts in the flow
    */
  def collectFlow(whitelist: Set[_ <: TagTree], label: String, maxBufferSize: Int = 1000000): PartFlow = {
    val maxTag = if (whitelist.isEmpty) 0 else whitelist.map(_.head.tag).map(intToUnsignedLong).max
    val tagCondition = (currentPath: TagPath) =>
      whitelist.exists(t => t.hasTrunk(currentPath) || t.isTrunkOf(currentPath))
    val stopCondition = (tagPath: TagPath) =>
      whitelist.isEmpty || tagPath.isRoot && intToUnsignedLong(tagPath.tag) > maxTag
    collectFlow(tagCondition, stopCondition, label, maxBufferSize)
  }

  /**
    * Collect data elements whenever the input tag condition yields `true` while buffering all elements of the stream. When
    * the stop condition yields `true`, a ElementsPart is emitted containing a list of
    * CollectedElement objects with the collected information, followed by all buffered parts. Remaining elements in the
    * stream are immediately emitted downstream without buffering.
    *
    * This flow is used when there is a need to "look ahead" for certain information in the stream so that streamed
    * elements can be processed correctly according to this information. As an example, an implementation may have
    * different graph paths for different modalities and the modality must be known before any elements are processed.
    *
    * @param tagCondition  function determining the condition(s) for which elements are collected
    * @param stopCondition function determining the condition for when collection should stop and elements are emitted
    * @param label         a label for the resulting ElementsPart to separate this from other such elements in the
    *                      same flow
    * @param maxBufferSize the maximum allowed size of the buffer (to avoid running out of memory). The flow will fail
    *                      if this limit is exceed. Set to 0 for an unlimited buffer size
    * @return A DicomPart Flow which will begin with a ElementsPart followed by the input parts
    */
  def collectFlow(
      tagCondition: TagPath => Boolean,
      stopCondition: TagPath => Boolean,
      label: String,
      maxBufferSize: Int
  ): PartFlow =
    Flow[DicomPart]
      .via(new DeferToPartFlow[DicomPart] with TagPathTracking[DicomPart] with EndEvent[DicomPart] {

        override def createLogic(attr: Attributes): GraphStageLogic = new DeferToPartLogic with TagPathTrackingLogic with EndEventLogic {
          var buffer: List[DicomPart] = Nil
          var currentBufferSize = 0
          var hasEmitted = false
          var bytes: ByteString = ByteString.empty
          var currentValue: Option[ValueElement] = None
          var currentFragment: Option[FragmentElement] = None

          val builder: ElementsBuilder = Elements.newBuilder()

          def elementsAndBuffer(): List[DicomPart] = {
            val parts = ElementsPart(label, builder.build()) :: buffer

            hasEmitted = true
            buffer = Nil
            currentBufferSize = 0

            parts
          }

          def maybeAdd(element: Element): ElementsBuilder =
            if (tagCondition(tagPath))
              builder += element
            else
              builder !! element

          override def onEnd(): List[DicomPart] =
            if (hasEmitted)
              Nil
            else
              elementsAndBuffer()

          override def onPart(part: DicomPart): List[DicomPart] =
            if (hasEmitted)
              part :: Nil
            else {
              if (maxBufferSize > 0 && currentBufferSize > maxBufferSize)
                throw new DicomStreamException("Error collecting elements: max buffer size exceeded")

              part match {
                case ValueChunkMarker =>
                case SequenceDelimitationPartMarker =>
                case _: ItemDelimitationPartMarker =>
                case _ =>
                  buffer = buffer :+ part
                  currentBufferSize = currentBufferSize + part.bytes.size
              }

              part match {
                case _: TagPart if stopCondition(tagPath) =>
                  elementsAndBuffer()

                case header: HeaderPart =>
                  currentValue = Option(ValueElement.empty(header.tag, header.vr, header.bigEndian, header.explicitVR))
                  bytes = ByteString.empty
                  Nil

                case item: ItemPart if inFragments =>
                  currentFragment = Option(FragmentElement.empty(item.index, item.length, item.bigEndian))
                  bytes = ByteString.empty
                  Nil

                case valueChunk: ValueChunk =>
                  bytes = bytes ++ valueChunk.bytes
                  if (valueChunk.last) {
                    if (inFragments)
                      currentFragment.map(_.copy(value = Value(bytes))).foreach(maybeAdd)
                    else
                      currentValue.map(_.copy(value = Value(bytes))).foreach(maybeAdd)
                    currentFragment = None
                    currentValue = None
                  }
                  Nil

                case sequence: SequencePart =>
                  maybeAdd(SequenceElement(sequence.tag, sequence.length, sequence.bigEndian, sequence.explicitVR))
                  Nil
                case fragments: FragmentsPart =>
                  maybeAdd(FragmentsElement(fragments.tag, fragments.vr, fragments.bigEndian, fragments.explicitVR))
                  Nil
                case item: ItemPart =>
                  maybeAdd(ItemElement(item.index, item.length, item.bigEndian))
                  Nil
                case _: ItemDelimitationPartMarker =>
                  Nil
                case itemDelimitation: ItemDelimitationPart =>
                  maybeAdd(ItemDelimitationElement(itemDelimitation.index, itemDelimitation.bigEndian))
                  Nil
                case SequenceDelimitationPartMarker =>
                  Nil
                case sequenceDelimitation: SequenceDelimitationPart =>
                  maybeAdd(SequenceDelimitationElement(sequenceDelimitation.bigEndian))
                  Nil
                case _ =>
                  Nil
              }
            }
        }
      })
}
