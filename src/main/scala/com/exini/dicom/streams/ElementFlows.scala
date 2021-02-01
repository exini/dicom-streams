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
import akka.stream.Attributes
import akka.stream.scaladsl.Flow
import akka.stream.stage.GraphStageLogic
import akka.util.ByteString
import com.exini.dicom.data.DicomElements._
import com.exini.dicom.data.DicomParts._
import com.exini.dicom.data.TagPath._
import com.exini.dicom.data.{ TagPath, Value }

object ElementFlows {

  /**
    * @return a `Flow` that aggregates `DicomPart`s into data elements. Each element holds header and complete value
    *         information.
    */
  def elementFlow: Flow[DicomPart, Element, NotUsed] =
    partFlow
      .via(new DeferToPartFlow[Element] with GuaranteedValueEvent[Element] {

        override def createLogic(attr: Attributes): GraphStageLogic =
          new DeferToPartLogic with GuaranteedValueEventLogic {
            var bytes: ByteString                        = ByteString.empty
            var currentValue: Option[ValueElement]       = None
            var currentFragment: Option[FragmentElement] = None

            override def onPart(part: DicomPart): List[Element] =
              part match {

                case _: PreamblePart => PreambleElement :: Nil

                // Begin aggregate values
                case header: HeaderPart =>
                  currentValue = Option(ValueElement.empty(header.tag, header.vr, header.bigEndian, header.explicitVR))
                  bytes = ByteString.empty
                  Nil
                case item: ItemPart if inFragments =>
                  currentFragment = Option(FragmentElement.empty(item.length, item.bigEndian))
                  bytes = ByteString.empty
                  Nil

                // aggregate, emit if at end
                case valueChunk: ValueChunk =>
                  bytes = bytes ++ valueChunk.bytes
                  if (valueChunk.last)
                    if (inFragments)
                      currentFragment.map(_.copy(value = Value(bytes)) :: Nil).getOrElse(Nil)
                    else
                      currentValue.map(_.copy(value = Value(bytes)) :: Nil).getOrElse(Nil)
                  else
                    Nil

                // types that directly map to elements
                case sequence: SequencePart =>
                  SequenceElement(sequence.tag, sequence.length, sequence.bigEndian, sequence.explicitVR) :: Nil
                case fragments: FragmentsPart =>
                  FragmentsElement(fragments.tag, fragments.vr, fragments.bigEndian, fragments.explicitVR) :: Nil
                case item: ItemPart =>
                  ItemElement(item.length, item.bigEndian) :: Nil
                case itemDelimitation: ItemDelimitationPart =>
                  ItemDelimitationElement(itemDelimitation.bigEndian) :: Nil
                case sequenceDelimitation: SequenceDelimitationPart =>
                  SequenceDelimitationElement(sequenceDelimitation.bigEndian) :: Nil

                case _ => Nil
              }
          }
      })

  def tagPathFlow: Flow[Element, (TagPath, Element), NotUsed] =
    Flow[Element]
      .statefulMapConcat {
        var tagPath: TagPath = EmptyTagPath
        var inFragments      = false

        () => {
          case e: ValueElement =>
            tagPath = tagPath match {
              case t: TagPathItem => t.thenTag(e.tag)
              case t              => t.previous.thenTag(e.tag)
            }
            (tagPath, e) :: Nil
          case e: FragmentsElement =>
            tagPath = tagPath match {
              case t: TagPathItem => t.thenTag(e.tag)
              case t              => t.previous.thenTag(e.tag)
            }
            inFragments = true
            (tagPath, e) :: Nil
          case e: SequenceElement =>
            tagPath = tagPath match {
              case t: TagPathItem => t.thenSequence(e.tag)
              case t              => t.previous.thenSequence(e.tag)
            }
            (tagPath, e) :: Nil
          case e: SequenceDelimitationElement =>
            if (!inFragments) tagPath = tagPath.previous.thenSequenceEnd(tagPath.tag)
            inFragments = false
            (tagPath, e) :: Nil
          case e: ItemElement =>
            if (!inFragments)
              tagPath = tagPath match {
                case t: TagPathItemEnd =>
                  t.previous.thenItem(t.tag, t.item + 1)
                case t => t.previous.thenItem(t.tag, 1)
              }
            (tagPath, e) :: Nil
          case e: ItemDelimitationElement =>
            tagPath = tagPath match {
              case t: TagPathItem => t.previous.thenItemEnd(t.tag, t.item)
              case t =>
                t.previous match {
                  case ti: TagPathItem => ti.previous.thenItemEnd(ti.tag, ti.item)
                  case _               => tagPath // should never get delimitation when not inside item
                }
            }
            (tagPath, e) :: Nil
          case e =>
            (tagPath, e) :: Nil
        }
      }
}
