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

case class ComponentGroup(alphabetic: String, ideographic: String, phonetic: String)

case class PersonName(
    familyName: ComponentGroup,
    givenName: ComponentGroup,
    middleName: ComponentGroup,
    prefix: ComponentGroup,
    suffix: ComponentGroup
) {
  override def toString: String = {
    val components = List(
      familyName,
      givenName,
      middleName,
      prefix,
      suffix
    )
    val representations = List(
      (c: ComponentGroup) => c.alphabetic,
      (c: ComponentGroup) => c.ideographic,
      (c: ComponentGroup) => c.phonetic
    )
    representations
      .map(repr =>
        components
          .map(repr)
          .mkString("^")
          .replaceAll("\\^+$", "") // Trim trailing ^ separators
      )
      .mkString("=")
      .replaceAll("=+$", "") // Trim trailing = separators
  }
}

object PersonName {
  def parse(s: String): Seq[PersonName] = Value.parsePN(s)
}
