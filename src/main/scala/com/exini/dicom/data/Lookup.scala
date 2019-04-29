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

import com.exini.dicom.data.VR.VR

object Lookup {

  def vrOf(tag: Int): VR = TagToVR.vrOf(tag)

  def vmOf(tag: Int): Multiplicity = TagToVM.vmOf(tag)

  def keywordOf(tag: Int): String = TagToKeyword.keywordOf(tag)

  def tagOf(keyword: String): Int = KeywordToTag.tagOf(keyword)

  def keywords(): List[String] = Tag.getClass.getMethods.map(_.getName).filter(_.head.isUpper).toList
}
