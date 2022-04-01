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

import java.math.BigInteger
import java.net.URI
import java.time.{ LocalDate, LocalTime, ZoneOffset, ZonedDateTime }

import akka.util.ByteString
import com.exini.dicom.data.DicomElements._
import com.exini.dicom.data.DicomParts._
import com.exini.dicom.data.Elements._
import com.exini.dicom.data.TagPath._

/**
  * Representation of a group of `ElementSet`s, a dataset. Representation is immutable so methods for inserting, updating
  * and removing elements return a new instance. For performant building use the associated builder. Also specifies the
  * character sets and time zone offset that should be used for decoding the values of certain elements.
  *
  * @param characterSets The character sets used for decoding text values
  * @param zoneOffset    The time zone offset used for date-time elements with no time zone specified
  * @param data          the data elements
  */
case class Elements(characterSets: CharacterSets, zoneOffset: ZoneOffset, data: Vector[ElementSet]) {

  /**
    * Get a single element set, if present
    *
    * @param tag tag number the element, referring to the root dataset
    * @return element set, if present
    */
  def apply(tag: Int): Option[ElementSet] = data.find(_.tag == tag)

  /**
    * Get a single element set by tag path, if present
    *
    * @param tagPath input tag path
    * @return element set, if present
    */
  def apply(tagPath: TagPathTag): Option[ElementSet] =
    tagPath.previous match {
      case tp: TagPathItem => getNested(tp).flatMap(_.apply(tagPath.tag))
      case EmptyTagPath    => apply(tagPath.tag)
      case _               => throw new IllegalArgumentException("Unsupported tag path type")
    }

  private def get[A](tag: Int, f: ValueElement => Option[A]): Option[A] =
    apply(tag).flatMap {
      case e: ValueElement => f(e)
      case _               => None
    }

  private def getPath[A](tagPath: TagPathTag, f: ValueElement => Option[A]): Option[A] =
    apply(tagPath).flatMap {
      case e: ValueElement => f(e)
      case _               => None
    }

  private def getAll[A](tag: Int, f: ValueElement => Seq[A]): Seq[A] =
    apply(tag)
      .map {
        case e: ValueElement => f(e)
        case _               => Seq.empty
      }
      .getOrElse(Seq.empty)

  private def getAllPath[A](tagPath: TagPathTag, f: ValueElement => Seq[A]): Seq[A] =
    apply(tagPath)
      .map {
        case e: ValueElement => f(e)
        case _               => Seq.empty
      }
      .getOrElse(Seq.empty)

  def getValueElement(tag: Int): Option[ValueElement]            = get(tag, Option.apply)
  def getValueElement(tagPath: TagPathTag): Option[ValueElement] = getPath(tagPath, Option.apply)
  def getValue(tag: Int): Option[Value]                          = getValueElement(tag).map(_.value)
  def getValue(tagPath: TagPathTag): Option[Value]               = getValueElement(tagPath).map(_.value)
  def getBytes(tag: Int): Option[ByteString]                     = getValue(tag).map(_.bytes)
  def getBytes(tagPath: TagPathTag): Option[ByteString]          = getValue(tagPath).map(_.bytes)
  def getStrings(tag: Int): Seq[String]                          = getAll(tag, v => v.value.toStrings(v.vr, v.bigEndian, characterSets))
  def getStrings(tagPath: TagPathTag): Seq[String] =
    getAllPath(tagPath, v => v.value.toStrings(v.vr, v.bigEndian, characterSets))
  def getSingleString(tag: Int): Option[String] =
    get(tag, v => v.value.toSingleString(v.vr, v.bigEndian, characterSets))
  def getSingleString(tagPath: TagPathTag): Option[String] =
    getPath(tagPath, v => v.value.toSingleString(v.vr, v.bigEndian, characterSets))
  def getString(tag: Int): Option[String] = get(tag, v => v.value.toString(v.vr, v.bigEndian, characterSets))
  def getString(tagPath: TagPathTag): Option[String] =
    getPath(tagPath, v => v.value.toString(v.vr, v.bigEndian, characterSets))
  def getShorts(tag: Int): Seq[Short]              = getAll(tag, v => v.value.toShorts(v.vr, v.bigEndian))
  def getShorts(tagPath: TagPathTag): Seq[Short]   = getAllPath(tagPath, v => v.value.toShorts(v.vr, v.bigEndian))
  def getShort(tag: Int): Option[Short]            = get(tag, v => v.value.toShort(v.vr, v.bigEndian))
  def getShort(tagPath: TagPathTag): Option[Short] = getPath(tagPath, v => v.value.toShort(v.vr, v.bigEndian))
  def getInts(tag: Int): Seq[Int]                  = getAll(tag, v => v.value.toInts(v.vr, v.bigEndian))
  def getInts(tagPath: TagPathTag): Seq[Int]       = getAllPath(tagPath, v => v.value.toInts(v.vr, v.bigEndian))
  def getInt(tag: Int): Option[Int]                = get(tag, v => v.value.toInt(v.vr, v.bigEndian))
  def getInt(tagPath: TagPathTag): Option[Int]     = getPath(tagPath, v => v.value.toInt(v.vr, v.bigEndian))
  def getLongs(tag: Int): Seq[Long]                = getAll(tag, v => v.value.toLongs(v.vr, v.bigEndian))
  def getLongs(tagPath: TagPathTag): Seq[Long]     = getAllPath(tagPath, v => v.value.toLongs(v.vr, v.bigEndian))
  def getLong(tag: Int): Option[Long]              = get(tag, v => v.value.toLong(v.vr, v.bigEndian))
  def getLong(tagPath: TagPathTag): Option[Long]   = getPath(tagPath, v => v.value.toLong(v.vr, v.bigEndian))
  def getVeryLongs(tag: Int): Seq[BigInteger]      = getAll(tag, v => v.value.toVeryLongs(v.vr, v.bigEndian))
  def getVeryLongs(tagPath: TagPathTag): Seq[BigInteger] =
    getAllPath(tagPath, v => v.value.toVeryLongs(v.vr, v.bigEndian))
  def getVeryLong(tag: Int): Option[BigInteger] = get(tag, v => v.value.toVeryLong(v.vr, v.bigEndian))
  def getVeryLong(tagPath: TagPathTag): Option[BigInteger] =
    getPath(tagPath, v => v.value.toVeryLong(v.vr, v.bigEndian))
  def getFloats(tag: Int): Seq[Float]                 = getAll(tag, v => v.value.toFloats(v.vr, v.bigEndian))
  def getFloats(tagPath: TagPathTag): Seq[Float]      = getAllPath(tagPath, v => v.value.toFloats(v.vr, v.bigEndian))
  def getFloat(tag: Int): Option[Float]               = get(tag, v => v.value.toFloat(v.vr, v.bigEndian))
  def getFloat(tagPath: TagPathTag): Option[Float]    = getPath(tagPath, v => v.value.toFloat(v.vr, v.bigEndian))
  def getDoubles(tag: Int): Seq[Double]               = getAll(tag, v => v.value.toDoubles(v.vr, v.bigEndian))
  def getDoubles(tagPath: TagPathTag): Seq[Double]    = getAllPath(tagPath, v => v.value.toDoubles(v.vr, v.bigEndian))
  def getDouble(tag: Int): Option[Double]             = get(tag, v => v.value.toDouble(v.vr, v.bigEndian))
  def getDouble(tagPath: TagPathTag): Option[Double]  = getPath(tagPath, v => v.value.toDouble(v.vr, v.bigEndian))
  def getDates(tag: Int): Seq[LocalDate]              = getAll(tag, v => v.value.toDates(v.vr))
  def getDates(tagPath: TagPathTag): Seq[LocalDate]   = getAllPath(tagPath, v => v.value.toDates(v.vr))
  def getDate(tag: Int): Option[LocalDate]            = get(tag, v => v.value.toDate(v.vr))
  def getDate(tagPath: TagPathTag): Option[LocalDate] = getPath(tagPath, v => v.value.toDate(v.vr))
  def getTimes(tag: Int): Seq[LocalTime]              = getAll(tag, v => v.value.toTimes(v.vr))
  def getTimes(tagPath: TagPathTag): Seq[LocalTime]   = getAllPath(tagPath, v => v.value.toTimes(v.vr))
  def getTime(tag: Int): Option[LocalTime]            = get(tag, v => v.value.toTime(v.vr))
  def getTime(tagPath: TagPathTag): Option[LocalTime] = getPath(tagPath, v => v.value.toTime(v.vr))
  def getDateTimes(tag: Int): Seq[ZonedDateTime]      = getAll(tag, v => v.value.toDateTimes(v.vr, zoneOffset))
  def getDateTimes(tagPath: TagPathTag): Seq[ZonedDateTime] =
    getAllPath(tagPath, v => v.value.toDateTimes(v.vr, zoneOffset))
  def getDateTime(tag: Int): Option[ZonedDateTime] = get(tag, v => v.value.toDateTime(v.vr, zoneOffset))
  def getDateTime(tagPath: TagPathTag): Option[ZonedDateTime] =
    getPath(tagPath, v => v.value.toDateTime(v.vr, zoneOffset))
  def getPersonNames(tag: Int): Seq[PersonName] = getAll(tag, v => v.value.toPersonNames(v.vr, characterSets))
  def getPersonNames(tagPath: TagPathTag): Seq[PersonName] =
    getAllPath(tagPath, v => v.value.toPersonNames(v.vr, characterSets))
  def getPersonName(tag: Int): Option[PersonName] = get(tag, v => v.value.toPersonName(v.vr, characterSets))
  def getPersonName(tagPath: TagPathTag): Option[PersonName] =
    getPath(tagPath, v => v.value.toPersonName(v.vr, characterSets))
  def getURI(tag: Int): Option[URI]            = get(tag, v => v.value.toURI(v.vr))
  def getURI(tagPath: TagPathTag): Option[URI] = getPath(tagPath, v => v.value.toURI(v.vr))

  private def traverseTrunk(elems: Option[Elements], trunk: TagPathTrunk): Option[Elements] =
    if (trunk.isEmpty)
      elems
    else
      trunk match {
        case tp: TagPathItem => traverseTrunk(elems, trunk.previous).flatMap(_.getNested(tp.tag, tp.item))
        case _               => throw new IllegalArgumentException("Unsupported tag path type")
      }
  def getSequence(tag: Int): Option[Sequence] =
    apply(tag).flatMap {
      case e: Sequence => Some(e)
      case _           => None
    }
  def getSequence(tagPath: TagPathSequence): Option[Sequence] =
    traverseTrunk(Some(this), tagPath.previous).flatMap(_.getSequence(tagPath.tag))

  def getItem(tag: Int, item: Int): Option[Item]       = getSequence(tag).flatMap(_.item(item))
  def getNested(tag: Int, item: Int): Option[Elements] = getItem(tag, item).map(_.elements)
  def getNested(tagPath: TagPathItem): Option[Elements] =
    traverseTrunk(Some(this), tagPath.previous).flatMap(_.getNested(tagPath.tag, tagPath.item))

  def getFragments(tag: Int): Option[Fragments] =
    apply(tag).flatMap {
      case e: Fragments => Some(e)
      case _            => None
    }

  private def insertOrdered(element: ElementSet): Vector[ElementSet] =
    if (isEmpty) Vector(element)
    else {
      val b       = Vector.newBuilder[ElementSet]
      var isBelow = true
      data.foreach { e =>
        if (isBelow && e.tag > element.tag) {
          b += element
          isBelow = false
        }
        if (e.tag == element.tag) {
          b += element
          isBelow = false
        } else
          b += e
      }
      if (isBelow) b += element
      b.result()
    }

  /**
    * Insert or update element in the root dataset with the specified tag number. If the element is Specific Character
    * Set or Timezone Offset From UTC, this information will be updated accordingly. If the element is not previously
    * present it is inserted in the correct tag number order.
    *
    * @param element element set to insert or update
    * @return a new Elements containing the updated element
    */
  def set(element: ElementSet): Elements =
    element match {
      case e: ValueElement if e.tag == Tag.SpecificCharacterSet =>
        copy(characterSets = CharacterSets(e), data = insertOrdered(e))
      case e: ValueElement if e.tag == Tag.TimezoneOffsetFromUTC =>
        copy(zoneOffset = parseZoneOffset(e.value.toUtf8String).getOrElse(zoneOffset), data = insertOrdered(e))
      case e => copy(data = insertOrdered(e))
    }

  def set(elementSets: Seq[_ <: ElementSet]): Elements = elementSets.foldLeft(this)(_.set(_))

  def setSequence(sequence: Sequence): Elements = set(sequence)

  private def updateSequence(tag: Int, index: Int, update: Elements => Elements): Option[Elements] =
    for {
      s1 <- getSequence(tag)
      i1 <- s1.item(index)
    } yield {
      val e1 = i1.elements
      val e2 = update(e1)
      val i2 = i1.setElements(e2)
      val s2 = s1.setItem(index, i2)
      set(s2)
    }

  private def updatePath(elems: Elements, tagPath: List[_ <: TagPath], f: Elements => Elements): Elements =
    if (tagPath.isEmpty)
      f(elems)
    else
      tagPath.head match {
        case tp: TagPathItem =>
          elems.updateSequence(tp.tag, tp.item, e => updatePath(e, tagPath.tail, f)).getOrElse(elems)
        case _ => throw new IllegalArgumentException("Unsupported tag path type")
      }

  /**
    * Replace the item that the tag path points to
    *
    * @param tagPath  pointer to the item to be replaced
    * @param elements elements of new item
    * @return the updated (root) elements
    */
  def setNested(tagPath: TagPathItem, elements: Elements): Elements =
    updatePath(this, tagPath.toList, _ => elements)

  /**
    * Set (insert or update) an element in the item that the tag path points to
    *
    * @param tagPath pointer to the item to insert element to
    * @param element new element to insert or update
    * @return the updated (root) elements
    */
  def set(tagPath: TagPathItem, element: ElementSet): Elements =
    updatePath(this, tagPath.toList, _.set(element))

  /**
    * Set (insert of update) a sequence in the item that the tag path points to
    *
    * @param tagPath  pointer to the item to insert sequence to
    * @param sequence new sequence to insert or update
    * @return the updated (root) elements
    */
  def setSequence(tagPath: TagPathItem, sequence: Sequence): Elements =
    set(tagPath, sequence)

  /**
    * Add an item to the sequence that the tag path points to
    *
    * @param tagPath  pointer to the sequence to add item to
    * @param elements elements of new item
    * @return the updated (root) elements
    */
  def addItem(tagPath: TagPathSequence, elements: Elements): Elements =
    getSequence(tagPath)
      .map { sequence =>
        val bigEndian     = sequence.bigEndian
        val indeterminate = sequence.indeterminate
        val item =
          if (indeterminate)
            Item.fromElements(elements, indeterminateLength, bigEndian)
          else
            Item.fromElements(elements, elements.toBytes(withPreamble = false).length.toLong, bigEndian)
        val updatedSequence = sequence + item
        tagPath.previous match {
          case EmptyTagPath    => setSequence(updatedSequence)
          case tp: TagPathItem => setSequence(tp, updatedSequence)
          case _               => throw new IllegalArgumentException("Unsupported tag path type")
        }
      }
      .getOrElse(this)

  def setCharacterSets(characterSets: CharacterSets): Elements = copy(characterSets = characterSets)
  def setZoneOffset(zoneOffset: ZoneOffset): Elements          = copy(zoneOffset = zoneOffset)

  def setValue(tag: Int, vr: VR, value: Value, bigEndian: Boolean, explicitVR: Boolean): Elements =
    set(ValueElement(tag, vr, value, bigEndian, explicitVR))
  def setValue(tag: Int, value: Value, bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setValue(tag, Lookup.vrOf(tag), value, bigEndian, explicitVR)

  def setBytes(tag: Int, vr: VR, value: ByteString, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value(value), bigEndian, explicitVR)
  def setBytes(tag: Int, value: ByteString, bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setBytes(tag, Lookup.vrOf(tag), value, bigEndian, explicitVR)

  def setStrings(tag: Int, vr: VR, values: Seq[String], bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromStrings(vr, values, bigEndian), bigEndian, explicitVR)
  def setStrings(tag: Int, values: Seq[String], bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setStrings(tag, Lookup.vrOf(tag), values, bigEndian, explicitVR)
  def setString(tag: Int, vr: VR, value: String, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromString(vr, value, bigEndian), bigEndian, explicitVR)
  def setString(tag: Int, value: String, bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setString(tag, Lookup.vrOf(tag), value, bigEndian, explicitVR)

  def setShorts(tag: Int, vr: VR, values: Seq[Short], bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromShorts(vr, values, bigEndian), bigEndian, explicitVR)
  def setShorts(tag: Int, values: Seq[Short], bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setShorts(tag, Lookup.vrOf(tag), values, bigEndian, explicitVR)
  def setShort(tag: Int, vr: VR, value: Short, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromShort(vr, value, bigEndian), bigEndian, explicitVR)
  def setShort(tag: Int, value: Short, bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setShort(tag, Lookup.vrOf(tag), value, bigEndian, explicitVR)

  def setInts(tag: Int, vr: VR, values: Seq[Int], bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromInts(vr, values, bigEndian), bigEndian, explicitVR)
  def setInts(tag: Int, values: Seq[Int], bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setInts(tag, Lookup.vrOf(tag), values, bigEndian, explicitVR)
  def setInt(tag: Int, vr: VR, value: Int, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromInt(vr, value, bigEndian), bigEndian, explicitVR)
  def setInt(tag: Int, value: Int, bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setInt(tag, Lookup.vrOf(tag), value, bigEndian, explicitVR)

  def setLongs(tag: Int, vr: VR, values: Seq[Long], bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromLongs(vr, values, bigEndian), bigEndian, explicitVR)
  def setLongs(tag: Int, values: Seq[Long], bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setLongs(tag, Lookup.vrOf(tag), values, bigEndian, explicitVR)
  def setLong(tag: Int, vr: VR, value: Long, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromLong(vr, value, bigEndian), bigEndian, explicitVR)
  def setLong(tag: Int, value: Long, bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setLong(tag, Lookup.vrOf(tag), value, bigEndian, explicitVR)

  def setVeryLongs(tag: Int, vr: VR, values: Seq[BigInteger], bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromVeryLongs(vr, values, bigEndian), bigEndian, explicitVR)
  def setVeryLongs(
      tag: Int,
      values: Seq[BigInteger],
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setVeryLongs(tag, Lookup.vrOf(tag), values, bigEndian, explicitVR)
  def setVeryLong(tag: Int, vr: VR, value: BigInteger, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromVeryLong(vr, value, bigEndian), bigEndian, explicitVR)
  def setVeryLong(tag: Int, value: BigInteger, bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setVeryLong(tag, Lookup.vrOf(tag), value, bigEndian, explicitVR)

  def setFloats(tag: Int, vr: VR, values: Seq[Float], bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromFloats(vr, values, bigEndian), bigEndian, explicitVR)
  def setFloats(tag: Int, values: Seq[Float], bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setFloats(tag, Lookup.vrOf(tag), values, bigEndian, explicitVR)
  def setFloat(tag: Int, vr: VR, value: Float, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromFloat(vr, value, bigEndian), bigEndian, explicitVR)
  def setFloat(tag: Int, value: Float, bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setFloat(tag, Lookup.vrOf(tag), value, bigEndian, explicitVR)

  def setDoubles(tag: Int, vr: VR, values: Seq[Double], bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromDoubles(vr, values, bigEndian), bigEndian, explicitVR)
  def setDoubles(tag: Int, values: Seq[Double], bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setDoubles(tag, Lookup.vrOf(tag), values, bigEndian, explicitVR)
  def setDouble(tag: Int, vr: VR, value: Double, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromDouble(vr, value, bigEndian), bigEndian, explicitVR)
  def setDouble(tag: Int, value: Double, bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setDouble(tag, Lookup.vrOf(tag), value, bigEndian, explicitVR)

  def setDates(tag: Int, vr: VR, values: Seq[LocalDate], bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromDates(vr, values), bigEndian, explicitVR)
  def setDates(tag: Int, values: Seq[LocalDate], bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setDates(tag, Lookup.vrOf(tag), values, bigEndian, explicitVR)
  def setDate(tag: Int, vr: VR, value: LocalDate, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromDate(vr, value), bigEndian, explicitVR)
  def setDate(tag: Int, value: LocalDate, bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setDate(tag, Lookup.vrOf(tag), value, bigEndian, explicitVR)

  def setTimes(tag: Int, vr: VR, values: Seq[LocalTime], bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromTimes(vr, values), bigEndian, explicitVR)
  def setTimes(tag: Int, values: Seq[LocalTime], bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setTimes(tag, Lookup.vrOf(tag), values, bigEndian, explicitVR)
  def setTime(tag: Int, vr: VR, value: LocalTime, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromTime(vr, value), bigEndian, explicitVR)
  def setTime(tag: Int, value: LocalTime, bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setTime(tag, Lookup.vrOf(tag), value, bigEndian, explicitVR)

  def setDateTimes(tag: Int, vr: VR, values: Seq[ZonedDateTime], bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromDateTimes(vr, values), bigEndian, explicitVR)
  def setDateTimes(
      tag: Int,
      values: Seq[ZonedDateTime],
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setDateTimes(tag, Lookup.vrOf(tag), values, bigEndian, explicitVR)
  def setDateTime(tag: Int, vr: VR, value: ZonedDateTime, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromDateTime(vr, value), bigEndian, explicitVR)
  def setDateTime(tag: Int, value: ZonedDateTime, bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setDateTime(tag, Lookup.vrOf(tag), value, bigEndian, explicitVR)

  def setPersonNames(tag: Int, vr: VR, values: Seq[PersonName], bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromPersonNames(vr, values), bigEndian, explicitVR)
  def setPersonNames(
      tag: Int,
      values: Seq[PersonName],
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setPersonNames(tag, Lookup.vrOf(tag), values, bigEndian, explicitVR)
  def setPersonName(tag: Int, vr: VR, value: PersonName, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromPersonName(vr, value), bigEndian, explicitVR)
  def setPersonName(tag: Int, value: PersonName, bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setPersonName(tag, Lookup.vrOf(tag), value, bigEndian, explicitVR)

  def setURI(tag: Int, vr: VR, value: URI, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setValue(tag, vr, Value.fromURI(vr, value), bigEndian, explicitVR)
  def setURI(tag: Int, value: URI, bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setURI(tag, Lookup.vrOf(tag), value, bigEndian, explicitVR)

  def setNestedValue(tagPath: TagPathTag, vr: VR, value: Value, bigEndian: Boolean, explicitVR: Boolean): Elements =
    if (tagPath.isRoot)
      setValue(tagPath.tag, vr, value, bigEndian, explicitVR)
    else
      setNested(
        tagPath.previous.asInstanceOf[TagPathItem],
        getNested(tagPath.previous.asInstanceOf[TagPathItem])
          .getOrElse(Elements.empty())
          .setValue(tagPath.tag, vr, value, bigEndian, explicitVR)
      )
  def setNestedValue(
      tagPath: TagPathTag,
      value: Value,
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedValue(tagPath, Lookup.vrOf(tagPath.tag), value, bigEndian, explicitVR)

  def setNestedBytes(
      tagPath: TagPathTag,
      vr: VR,
      value: ByteString,
      bigEndian: Boolean,
      explicitVR: Boolean
  ): Elements =
    setNestedValue(tagPath, vr, Value(value), bigEndian, explicitVR)
  def setNestedBytes(
      tagPath: TagPathTag,
      value: ByteString,
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedBytes(tagPath, Lookup.vrOf(tagPath.tag), value, bigEndian, explicitVR)

  def setNestedStrings(
      tagPath: TagPathTag,
      vr: VR,
      values: Seq[String],
      bigEndian: Boolean,
      explicitVR: Boolean
  ): Elements =
    setNestedValue(tagPath, vr, Value.fromStrings(vr, values, bigEndian), bigEndian, explicitVR)
  def setNestedStrings(
      tagPath: TagPathTag,
      values: Seq[String],
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedStrings(tagPath, Lookup.vrOf(tagPath.tag), values, bigEndian, explicitVR)
  def setNestedString(tagPath: TagPathTag, vr: VR, value: String, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setNestedValue(tagPath, vr, Value.fromString(vr, value, bigEndian), bigEndian, explicitVR)
  def setNestedString(
      tagPath: TagPathTag,
      value: String,
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedString(tagPath, Lookup.vrOf(tagPath.tag), value, bigEndian, explicitVR)

  def setNestedShorts(
      tagPath: TagPathTag,
      vr: VR,
      values: Seq[Short],
      bigEndian: Boolean,
      explicitVR: Boolean
  ): Elements =
    setNestedValue(tagPath, vr, Value.fromShorts(vr, values, bigEndian), bigEndian, explicitVR)
  def setNestedShorts(
      tagPath: TagPathTag,
      values: Seq[Short],
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedShorts(tagPath, Lookup.vrOf(tagPath.tag), values, bigEndian, explicitVR)
  def setNestedShort(tagPath: TagPathTag, vr: VR, value: Short, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setNestedValue(tagPath, vr, Value.fromShort(vr, value, bigEndian), bigEndian, explicitVR)
  def setNestedShort(
      tagPath: TagPathTag,
      value: Short,
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedShort(tagPath, Lookup.vrOf(tagPath.tag), value, bigEndian, explicitVR)

  def setNestedInts(tagPath: TagPathTag, vr: VR, values: Seq[Int], bigEndian: Boolean, explicitVR: Boolean): Elements =
    setNestedValue(tagPath, vr, Value.fromInts(vr, values, bigEndian), bigEndian, explicitVR)
  def setNestedInts(
      tagPath: TagPathTag,
      values: Seq[Int],
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedInts(tagPath, Lookup.vrOf(tagPath.tag), values, bigEndian, explicitVR)
  def setNestedInt(tagPath: TagPathTag, vr: VR, value: Int, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setNestedValue(tagPath, vr, Value.fromInt(vr, value, bigEndian), bigEndian, explicitVR)
  def setNestedInt(tagPath: TagPathTag, value: Int, bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setNestedInt(tagPath, Lookup.vrOf(tagPath.tag), value, bigEndian, explicitVR)

  def setNestedLongs(
      tagPath: TagPathTag,
      vr: VR,
      values: Seq[Long],
      bigEndian: Boolean,
      explicitVR: Boolean
  ): Elements =
    setNestedValue(tagPath, vr, Value.fromLongs(vr, values, bigEndian), bigEndian, explicitVR)
  def setNestedLongs(
      tagPath: TagPathTag,
      values: Seq[Long],
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedLongs(tagPath, Lookup.vrOf(tagPath.tag), values, bigEndian, explicitVR)
  def setNestedLong(tagPath: TagPathTag, vr: VR, value: Long, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setNestedValue(tagPath, vr, Value.fromLong(vr, value, bigEndian), bigEndian, explicitVR)
  def setNestedLong(
      tagPath: TagPathTag,
      value: Long,
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedLong(tagPath, Lookup.vrOf(tagPath.tag), value, bigEndian, explicitVR)

  def setNestedVeryLongs(
      tagPath: TagPathTag,
      vr: VR,
      values: Seq[BigInteger],
      bigEndian: Boolean,
      explicitVR: Boolean
  ): Elements =
    setNestedValue(tagPath, vr, Value.fromVeryLongs(vr, values, bigEndian), bigEndian, explicitVR)
  def setNestedVeryLongs(
      tagPath: TagPathTag,
      values: Seq[BigInteger],
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedVeryLongs(tagPath, Lookup.vrOf(tagPath.tag), values, bigEndian, explicitVR)
  def setNestedVeryLong(
      tagPath: TagPathTag,
      vr: VR,
      value: BigInteger,
      bigEndian: Boolean,
      explicitVR: Boolean
  ): Elements =
    setNestedValue(tagPath, vr, Value.fromVeryLong(vr, value, bigEndian), bigEndian, explicitVR)
  def setNestedVeryLong(
      tagPath: TagPathTag,
      value: BigInteger,
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedVeryLong(tagPath, Lookup.vrOf(tagPath.tag), value, bigEndian, explicitVR)

  def setNestedFloats(
      tagPath: TagPathTag,
      vr: VR,
      values: Seq[Float],
      bigEndian: Boolean,
      explicitVR: Boolean
  ): Elements =
    setNestedValue(tagPath, vr, Value.fromFloats(vr, values, bigEndian), bigEndian, explicitVR)
  def setNestedFloats(
      tagPath: TagPathTag,
      values: Seq[Float],
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedFloats(tagPath, Lookup.vrOf(tagPath.tag), values, bigEndian, explicitVR)
  def setNestedFloat(tagPath: TagPathTag, vr: VR, value: Float, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setNestedValue(tagPath, vr, Value.fromFloat(vr, value, bigEndian), bigEndian, explicitVR)
  def setNestedFloat(
      tagPath: TagPathTag,
      value: Float,
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedFloat(tagPath, Lookup.vrOf(tagPath.tag), value, bigEndian, explicitVR)

  def setNestedDoubles(
      tagPath: TagPathTag,
      vr: VR,
      values: Seq[Double],
      bigEndian: Boolean,
      explicitVR: Boolean
  ): Elements =
    setNestedValue(tagPath, vr, Value.fromDoubles(vr, values, bigEndian), bigEndian, explicitVR)
  def setNestedDoubles(
      tagPath: TagPathTag,
      values: Seq[Double],
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedDoubles(tagPath, Lookup.vrOf(tagPath.tag), values, bigEndian, explicitVR)
  def setNestedDouble(tagPath: TagPathTag, vr: VR, value: Double, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setNestedValue(tagPath, vr, Value.fromDouble(vr, value, bigEndian), bigEndian, explicitVR)
  def setNestedDouble(
      tagPath: TagPathTag,
      value: Double,
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedDouble(tagPath, Lookup.vrOf(tagPath.tag), value, bigEndian, explicitVR)

  def setNestedDates(
      tagPath: TagPathTag,
      vr: VR,
      values: Seq[LocalDate],
      bigEndian: Boolean,
      explicitVR: Boolean
  ): Elements =
    setNestedValue(tagPath, vr, Value.fromDates(vr, values), bigEndian, explicitVR)
  def setNestedDates(
      tagPath: TagPathTag,
      values: Seq[LocalDate],
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedDates(tagPath, Lookup.vrOf(tagPath.tag), values, bigEndian, explicitVR)
  def setNestedDate(tagPath: TagPathTag, vr: VR, value: LocalDate, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setNestedValue(tagPath, vr, Value.fromDate(vr, value), bigEndian, explicitVR)
  def setNestedDate(
      tagPath: TagPathTag,
      value: LocalDate,
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedDate(tagPath, Lookup.vrOf(tagPath.tag), value, bigEndian, explicitVR)

  def setNestedTimes(
      tagPath: TagPathTag,
      vr: VR,
      values: Seq[LocalTime],
      bigEndian: Boolean,
      explicitVR: Boolean
  ): Elements =
    setNestedValue(tagPath, vr, Value.fromTimes(vr, values), bigEndian, explicitVR)
  def setNestedTimes(
      tagPath: TagPathTag,
      values: Seq[LocalTime],
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedTimes(tagPath, Lookup.vrOf(tagPath.tag), values, bigEndian, explicitVR)
  def setNestedTime(tagPath: TagPathTag, vr: VR, value: LocalTime, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setNestedValue(tagPath, vr, Value.fromTime(vr, value), bigEndian, explicitVR)
  def setNestedTime(
      tagPath: TagPathTag,
      value: LocalTime,
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedTime(tagPath, Lookup.vrOf(tagPath.tag), value, bigEndian, explicitVR)

  def setNestedDateTimes(
      tagPath: TagPathTag,
      vr: VR,
      values: Seq[ZonedDateTime],
      bigEndian: Boolean,
      explicitVR: Boolean
  ): Elements =
    setNestedValue(tagPath, vr, Value.fromDateTimes(vr, values), bigEndian, explicitVR)
  def setNestedDateTimes(
      tagPath: TagPathTag,
      values: Seq[ZonedDateTime],
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedDateTimes(tagPath, Lookup.vrOf(tagPath.tag), values, bigEndian, explicitVR)
  def setNestedDateTime(
      tagPath: TagPathTag,
      vr: VR,
      value: ZonedDateTime,
      bigEndian: Boolean,
      explicitVR: Boolean
  ): Elements =
    setNestedValue(tagPath, vr, Value.fromDateTime(vr, value), bigEndian, explicitVR)
  def setNestedDateTime(
      tagPath: TagPathTag,
      value: ZonedDateTime,
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedDateTime(tagPath, Lookup.vrOf(tagPath.tag), value, bigEndian, explicitVR)

  def setNestedPersonNames(
      tagPath: TagPathTag,
      vr: VR,
      values: Seq[PersonName],
      bigEndian: Boolean,
      explicitVR: Boolean
  ): Elements =
    setNestedValue(tagPath, vr, Value.fromPersonNames(vr, values), bigEndian, explicitVR)
  def setNestedPersonNames(
      tagPath: TagPathTag,
      values: Seq[PersonName],
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedPersonNames(tagPath, Lookup.vrOf(tagPath.tag), values, bigEndian, explicitVR)
  def setNestedPersonName(
      tagPath: TagPathTag,
      vr: VR,
      value: PersonName,
      bigEndian: Boolean,
      explicitVR: Boolean
  ): Elements =
    setNestedValue(tagPath, vr, Value.fromPersonName(vr, value), bigEndian, explicitVR)
  def setNestedPersonName(
      tagPath: TagPathTag,
      value: PersonName,
      bigEndian: Boolean = false,
      explicitVR: Boolean = true
  ): Elements =
    setNestedPersonName(tagPath, Lookup.vrOf(tagPath.tag), value, bigEndian, explicitVR)

  def setNestedURI(tagPath: TagPathTag, vr: VR, value: URI, bigEndian: Boolean, explicitVR: Boolean): Elements =
    setNestedValue(tagPath, vr, Value.fromURI(vr, value), bigEndian, explicitVR)
  def setNestedURI(tagPath: TagPathTag, value: URI, bigEndian: Boolean = false, explicitVR: Boolean = true): Elements =
    setNestedURI(tagPath, Lookup.vrOf(tagPath.tag), value, bigEndian, explicitVR)

  def remove(tag: Int): Elements = filter(_.tag != tag)
  def remove(tagPath: TagPath): Elements =
    tagPath match {
      case EmptyTagPath => this
      case tp: TagPathItem =>
        tp.previous match {
          case EmptyTagPath => getSequence(tp.tag).map(s => set(s.removeItem(tp.item))).getOrElse(this)
          case tpsi: TagPathItem =>
            getNested(tpsi).map(_.remove(TagPath.fromItem(tp.tag, tp.item))).map(setNested(tpsi, _)).getOrElse(this)
          case _ => throw new IllegalArgumentException("Unsupported tag path type")
        }
      case tp: TagPathTag =>
        tp.previous match {
          case EmptyTagPath      => remove(tp.tag)
          case tpsi: TagPathItem => getNested(tpsi).map(_.remove(tp.tag)).map(setNested(tpsi, _)).getOrElse(this)
          case _                 => throw new IllegalArgumentException("Unsupported tag path type")
        }
      case _ => throw new IllegalArgumentException("Unsupported tag path type")
    }
  def filter(f: ElementSet => Boolean): Elements = copy(data = data.filter(f))

  def head: ElementSet                        = data.head
  def size: Int                               = data.size
  def isEmpty: Boolean                        = data.isEmpty
  def nonEmpty: Boolean                       = !isEmpty
  def contains(tag: Int): Boolean             = data.map(_.tag).contains(tag)
  def contains(tagPath: TagPathTag): Boolean  = apply(tagPath).isDefined
  def contains(tagPath: TagPathItem): Boolean = getNested(tagPath).isDefined

  /**
    * @return a new Elements sorted by tag number. If already sorted, this function returns a copy
    */
  def sorted(): Elements = copy(data = data.sortBy(_.tag))

  def toList: List[ElementSet] = data.toList
  def toElements(withPreamble: Boolean = true): List[Element] =
    if (withPreamble) PreambleElement :: toList.flatMap(_.toElements) else toList.flatMap(_.toElements)
  def toParts(withPreamble: Boolean = true): List[DicomPart] = toElements(withPreamble).flatMap(_.toParts)
  def toBytes(withPreamble: Boolean = true): ByteString =
    data.map(_.toBytes).foldLeft(if (withPreamble) PreambleElement.toBytes else ByteString.empty)(_ ++ _)

  private def toStrings(indent: String): Vector[String] = {
    def space1(description: String): String = " " * Math.max(0, 40 - description.length)

    def space2(length: Long): String = " " * Math.max(0, 4 - length.toString.length)

    data.flatMap {
      case e: ValueElement =>
        val strings = e.value.toStrings(e.vr, e.bigEndian, characterSets)
        val s       = strings.mkString(multiValueDelimiter)
        val vm      = strings.length.toString
        s"$indent${tagToString(e.tag)} ${e.vr} [$s] ${space1(s)} # ${space2(e.length)} ${e.length}, $vm ${Lookup
          .keywordOf(e.tag)
          .getOrElse("")}" :: Nil

      case s: Sequence =>
        val heading = {
          val description =
            if (s.length == indeterminateLength) "Sequence with indeterminate length"
            else s"Sequence with explicit length ${s.length}"
          s"$indent${tagToString(s.tag)} SQ ($description) ${space1(description)} # ${space2(s.length)} ${s.length}, 1 ${Lookup
            .keywordOf(s.tag)
            .getOrElse("")}"
        }
        val items = s.items.flatMap { i =>
          val heading = {
            val description =
              if (i.indeterminate) "Item with indeterminate length" else s"Item with explicit length ${i.length}"
            s"$indent  ${tagToString(Tag.Item)} na ($description) ${space1(description)} # ${space2(i.length)} ${i.length}, 1 Item"
          }
          val elems = i.elements.toStrings(indent + "    ").toList
          val delimitation =
            s"$indent  ${tagToString(Tag.ItemDelimitationItem)} na ${" " * 43} #     0, 0 ItemDelimitationItem${if (i.indeterminate) ""
            else " (marker)"}"
          heading :: elems ::: delimitation :: Nil
        }
        val delimitation =
          s"$indent${tagToString(Tag.SequenceDelimitationItem)} na ${" " * 43} #     0, 0 SequenceDelimitationItem${if (s.indeterminate) ""
          else " (marker)"}"
        heading :: items ::: delimitation :: Nil

      case f: Fragments =>
        val heading = {
          val description = s"Fragments with ${f.size} fragment(s)"
          s"$indent${tagToString(f.tag)} ${f.vr} ($description) ${space1(description)} #    na, 1 ${Lookup.keywordOf(f.tag).getOrElse("")}"
        }
        val offsets = f.offsets
          .map { o =>
            val description = s"Offsets table with ${o.length} offset(s)"
            s"$indent  ${tagToString(Tag.Item)} na ($description) ${space1(description)} # ${space2(o.length * 4L)} ${o.length * 4}, 1 Item" :: Nil
          }
          .getOrElse(Nil)
        val fragments = f.fragments.map { f =>
          val description = s"Fragment with length ${f.length}"
          s"$indent  ${tagToString(Tag.Item)} na ($description) ${space1(description)} # ${space2(f.length)} ${f.length}, 1 Item"
        }
        val delimitation =
          s"$indent${tagToString(Tag.SequenceDelimitationItem)} na ${" " * 43} #     0, 0 SequenceDelimitationItem"
        heading :: offsets ::: fragments ::: delimitation :: Nil

      case _ => Nil
    }
  }

  override def toString: String = toStrings("").mkString(System.lineSeparator)

}

object Elements {

  /**
    * @return an Elements with no data and default character set only and the system's time zone
    */
  def empty(): Elements =
    Elements(defaultCharacterSet, systemZone, Vector.empty)

  /**
    * @return create a new Elements builder used to incrementally add data to Elements in a performant manner
    */
  def newBuilder() = new ElementsBuilder()

  def fileMetaInformationElements(
      sopInstanceUID: String,
      sopClassUID: String,
      transferSyntax: String
  ): List[ValueElement] = {
    val fmiElements = List(
      ValueElement.fromBytes(Tag.FileMetaInformationVersion, ByteString(0, 1)),
      ValueElement
        .fromBytes(Tag.MediaStorageSOPClassUID, padToEvenLength(ByteString(sopClassUID), Tag.MediaStorageSOPClassUID)),
      ValueElement.fromBytes(
        Tag.MediaStorageSOPInstanceUID,
        padToEvenLength(ByteString(sopInstanceUID), Tag.MediaStorageSOPInstanceUID)
      ),
      ValueElement.fromBytes(Tag.TransferSyntaxUID, padToEvenLength(ByteString(transferSyntax), Tag.TransferSyntaxUID)),
      ValueElement.fromBytes(
        Tag.ImplementationClassUID,
        padToEvenLength(ByteString(Implementation.classUid), Tag.ImplementationClassUID)
      ),
      ValueElement.fromBytes(
        Tag.ImplementationVersionName,
        padToEvenLength(ByteString(Implementation.versionName), Tag.ImplementationVersionName)
      )
    )
    val groupLength =
      ValueElement.fromBytes(Tag.FileMetaInformationGroupLength, intToBytesLE(fmiElements.map(_.toBytes.length).sum))
    groupLength :: fmiElements
  }

  def parseZoneOffset(s: String): Option[ZoneOffset] =
    try Option(ZoneOffset.of(s))
    catch {
      case _: Throwable => None
    }

}
