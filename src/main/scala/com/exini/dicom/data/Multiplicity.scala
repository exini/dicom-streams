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

case class Multiplicity(min: Int, max: Option[Int]) {
  def isSingle: Boolean    = this == Multiplicity.single
  def isMultiple: Boolean  = !isSingle
  def isBounded: Boolean   = max.isDefined
  def isUnbounded: Boolean = !isBounded
}

object Multiplicity {
  def fixed(value: Int)             = Multiplicity(value, Some(value))
  def bounded(min: Int, max: Int)   = Multiplicity(min, Some(max))
  def unbounded(min: Int)           = Multiplicity(min, None)
  final val single: Multiplicity    = fixed(1)
  final val oneToMany: Multiplicity = unbounded(1)
}
