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

import com.exini.dicom.data.TagPath._

import scala.annotation.tailrec

/**
  * Representation of a single pointer into a DICOM dataset described by a (possibly empty) sequence of
  * (sequence tag, item) pairs and (optionally) a leaf tag.
  */
sealed trait TagPath extends TagPathLike {

  override type P = TagPath
  override type T = TagPathTrunk
  override type E = EmptyTagPath.type

  override protected val empty: E = EmptyTagPath

  /**
    * Test if this tag path is less than the input path, comparing their parts pairwise from root to leaf according to
    * the following rules:<br>
    * (1) a is less than b if the a's tag number is less b's tag number<br>
    * (2) a is less than b if tag numbers are equal and a's item index is less than b's item index<br>
    * (3) the start of a sequence is less than the body and end of that sequence<br>
    * (4) the start and body of an item is less than the end of that item<br>
    * (5) the empty tag path is less than all other tags<br>
    *
    * @param that the tag path to compare with
    * @return `true` if this tag path is less than the input path
    */
  def <(that: TagPath): Boolean = {
    val thisList: List[TagPath] = this.toList
    val thatList: List[TagPath] = that.toList

    thisList.zip(thatList).foreach {
      case (EmptyTagPath, thatPath) =>
        return !thatPath.isEmpty
      case (_, EmptyTagPath) =>
        return false
      case (thisPath, thatPath) if thisPath.tag != thatPath.tag =>
        return intToUnsignedLong(thisPath.tag) < intToUnsignedLong(thatPath.tag)
      case (_: TagPathSequence, _: TagPath with ItemIndex) => // tag numbers equal from here
        return true
      case (_: TagPathSequence, _: TagPathSequenceEnd) =>
        return true
      case (_: TagPathSequenceEnd, _: TagPath with ItemIndex) =>
        return false
      case (_: TagPathSequenceEnd, _: TagPathSequence) =>
        return false
      case (_: TagPath with ItemIndex, _: TagPathSequence) =>
        return false
      case (_: TagPath with ItemIndex, _: TagPathSequenceEnd) =>
        return true
      case (thisPath: TagPath with ItemIndex, thatPath: TagPath with ItemIndex) if thisPath.item != thatPath.item =>
        return thisPath.item < thatPath.item
      case (_: TagPathItem, _: TagPathItemEnd) => // tag and item numbers equal from here
        return true
      case (_: TagPathItemEnd, _: TagPathItem) => // tag and item numbers equal from here
        return false
      case _ => // tags and item numbers are equal, and same class -> check next
    }
    thisList.length < thatList.length
  }

  /**
    * @param that tag path to test
    * @return `true` if the input tag path is equal to this tag path. TagPath nodes are compared pairwise from the end
    *         towards the start of the paths. Node types, tag numbers as well as item indices where applicable must be
    *         equal. Paths of different lengths cannot be equal.
    * @example (0010,0010) == (0010,0010)
    * @example (0010,0010) != (0010,0020)
    * @example (0010,0010) != (0008,9215)[1].(0010,0010)
    * @example (0008,9215)[3].(0010,0010) == (0008,9215)[3].(0010,0010)
    */
  override def equals(that: Any): Boolean = (this, that) match {
    case (p1: TagPath, p2: TagPath) if p1.isEmpty && p2.isEmpty => true
    case (p1: TagPathTag, p2: TagPathTag) => p1.tag == p2.tag && p1.previous == p2.previous
    case (p1: TagPathSequence, p2: TagPathSequence) => p1.tag == p2.tag && p1.previous == p2.previous
    case (p1: TagPathSequenceEnd, p2: TagPathSequenceEnd) => p1.tag == p2.tag && p1.previous == p2.previous
    case (p1: TagPathItem, p2: TagPathItem) => p1.tag == p2.tag && p1.item == p2.item && p1.previous == p2.previous
    case (p1: TagPathItemEnd, p2: TagPathItemEnd) => p1.tag == p2.tag && p1.item == p2.item && p1.previous == p2.previous
    case _ => false
  }

  /**
    * @param that tag path to test
    * @return `true` if this tag path begins with the input tag path.
    * @example (0010,0010) starts with (0010,0010)
    * @example (0008,9215)[2].(0010,0010) starts with (0008,9215)[2]
    * @example (0008,9215)[2].(0010,0010) starts with the empty tag path
    * @example (0008,9215)[2].(0010,0010) does not start with (0008,9215)[1]
    * @example (0008,9215)[2] does not start with (0008,9215)[2].(0010,0010)
    */
  def startsWith(that: TagPath): Boolean = {
    val thisDepth = this.depth
    val thatDepth = that.depth
    if (thisDepth >= thatDepth) {
      val n = Math.min(thisDepth, thatDepth)
      this.take(n) == that.take(n)
    } else
      false
  }

