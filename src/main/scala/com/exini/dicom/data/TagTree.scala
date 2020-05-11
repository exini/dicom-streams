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
import com.exini.dicom.data.TagTree._

import scala.annotation.tailrec

/**
  * Representation of a tag path with support for pointing to all items in sequences, thus forming a tree of tag paths.
  *
  * The nomenclature of tag trees is as follows. The left-most node is called the <i>root</i>. The right-most node is a
  * <i>leaf</i>. A branch extending from a root to a leaf is a <i>path</i>. A branch starting at the root and
  * extends to some point in the tree, not necessarily a leaf, is called a <i>trunk</i>. A branch starting from any point
  * in the tree and extends to a leaf is called a <i>twig</i>.
  */
sealed trait TagTree extends TagPathLike {

  override type P = TagTree
  override type T = TagTreeTrunk
  override type E = EmptyTagTree.type

  override protected val empty: E = EmptyTagTree

  /**
    * @param that tag tree to test
    * @return `true` if the input tag tree is equal to this tag tree. TagTree nodes are compared pairwise from the end
    *         towards the start of the paths. Node types, tag numbers as well as item indices where applicable must be
    *         equal. Paths of different lengths cannot be equal.
    * @example (0010,0010) == (0010,0010)
    * @example (0010,0010) != (0010,0020)
    * @example (0010,0010) != (0008,9215)[1].(0010,0010)
    * @example (0008,9215)[*].(0010,0010) != (0008,9215)[1].(0010,0010)
    * @example (0008,9215)[3].(0010,0010) == (0008,9215)[3].(0010,0010)
    */
  override def equals(that: Any): Boolean =
    (this, that) match {
      case (t: TagTree, p: TagTree) if t.isEmpty && p.isEmpty => true
      case (t: TagTreeTag, p: TagTreeTag)                     => t.tag == p.tag && t.previous == p.previous
      case (t: TagTreeItem, p: TagTreeItem)                   => t.tag == p.tag && t.item == p.item && t.previous == p.previous
      case (t: TagTreeAnyItem, p: TagTreeAnyItem)             => t.tag == p.tag && t.previous == p.previous
      case _                                                  => false
    }

  /**
    * @return `true` if this tree does not contain any wildcard (all-items) pointers and is thus structurally equivalent
    *         to a tag path
    * @example (0008,9215)[3].(0010,0010) is a path
    * @example (0008,9215)[*].(0010,0010) is not a path
    * @example the empty tag tree is a path
    */
  def isPath: Boolean =
    this match {
      case EmptyTagTree      => true
      case _: TagTreeAnyItem => false
      case t                 => t.previous.isPath
    }

  /**
    * @param tagPath tag path to test
    * @return `true` if the input tag path is a path from root to leaf in this tree
    * @example (0008,9215)[1].(0010,0010) is a path in (0008,9215)[*].(0010,0010)
    * @example (0008,9215)[1] is not a path in (0008,9215)[*].(0010,0010)
    * @example the empty tag path is a path in the empty tag tree
    */
  def hasPath(tagPath: TagPath): Boolean =
    (this, tagPath) match {
      case (EmptyTagTree, EmptyTagPath)   => true
      case (t: TagTreeTag, p: TagPathTag) => t.tag == p.tag && t.previous.hasPath(p.previous)
      case (t: TagTreeItem, p: TagPath with ItemIndex) =>
        t.item == p.item && t.tag == p.tag && t.previous.hasPath(p.previous)
      case (t: TagTreeAnyItem, p: TagPath with ItemIndex) => t.tag == p.tag && t.previous.hasPath(p.previous)
      case (t: TagTreeAnyItem, p: TagPathSequence)        => t.tag == p.tag && t.previous.hasPath(p.previous)
      case (t: TagTreeAnyItem, p: TagPathSequenceEnd)     => t.tag == p.tag && t.previous.hasPath(p.previous)
      case _                                              => false
    }

