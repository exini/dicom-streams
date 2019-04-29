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

import akka.util.ByteString
import com.exini.dicom.data._
import com.exini.dicom.data.DicomParts._
import com.exini.dicom.data.TagPath.{EmptyTagPath, TagPathTag}
import com.exini.dicom.data.{Lookup, TagPath, VR, isFileMetaInformation}

object ModifyFlow {

  /**
    * Class used to specify modifications to individual elements of a dataset
    *
    * @param matches      function used to determine if another tag path is matching the tag path of this modification
    *                     (may mean e.g. equals, starts with or ends with)
    * @param modification a modification function
    */
  case class TagModification(matches: TagPath => Boolean, modification: ByteString => ByteString)

  object TagModification {

    /**
      * Modification that will modify dataset elements matching its tag path.
      */
    def equals(tagPath: TagPathTag, modification: ByteString => ByteString) = TagModification(tagPath.equals, modification)

    /**
      * Modification that will modify dataset elements where the corresponding tag path ends with the tag path of this
      * modification. E.g. both the dataset elements (0010,0010) and (0008,9215)[1].(0010,0010) will be modified if
      * this tag path is (0010,0010). Useful for changing all instances of a certain element.
      */
    def endsWith(tagPath: TagPathTag, modification: ByteString => ByteString) = TagModification(_.endsWith(tagPath), modification)
  }

  /**
    * Class used to specify insertions (or replacements) of elements into a dataset
    *
    * @param tagPath   tag path pointing to position to insert or replace data
    * @param insertion insertion data as a function of existing data, if present
    */
  case class TagInsertion(tagPath: TagPathTag, insertion: Option[ByteString] => ByteString)

  /**
    * Meta-part carrying tag modifications and element insertions that when received by modify flow are picked up and
    * added to or replaces the input tag modifications
    *
    * @param modifications additional tag modifications
    * @param insertions    additional element insertions
    * @param replace       `true` if these modifications/insertions should replace the current ones
    */
  case class TagModificationsPart(modifications: Seq[TagModification] = Seq.empty, insertions: Seq[TagInsertion] = Seq.empty, replace: Boolean = false) extends MetaPart