  /**
    * @param that tag path to test
    * @return `true` if this tag path ends with the input tag path
    * @example (0010,0010) ends with (0010,0010)
    * @example (0008,9215)[2].(0010,0010) ends with (0010,0010)
    * @example (0008,9215)[2].(0010,0010) ends with the empty tag path
    * @example (0010,0010) does not end with (0008,9215)[2].(0010,0010)
    */
  def endsWith(that: TagPath): Boolean = {
    val n = this.depth - that.depth
    if (n >= 0)
      drop(n) == that
    else
      false
  }

  def drop(n: Int): TagPath = {
    def drop(path: TagPath, i: Int): TagPath =
      if (i < 0)
        EmptyTagPath
      else if (i == 0)
        path match {
          case EmptyTagPath => EmptyTagPath
          case p: TagPathItem => TagPath.fromItem(p.tag, p.item)
          case p: TagPathItemEnd => TagPath.fromItemEnd(p.tag, p.item)
          case p: TagPathSequence => TagPath.fromSequence(p.tag)
          case p: TagPathSequenceEnd => TagPath.fromSequenceEnd(p.tag)
          case p => TagPath.fromTag(p.tag)
        }
      else
        drop(path.previous, i - 1) match {
          case p: TagPathTrunk => path match {
            case pi: TagPathItem => p.thenItem(pi.tag, pi.item)
            case pi: TagPathItemEnd => p.thenItemEnd(pi.tag, pi.item)
            case pi: TagPathSequence => p.thenSequence(pi.tag)
            case pi: TagPathSequenceEnd => p.thenSequenceEnd(pi.tag)
            case pt: TagPathTag => p.thenTag(pt.tag)
            case _ => EmptyTagPath
          }
          case _ => EmptyTagPath // cannot happen
        }

    drop(path = this, i = depth - n - 1)
  }

  def toString(lookup: Boolean): String = {
    def toTagString(tag: Int): String = if (lookup) {
      val keyword = Lookup.keywordOf(tag)
      if (keyword.isEmpty) tagToString(tag) else keyword
    }
    else
      tagToString(tag)

    @tailrec
    def toTagPathString(path: TagPath, tail: String): String = {
      val itemIndexSuffix = path match {
        case s: TagPathItem => s"[${s.item}]"
        case s: TagPathItemEnd => s"[${s.item}]"
        case _ => ""
      }
      val head = toTagString(path.tag) + itemIndexSuffix
      val part = head + tail
      if (path.isRoot) part else toTagPathString(path.previous, "." + part)
    }

    if (isEmpty) "<empty path>" else toTagPathString(path = this, tail = "")
  }

  override def hashCode(): Int = this match {
    case EmptyTagPath => 0
    case s: TagPathItem => 31 * (31 * (31 * previous.hashCode() + tag.hashCode()) * s.item.hashCode())
    case s: TagPathItemEnd => 31 * (31 * (31 * previous.hashCode() + tag.hashCode()) * s.item.hashCode())
    case _ => 31 * (31 * previous.hashCode() + tag.hashCode())
  }
}

object TagPath {


  /**
    * Common trait for tag path nodes with an item index
    */
  trait ItemIndex {
    val item: Int
  }

  /**
    * A tag path that points to a non-sequence tag
    *
    * @param tag      the tag number
    * @param previous a link to the part of this tag path to the left of this tag
    */
  class TagPathTag private[TagPath](val tag: Int, val previous: TagPathTrunk) extends TagPath

  object TagPathTag {

