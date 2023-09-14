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

import java.util

object DicomParts {

  /**
    * The most general representation of a DICOM data chunk
    */
  trait DicomPart {
    def bigEndian: Boolean
    def bytes: Array[Byte]
    override def equals(obj: Any): Boolean = obj match {
      case p: DicomPart => bigEndian == p.bigEndian && bytes.sameElements(p.bytes)
      case _ => false
    }
    override def hashCode(): Int = 31 * (31 * 7 + bigEndian.hashCode()) + util.Arrays.hashCode(bytes)
  }

  /**
    * A `DicomPart` with a tag number (e.g. `HeaderPart`, `SequencePart`, 'FragmentsPart')
    */
  trait TagPart extends DicomPart {
    def tag: Int
  }

  /**
    * A 'DicomPart' with a length attribute (e.g. `HeaderPart`, `SequencePart`, 'ItemPart')
    */
  trait LengthPart extends DicomPart {
    def length: Long
    def indeterminate: Boolean = length == indeterminateLength
  }

  /**
    * A 'DicomPart' with a VR type (e.g. `HeaderPart`, `SequencePart`, `FragmentsPart`)
    */
  trait VrPart extends DicomPart {
    def vr: VR
  }

  case class PreamblePart(bytes: Array[Byte]) extends DicomPart {
    def bigEndian         = false
    override def toString = s"${getClass.getSimpleName} (${bytes.length} bytes)"
  }

  case class HeaderPart(
      tag: Int,
      vr: VR,
      length: Long,
      isFmi: Boolean,
      bigEndian: Boolean,
      explicitVR: Boolean,
      bytes: Array[Byte]
  ) extends DicomPart
      with TagPart
      with VrPart
      with LengthPart {

    def withUpdatedLength(newLength: Long): HeaderPart =
      if (newLength == length)
        this
      else {
        val updated =
          if ((bytes.length >= 8) && explicitVR && (vr.headerLength == 8)) //explicit vr
            bytes.take(6) ++ shortToBytes(newLength.toShort, bigEndian)
          else if ((bytes.length >= 12) && explicitVR && (vr.headerLength == 12)) //explicit vr
            bytes.take(8) ++ intToBytes(newLength.toInt, bigEndian)
          else //implicit vr
            bytes.take(4) ++ intToBytes(newLength.toInt, bigEndian)

        HeaderPart(tag, vr, newLength, isFmi, bigEndian, explicitVR, updated)
      }

    override def toString =
      s"${getClass.getSimpleName} ${tagToString(tag)} ${if (isFmi) "(meta) " else ""}$vr ${if (!explicitVR) "(implicit) "
      else ""}length = ${bytes.length} value length = $length ${if (bigEndian) "(big endian) " else ""}${bytes.arrayString}"
  }

  object HeaderPart {
    def apply(
        tag: Int,
        vr: VR,
        length: Long,
        isFmi: Boolean = false,
        bigEndian: Boolean = false,
        explicitVR: Boolean = true
    ): HeaderPart = {
      val headerBytes =
        if (explicitVR)
          if (vr.headerLength == 8)
            tagToBytes(tag, bigEndian) ++ CharacterSets.encode(vr.toString) ++ shortToBytes(length.toShort, bigEndian)
          else
            tagToBytes(tag, bigEndian) ++ CharacterSets.encode(vr.toString) ++ bytes(0, 0) ++ intToBytes(
              length.toInt,
              bigEndian
            )
        else
          tagToBytes(tag, bigEndian) ++ intToBytes(length.toInt, bigEndian)
      HeaderPart(tag, vr, length, isFmi, bigEndian, explicitVR, headerBytes)
    }
  }

  case class ValueChunk(bigEndian: Boolean, bytes: Array[Byte], last: Boolean) extends DicomPart {
    override def toString =
      s"${getClass.getSimpleName} ${if (last) "(last) " else ""}length = ${bytes.length} ${if (bigEndian) "(big endian) "
      else ""}${if (bytes.length <= 64) "ASCII = " + bytes.utf8String else ""}"
  }

  case class DeflatedChunk(bigEndian: Boolean, bytes: Array[Byte], nowrap: Boolean) extends DicomPart

  case class ItemPart(length: Long, bigEndian: Boolean, bytes: Array[Byte]) extends LengthPart {
    override def toString =
      s"${getClass.getSimpleName} length = $length ${if (bigEndian) "(big endian) " else ""}${bytes.arrayString}"
  }

  case class ItemDelimitationPart(bigEndian: Boolean, bytes: Array[Byte]) extends DicomPart

  case class SequencePart(tag: Int, length: Long, bigEndian: Boolean, explicitVR: Boolean, bytes: Array[Byte])
      extends DicomPart
      with TagPart
      with VrPart
      with LengthPart {
    override val vr: VR = VR.SQ
    override def toString =
      s"${getClass.getSimpleName} ${tagToString(tag)} length = $length ${if (bigEndian) "(big endian) " else ""}${if (!explicitVR) "(implicit) "
      else ""}${bytes.arrayString}"
  }

  case class SequenceDelimitationPart(bigEndian: Boolean, bytes: Array[Byte]) extends DicomPart

  case class FragmentsPart(tag: Int, length: Long, vr: VR, bigEndian: Boolean, explicitVR: Boolean, bytes: Array[Byte])
      extends DicomPart
      with TagPart
      with VrPart
      with LengthPart {
    override def toString =
      s"${getClass.getSimpleName} ${tagToString(tag)} $vr ${if (bigEndian) "(big endian) " else ""}${bytes.arrayString}"
  }

  case class UnknownPart(bigEndian: Boolean, bytes: Array[Byte]) extends DicomPart

  trait MetaPart extends DicomPart {
    def bigEndian: Boolean = false
    def bytes: Array[Byte] = Array.emptyByteArray
  }

  trait Singleton {
    override def equals(obj: Any): Boolean = obj match {
      case o: AnyRef if o.eq(this) => true
      case _ => false
    }
  }

  /**
    * Meta-part that encapsulates a dataset
    *
    * @param label    a string label used to keep datasets apart when more than one are present in the same data
    * @param elements a dataset
    */
  case class ElementsPart(label: String, elements: Elements) extends MetaPart

  /**
    * Meta-part that can be used to mark the start of a DICOM file, e.g. using `.prepend(Source.single(DicomStartMarker)`
    */
  case object DicomStartMarker extends MetaPart with Singleton

  /**
    * Meta-part that can be used to mark the end of a DICOM file, e.g. using `.concat(Source.single(DicomEndMarker)`
    */
  case object DicomEndMarker extends MetaPart with Singleton

}