  /**
    * Modification flow for inserting or overwriting the values of specified elements. When inserting a new element,
    * the corresponding modification function will be called with an empty `ByteString`. A modification is specified by
    * a tag path, a modification function from current value bytes to their replacement, and a flag specifying whether
    * the element should be inserted if not present. Elements are inserted only if they point to existing datasets.
    * That means that sequences are never inserted, only modified. Insertion works according to the `TagPathTag#contains`
    * method, meaning that if sequence wildcards are used in modifications they will apply to all items in a sequence.
    *
    * Note that modified DICOM data may not be valid. This function does not ensure values are padded to even length and
    * changing an element may lead to invalid group length elements such as MediaStorageSOPInstanceUID. There are
    * utility functions in `com.exini.dicom.data` for padding values and flows for adjusting group lengths.
    *
    * Modifications can either be supplied as input arguments or passed at any time on the stream encapsulated in a
    * [[com.exini.dicom.streams.ModifyFlow.TagModificationsPart]]
    *
    * @param modifications          a sequence of tag modifications each with its on trigger function on current tag path, and
    *                               modification function for element value when triggered
    * @param insertions             a sequence of tag insertion specifications each with a tag path pointer to the element to
    *                               be replaced or inserted, and the new value
    * @param logGroupLengthWarnings determines whether to log a warning when group length tags, or sequences or items
    *                               with determinate length are encountered
    * @return the modified flow of DICOM parts
    */
  def modifyFlow(modifications: Seq[TagModification] = Seq.empty, insertions: Seq[TagInsertion] = Seq.empty, logGroupLengthWarnings: Boolean = true): PartFlow =
    DicomFlowFactory.create(new DeferToPartFlow[DicomPart] with EndEvent[DicomPart] with TagPathTracking[DicomPart] with GroupLengthWarnings[DicomPart] {
      silent = !logGroupLengthWarnings

      var currentModifications: List[TagModification] = Nil
      var currentInsertions: List[TagInsertion] = Nil
      set(modifications, insertions)

      var currentModification: Option[TagModification] = None // current modification
      var currentHeader: Option[HeaderPart] = None // header of current element being modified
      var latestTagPath: TagPath = EmptyTagPath // last seen new tag path
      var value: ByteString = ByteString.empty // value of current element being modified
      var bigEndian = false // endianness of current element
      var explicitVR = true // VR representation of current element

      def set(m: Seq[TagModification], i: Seq[TagInsertion]): Unit = {
        currentModifications = m.toList
        currentInsertions = i
          .groupBy(_.tagPath).flatMap(_._2.headOption).toList // distinct on tag path
          .sortWith((a, b) => a.tagPath < b.tagPath) // sorted by tag path
      }

      def updateSyntax(header: HeaderPart): Unit = {
        bigEndian = header.bigEndian
        explicitVR = header.explicitVR
      }

      def valueOrNot(bytes: ByteString): List[DicomPart] =
        if (bytes.isEmpty) Nil else ValueChunk(bigEndian, bytes, last = true) :: Nil

      def headerAndValueParts(tagPath: TagPath, valueBytes: ByteString): List[DicomPart] = {
        val vr = Lookup.vrOf(tagPath.tag)
        if (vr == VR.UN) throw new IllegalArgumentException("Tag is not present in dictionary, cannot determine value representation")
        if (vr == VR.SQ) throw new IllegalArgumentException("Cannot insert sequences")
        val isFmi = isFileMetaInformation(tagPath.tag)
        val header = HeaderPart(tagPath.tag, vr, valueBytes.length, isFmi, bigEndian, explicitVR)
        header :: valueOrNot(valueBytes)
      }

      def isBetween(lowerTag: TagPath, tagToTest: TagPath, upperTag: TagPath): Boolean =
        lowerTag < tagToTest && tagToTest < upperTag

      def isInDataset(tagToTest: TagPath, tagPath: TagPath): Boolean =
        tagToTest.previous == tagPath.previous

      def findInsertParts: List[DicomPart] = currentInsertions
        .filter(i => isBetween(latestTagPath, i.tagPath, tagPath))
        .filter(i => isInDataset(i.tagPath, tagPath))
        .flatMap(i => headerAndValueParts(i.tagPath, padToEvenLength(i.insertion(None), i.tagPath.tag)))

      def findModifyPart(header: HeaderPart): List[DicomPart] = currentModifications
          .find(m => m.matches(tagPath))
        .map { tagModification =>
          currentHeader = Some(header)
          currentModification = Some(tagModification)
          value = ByteString.empty
          Nil
        }
        .orElse {
          currentInsertions
            .find(_.tagPath == tagPath)
            .map { insertion =>
              currentHeader = Some(header)
              currentModification = Some(TagModification(_ == insertion.tagPath, v => insertion.insertion(Some(v))))
              value = ByteString.empty
              Nil
            }
        }
        .getOrElse(header :: Nil)

      override def onPart(part: DicomPart): List[DicomPart] =
        part match {
          case modsPart: TagModificationsPart =>
            if (modsPart.replace)
              set(modsPart.modifications, modsPart.insertions)
            else
              set(currentModifications ++ modsPart.modifications, currentInsertions ++ modsPart.insertions)
            Nil

          case header: HeaderPart =>
            updateSyntax(header)
            val insertParts = findInsertParts
            val modifyPart = findModifyPart(header)
            latestTagPath = tagPath
            insertParts ::: modifyPart

          case sequence: SequencePart =>
            val insertParts = findInsertParts
            latestTagPath = tagPath
            insertParts ::: sequence :: Nil

          case chunk: ValueChunk =>
            if (currentModification.isDefined && currentHeader.isDefined) {
              value = value ++ chunk.bytes
              if (chunk.last) {
                val newValue = padToEvenLength(currentModification.get.modification(value), currentHeader.get.vr)
                val newHeader = currentHeader.get.withUpdatedLength(newValue.length)
                currentModification = None
                currentHeader = None
                newHeader :: valueOrNot(newValue)
              } else
                Nil
            } else
              chunk :: Nil

          case otherPart =>
            latestTagPath = tagPath
            otherPart :: Nil
        }

      override def onEnd(): List[DicomPart] = if (latestTagPath.isEmpty) Nil else
        currentInsertions
          .filter(_.tagPath.isRoot)
          .filter(m => latestTagPath < m.tagPath)
          .flatMap(m => headerAndValueParts(m.tagPath, padToEvenLength(m.insertion(None), m.tagPath.tag)))
    })

}
