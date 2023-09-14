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

import scala.annotation.tailrec

/**
  * Common functionality in tag path-like constructs such as TagPath itself and TagTree
  */
trait TagPathLike {

  type P <: TagPathLike
  type T <: P
  type E <: T

  protected val empty: E

  /**
    * @return tag value at this position of the tag path
    */
  def tag: Int

  /**
    * @return link to previous position along this tag path. If current position is at the root, the previous tag path
    *         is the empty tag path.
    */
  def previous: T

  /**
    * `true` if this tag path points to the root dataset, at depth 1
    */
  lazy val isRoot: Boolean = previous.isEmpty

  /**
    * `true` if this is the empty tag path
    */
  lazy val isEmpty: Boolean = this eq empty

  /**
    * Decompose the linked-list tag path structure to a regular list of tag paths ordered from root to leaf
    *
    * @return a List of tag paths
    */
  def toList: List[P] = {
    @tailrec
    def toList(path: P, tail: List[P]): List[P] =
      if (path.isRoot) path :: tail else toList(path.previous.asInstanceOf[P], path :: tail)

    toList(this.asInstanceOf[P], Nil)
  }

  /**
    * @param tag tag number
    * @return `true` if this tag path contains the input tag number
    * @example (0008,9215)[*].(0010,0010) contains 0x00089215
    * @example (0008,9215)[*].(0010,0010) contains 0x00100010
    * @example (0008,9215)[*].(0010,0010) does not contain 0x00100020
    */
  def contains(tag: Int): Boolean = toList.map(_.tag).contains(tag)

  /**
    * Depth of this tag path. A tag path that points to a tag in a sequence in a sequence has depth 3. A tag path that
    * points to a tag in the root dataset has depth 1. Empty paths have depth 0.
    *
    * @return the depth of this tag path, counting from 1
    */
  def depth: Int = {
    @tailrec
    def depth(path: P, d: Int): Int = if (path.isRoot) d else depth(path.previous.asInstanceOf[P], d + 1)

    if (isEmpty) 0 else depth(this.asInstanceOf[P], 1)
  }

  /**
    * @return the root (left-most) element of this tag path
    */
  def head: P = take(1)

  /**
    * @return the tag path following the root element, that is, all elements to the right of the root element. Returns
    *         empty path if there are no such elements.
    */
  def tail: P = drop(1)

  /**
    * The first n steps of this path, counted from the left
    *
    * @param n the number of steps to take, counted from the left-most root path
    * @return a new TagPath
    */
  def take(n: Int): P = {
    @tailrec
    def take(path: P, i: Int): P =
      if (i <= 0) path else take(path.previous.asInstanceOf[P], i - 1)

    take(path = this.asInstanceOf[P], i = depth - n)
  }

  /**
    * Drop n steps of this path from the left
    *
    * @param n the number of steps to omit, counted from the lef-most root path
    * @return a new TagPath
    */
  def drop(n: Int): P

  /**
    * Create a string representation of this tag path
    *
    * @param lookup if `true`, tag numbers will be replaced by keywords if found in the dictionary
    * @return a string representation of this tag path
    */
  def toString(lookup: Boolean): String

  override def toString: String = toString(lookup = true)

}