  /**
    * @param tagPath tag path to test
    * @return `true` if the input tag path is a trunk of this tree
    * @example (0008,9215)[1].(0010,0010) is a trunk of (0008,9215)[*].(0010,0010)
    * @example (0008,9215)[1] is a trunk of (0008,9215)[*].(0010,0010)
    * @example (0010,0010) is not a trunk of (0008,9215)[*].(0010,0010)
    */
  def hasTrunk(tagPath: TagPath): Boolean =
    if (this.depth >= tagPath.depth)
      this.toList.zip(tagPath.toList).forall {
        case (_, EmptyTagPath)                              => true
        case (t: TagTreeItem, p: TagPath with ItemIndex)    => t.tag == p.tag && t.item == p.item
        case (t: TagTreeItem, p: TagPathSequence)           => t.tag == p.tag
        case (t: TagTreeItem, p: TagPathSequenceEnd)        => t.tag == p.tag
        case (t: TagTreeAnyItem, p: TagPath with ItemIndex) => t.tag == p.tag
        case (t: TagTreeAnyItem, p: TagPathSequence)        => t.tag == p.tag
        case (t: TagTreeAnyItem, p: TagPathSequenceEnd)     => t.tag == p.tag
        case (t: TagTreeTag, p: TagPathTag)                 => t.tag == p.tag
        case _                                              => false
      }
    else
      false

  /**
    * @param tagPath tag path to test
    * @return `true` if any path of this tree is equal to or the start of the input tag path
    * @example (0008,9215)[1].(0010,0010) has trunk (0008,9215)[1].(0010,0010)
    * @example (0008,9215)[1].(0010,0010) has trunk (0008,9215)[*].(0010,0010)
    * @example (0008,9215)[1].(0010,0010) has trunk (0008,9215)[*]
    * @example (0008,9215)[1].(0010,0010) does not have trunk (0008,9215)[3]
    * @example (0008,9215)[1].(0010,0010) does not have trunk (0010,0010)
    */
  def isTrunkOf(tagPath: TagPath): Boolean =
    if (this.depth <= tagPath.depth)
      this.toList.zip(tagPath.toList).forall {
        case (_, EmptyTagPath)                              => true
        case (t: TagTreeItem, p: TagPath with ItemIndex)    => t.tag == p.tag && t.item == p.item
        case (t: TagTreeAnyItem, p: TagPath with ItemIndex) => t.tag == p.tag
        case (t: TagTreeAnyItem, p: TagPathSequence)        => t.tag == p.tag
        case (t: TagTreeAnyItem, p: TagPathSequenceEnd)     => t.tag == p.tag
        case (t: TagTreeTag, p: TagPathTag)                 => t.tag == p.tag
        case _                                              => false
      }
    else
      false

  /**
    * @param tagPath tag path to test
    * @return `true` if the input tag path is a twig in this tree
    * @example (0008,9215)[1].(0010,0010) is a twig of (0008,9215)[*].(0010,0010)
    * @example (0010,0010) is a twig of (0008,9215)[*].(0010,0010)
    * @example (0008,9215)[1] is not a twig of (0008,9215)[*].(0010,0010)
    */
  def hasTwig(tagPath: TagPath): Boolean =
    ((this, tagPath) match {
      case (EmptyTagTree, EmptyTagPath)                   => true
      case (t: TagTreeAnyItem, p: TagPath with ItemIndex) => t.tag == p.tag
      case (t: TagTreeAnyItem, p: TagPathSequence)        => t.tag == p.tag
      case (t: TagTreeAnyItem, p: TagPathSequenceEnd)     => t.tag == p.tag
      case (t: TagTreeItem, p: TagPath with ItemIndex)    => t.tag == p.tag && t.item == p.item
      case (t: TagTreeTag, p: TagPathTag)                 => t.tag == p.tag
      case _                                              => false
    }) && ((this.previous, tagPath.previous) match {
      case (_, EmptyTagPath)    => true
      case (EmptyTagTree, _)    => false
      case (thisPrev, thatPrev) => thisPrev.hasTwig(thatPrev)
    })

