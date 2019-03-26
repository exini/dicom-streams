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

import se.nimsa.dicom.data.DicomParts._
import se.nimsa.dicom.data.Elements.ValueElement
import se.nimsa.dicom.data.{Elements, _}

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
    * @param tagPaths      tag paths of data elements to collect. Collection (and hence buffering) will end when the
    *                      stream moves past the highest tag number
    * @param label         a tag for the resulting ElementsPart to separate this from other such elements in the same
    *                      flow
    * @param maxBufferSize the maximum allowed size of the buffer (to avoid running out of memory). The flow will fail
    *                      if this limit is exceed. Set to 0 for an unlimited buffer size
    * @return A DicomPart Flow which will begin with a ElementsPart part followed by other parts in the flow
    */
  def collectFlow(tagPaths: Set[_ <: TagPath], label: String, maxBufferSize: Int = 1000000): PartFlow = {
    val maxTag = if (tagPaths.isEmpty) 0 else tagPaths.map(_.head.tag).max
    val tagCondition = (tagPath: TagPath) => tagPaths.exists(tagPath.startsWith)
    val stopCondition = if (tagPaths.isEmpty)
      (_: TagPath) => true
    else
      (tagPath: TagPath) => tagPath.isRoot && tagPath.tag > maxTag
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
  def collectFlow(tagCondition: TagPath => Boolean, stopCondition: TagPath => Boolean, label: String, maxBufferSize: Int): PartFlow =
    DicomFlowFactory.create(new DeferToPartFlow[DicomPart] with TagPathTracking[DicomPart] with EndEvent[DicomPart] {

      var reachedEnd = false
      var currentBufferSize = 0
      var currentElement: Option[ValueElement] = None
      var buffer: List[DicomPart] = Nil
      var elements: Elements = Elements.empty()

      def elementsAndBuffer(): List[DicomPart] = {
        val parts = ElementsPart(label, elements) :: buffer

        reachedEnd = true
        buffer = Nil
        currentBufferSize = 0

        parts
      }

      override def onEnd(): List[DicomPart] =
        if (reachedEnd)
          Nil
        else
          elementsAndBuffer()

      override def onPart(part: DicomPart): List[DicomPart] = {
        if (reachedEnd)
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

              case header: HeaderPart if tagCondition(tagPath) || header.tag == Tag.SpecificCharacterSet =>
              currentElement = Some(ValueElement(header.tag, header.vr, Value.empty, header.bigEndian, header.explicitVR))
              Nil

            case _: HeaderPart =>
              currentElement = None
              Nil

            case valueChunk: ValueChunk =>

              currentElement match {
                case Some(element) =>
                  val updatedElement = element.copy(value = element.value ++ valueChunk.bytes)
                  currentElement = Some(updatedElement)
                  if (valueChunk.last) {
                    if (updatedElement.tag == Tag.SpecificCharacterSet)
                      elements = elements.setCharacterSets(CharacterSets(updatedElement.toBytes))
                    if (tagCondition(tagPath))
                      elements = elements.set(updatedElement)
                    currentElement = None
                  }
                  Nil

                case None => Nil
              }

            case _ => Nil
          }
        }
      }
    })


}