    /**
      * Parse the string representation of a tag path into a tag path object. Tag paths can either be specified using tag
      * numbers or their corresponding keywords.
      *
      * Examples: (0008,9215)[1].(0010,0010) = DerivationCodeSequence[1].(0010,0010) = (0008,9215)[1].PatientName =
      * DerivationCodeSequence[1].PatientName
      *
      * @param s string to parse
      * @return a tag path
      * @throws IllegalArgumentException for malformed input
      */
    def parse(s: String): TagPathTag = {
      def indexPart(s: String): String = s.substring(s.lastIndexOf('[') + 1, s.length - 1)

      def tagPart(s: String): String = s.substring(0, s.indexOf('['))

      def parseTag(s: String): Int = try Integer.parseInt(s.substring(1, 5) + s.substring(6, 10), 16) catch {
        case _: Throwable =>
          Lookup.tagOf(s)
      }

      def parseIndex(s: String): Int = Integer.parseInt(s)

      def createTag(s: String): TagPathTag = TagPath.fromTag(parseTag(s))

      def addTag(s: String, path: TagPathTrunk): TagPathTag = path.thenTag(parseTag(s))

      def createSeq(s: String): TagPathTrunk = TagPath.fromItem(parseTag(tagPart(s)), parseIndex(indexPart(s)))

      def addSeq(s: String, path: TagPathTrunk): TagPathTrunk = path.thenItem(parseTag(tagPart(s)), parseIndex(indexPart(s)))

      val tags = if (s.indexOf('.') > 0) s.split("\\.").toList else List(s)
      val seqTags = if (tags.length > 1) tags.init else Nil // list of sequence tags, if any
      val lastTag = tags.last // tag or sequence
      try
        seqTags.headOption
          .map(first => seqTags.tail.foldLeft(createSeq(first))((path, tag) => addSeq(tag, path)))
          .map(path => addTag(lastTag, path))
          .getOrElse(createTag(lastTag))
      catch {
        case e: Exception => throw new IllegalArgumentException("Tag path could not be parsed", e)
      }
    }
  }

  /**
    * Representation of the start of a sequence
    */
  class TagPathSequence private[TagPath](val tag: Int, val previous: TagPathTrunk) extends TagPath

  /**
    * Representation of the end of a sequence
    */
  class TagPathSequenceEnd private[TagPath](val tag: Int, val previous: TagPathTrunk) extends TagPath

  /**
    * Representation of the start or body of an item
    */
  class TagPathItem private[TagPath](val tag: Int, val item: Int, val previous: TagPathTrunk) extends TagPathTrunk with ItemIndex

  /**
    * Representation of the end of an item
    */
  class TagPathItemEnd private[TagPath](val tag: Int, val item: Int, val previous: TagPathTrunk) extends TagPath with ItemIndex

  /**
    * A tag path that points to a node that may be non-terminal, i.e. an item or the empty tag path. All other types end
    * a tag path; therefore builder functions reside in this trait.
    */
  trait TagPathTrunk extends TagPath {

    /**
      * Add a tag node
      *
      * @param tag tag number of new node (terminal)
      * @return the tag path
      */
    def thenTag(tag: Int) = new TagPathTag(tag, this)

    /**
      * Add a node pointing to the start of a sequence (terminal)
      *
      * @param tag tag number of sequence
      * @return the tag path
      */
    def thenSequence(tag: Int) = new TagPathSequence(tag, this)

    /**
      * Add a node pointing to the end of a sequence (terminal)
      *
      * @param tag tag number of sequence to end
      * @return the tag path
      */
    def thenSequenceEnd(tag: Int) = new TagPathSequenceEnd(tag, this)

    /**
      * Add a node pointing to an item in a sequence (non-terminal)
      *
      * @param tag  tag number of sequence item
      * @param item item number (1-based)
      * @return the tag path
      */
    def thenItem(tag: Int, item: Int) = new TagPathItem(tag, item, this)

    /**
      * Add a node pointing to the end of an item (terminal)
      *
      * @param tag  tag number of sequence item to end
      * @param item item number
      * @return the tag path
      */
    def thenItemEnd(tag: Int, item: Int) = new TagPathItemEnd(tag, item, this)
  }

  /**
    * Empty tag path
    */
  object EmptyTagPath extends TagPathTrunk {
    def tag: Int = throw new NoSuchElementException("Empty tag path")
    val previous: TagPathTrunk = EmptyTagPath
  }

  /**
    * Create a path to a specific tag (terminal)
    *
    * @param tag tag number
    * @return the tag path
    */
  def fromTag(tag: Int): TagPathTag = EmptyTagPath.thenTag(tag)

  /**
    * Create a path to the start of a sequence (terminal)
    *
    * @param tag tag number
    * @return the tag path
    */
  def fromSequence(tag: Int): TagPathSequence = EmptyTagPath.thenSequence(tag)

  /**
    * Create a path to the end of a sequence (terminal)
    *
    * @param tag tag number
    * @return the tag path
    */
  def fromSequenceEnd(tag: Int): TagPathSequenceEnd = EmptyTagPath.thenSequenceEnd(tag)

  /**
    * Create a path to a specific item within a sequence (non-terminal)
    *
    * @param tag tag number
    * @return the tag path
    */
  def fromItem(tag: Int, item: Int): TagPathItem = EmptyTagPath.thenItem(tag, item)

  /**
    * Create a path to the end of a sequence item (terminal)
    *
    * @param tag tag number
    * @return the tag path
    */
  def fromItemEnd(tag: Int, item: Int): TagPathItemEnd = EmptyTagPath.thenItemEnd(tag, item)

}