  def drop(n: Int): TagTree = {
    def drop(tree: TagTree, i: Int): TagTree =
      if (i < 0)
        EmptyTagTree
      else if (i == 0)
        tree match {
          case EmptyTagTree      => EmptyTagTree
          case p: TagTreeItem    => TagTree.fromItem(p.tag, p.item)
          case p: TagTreeAnyItem => TagTree.fromAnyItem(p.tag)
          case p                 => TagTree.fromTag(p.tag)
        }
      else
        drop(tree.previous, i - 1) match {
          case p: TagTreeTrunk =>
            tree match {
              case pi: TagTreeItem    => p.thenItem(pi.tag, pi.item)
              case pi: TagTreeAnyItem => p.thenAnyItem(pi.tag)
              case pt: TagTreeTag     => p.thenTag(pt.tag)
              case _                  => EmptyTagTree
            }
          case _ => EmptyTagTree // cannot happen
        }

    drop(tree = this, i = depth - n - 1)
  }

  def toString(lookup: Boolean): String = {
    def toTagString(tag: Int): String =
      if (lookup)
        Lookup.keywordOf(tag).getOrElse(tagToString(tag))
      else
        tagToString(tag)

    @tailrec
    def toTagTreeString(tree: TagTree, tail: String): String = {
      val itemIndexSuffix = tree match {
        case _: TagTreeAnyItem => "[*]"
        case s: TagTreeItem    => s"[${s.item}]"
        case _                 => ""
      }
      val head = toTagString(tree.tag) + itemIndexSuffix
      val part = head + tail
      if (tree.isRoot) part else toTagTreeString(tree.previous, "." + part)
    }

    if (isEmpty) "<empty tree>" else toTagTreeString(tree = this, tail = "")
  }

  override def hashCode(): Int =
    this match {
      case EmptyTagTree   => 0
      case s: TagTreeItem => 31 * (31 * (31 * previous.hashCode() + tag.hashCode()) * s.item.hashCode())
      case _              => 31 * (31 * previous.hashCode() + tag.hashCode())
    }
}

object TagTree {

  /**
    * A tag tree that points to a non-sequence tag (terminal)
    */
  class TagTreeTag private[TagTree] (val tag: Int, val previous: TagTreeTrunk) extends TagTree

  /**
    * A tag tree that points to a node that may be non-terminal, i.e. an item or the empty tag tree. All other types end
    * a tag tree; therefore builder functions reside in this trait.
    */
  trait TagTreeTrunk extends TagTree {

    /**
      * Add a tag node
      *
      * @param tag tag number of new node (terminal)
      * @return the tag tree
      */
    def thenTag(tag: Int) = new TagTreeTag(tag, this)

    /**
      * Add a node pointing to all items of a sequence (non-terminal)
      *
      * @param tag tag number of sequence
      * @return the tag tree
      */
    def thenAnyItem(tag: Int) = new TagTreeAnyItem(tag, this)

    /**
      * Add a node pointing to specific item in a sequence (non-terminal)
      *
      * @param tag tag number of sequence
      * @return the tag tree
      */
    def thenItem(tag: Int, item: Int) = new TagTreeItem(tag, item, this)
  }

  /**
    * A tag tree node that points to all items of a sequence (non-terminal)
    */
  class TagTreeAnyItem private[TagTree] (val tag: Int, val previous: TagTreeTrunk) extends TagTreeTrunk

  /**
    * A tag tree that points to an item in a sequence (non-terminal)
    */
  class TagTreeItem private[TagTree] (val tag: Int, val item: Int, val previous: TagTreeTrunk) extends TagTreeTrunk

  /**
    * Empty tag tree
    */
  object EmptyTagTree extends TagTreeTrunk {
    def tag: Int               = throw new NoSuchElementException("Empty tag tree")
    val previous: TagTreeTrunk = EmptyTagTree
  }

  /**
    * Create a tree node representing a specific tag (terminal)
    *
    * @param tag tag number
    * @return the tag tree
    */
  def fromTag(tag: Int): TagTreeTag = EmptyTagTree.thenTag(tag)

  /**
    * Create a tree node representing all items in a sequence (non-terminal)
    *
    * @param tag tag number
    * @return the tag tree
    */
  def fromAnyItem(tag: Int): TagTreeAnyItem = EmptyTagTree.thenAnyItem(tag)

