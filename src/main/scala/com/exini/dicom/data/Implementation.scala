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

import com.exini.dicom.BuildInfo

object Implementation {

  val uidPrefix: String = "1.2.826.0.1.3680043.9.7634"
  val name: String = BuildInfo.name
  val version: String = BuildInfo.version
  val versionName: String = s"${name}_$version"
  val classUid: String = s"$uidPrefix.1.${version.replaceAll("""[^0-9\.]""", "")}"
}