  /**
    * Create a tree node representing a specific item within a sequence (non-terminal)
    *
    * @param tag  tag number
    * @param item item index
    * @return the tag tree
    */
  def fromItem(tag: Int, item: Int): TagTreeItem = EmptyTagTree.thenItem(tag, item)

  /**
    * Create a tag tree from a tag path
    *
    * @param tagPath input tag path
    * @return the equivalent (path-shaped) tag tree
    */
  def fromPath(tagPath: TagPath): TagTree = {
    val root = tagPath.head match {
      case p: TagPathTag         => TagTree.fromTag(p.tag)
      case p: TagPathItem        => TagTree.fromItem(p.tag, p.item)
      case p: TagPathItemEnd     => TagTree.fromItem(p.tag, p.item)
      case p: TagPathSequence    => TagTree.fromAnyItem(p.tag)
      case p: TagPathSequenceEnd => TagTree.fromAnyItem(p.tag)
      case _                     => EmptyTagTree
    }
    tagPath.drop(1).toList.foldLeft(root) {
      case (t: TagTreeTrunk, p: TagPathTag)         => t.thenTag(p.tag)
      case (t: TagTreeTrunk, p: TagPathItem)        => t.thenItem(p.tag, p.item)
      case (t: TagTreeTrunk, p: TagPathItemEnd)     => t.thenItem(p.tag, p.item)
      case (t: TagTreeTrunk, p: TagPathSequence)    => t.thenAnyItem(p.tag)
      case (t: TagTreeTrunk, p: TagPathSequenceEnd) => t.thenAnyItem(p.tag)
      case (t, _)                                   => t
    }
  }

  /**
    * Parse the string representation of a tag tree into a tag tree object. Tag trees can either be specified using tag
    * numbers or their corresponding keywords.
    *
    * Examples: (0008,9215)[*].(0010,0010) = DerivationCodeSequence[*].(0010,0010) = (0008,9215)[*].PatientName =
    * DerivationCodeSequence[1].PatientName
    *
    * @param s string to parse
    * @return a tag tree
    * @throws IllegalArgumentException for malformed input
    */
  def parse(s: String): TagTree = {
    def isSeq(s: String): Boolean = s.last == ']'

    def indexPart(s: String): String = s.substring(s.lastIndexOf('[') + 1, s.length - 1)

    def tagPart(s: String): String = s.substring(0, s.indexOf('['))

    def parseTag(s: String): Int =
      try Integer.parseInt(s.substring(1, 5) + s.substring(6, 10), 16)
      catch {
        case _: Throwable =>
          Lookup.tagOf(s)
      }

    def parseIndex(s: String): Option[Int] = if (s == "*") None else Some(Integer.parseInt(s))

    def createTag(s: String): TagTreeTag = TagTree.fromTag(parseTag(s))

    def addTag(s: String, tree: TagTreeTrunk): TagTreeTag = tree.thenTag(parseTag(s))

    def createSeq(s: String): TagTreeTrunk =
      parseIndex(indexPart(s))
        .map(index => TagTree.fromItem(parseTag(tagPart(s)), index))
        .getOrElse(TagTree.fromAnyItem(parseTag(tagPart(s))))

    def addSeq(s: String, tree: TagTreeTrunk): TagTreeTrunk =
      parseIndex(indexPart(s))
        .map(index => tree.thenItem(parseTag(tagPart(s)), index))
        .getOrElse(tree.thenAnyItem(parseTag(tagPart(s))))

    val tags    = if (s.indexOf('.') > 0) s.split("\\.").toList else List(s)
    val seqTags = if (tags.length > 1) tags.init else Nil // list of sequence tags, if any
    val lastTag = tags.last                               // tag or sequence
    try seqTags.headOption.map(first =>
      seqTags.tail.foldLeft(createSeq(first))((tree, tag) => addSeq(tag, tree))
    ) match {
      case Some(tree) => if (isSeq(lastTag)) addSeq(lastTag, tree) else addTag(lastTag, tree)
      case None       => if (isSeq(lastTag)) createSeq(lastTag) else createTag(lastTag)
    } catch {
      case e: Exception => throw new IllegalArgumentException("Tag tree could not be parsed", e)
    }
  }

}
