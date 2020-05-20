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

import enumeratum.values.{ IntEnum, IntEnumEntry }

sealed abstract class VR(val value: Int, val headerLength: Int, val paddingByte: Byte) extends IntEnumEntry

object VR extends IntEnum[VR] {

  val values = findValues

  case object AE extends VR(0x4145, 8, ' ')
  case object AS extends VR(0x4153, 8, ' ')
  case object AT extends VR(0x4154, 8, 0)
  case object CS extends VR(0x4353, 8, ' ')
  case object DA extends VR(0x4441, 8, ' ')
  case object DS extends VR(0x4453, 8, ' ')
  case object DT extends VR(0x4454, 8, ' ')
  case object FD extends VR(0x4644, 8, 0)
  case object FL extends VR(0x464c, 8, 0)
  case object IS extends VR(0x4953, 8, ' ')
  case object LO extends VR(0x4c4f, 8, ' ')
  case object LT extends VR(0x4c54, 8, ' ')
  case object OB extends VR(0x4f42, 12, 0)
  case object OD extends VR(0x4f44, 12, 0)
  case object OF extends VR(0x4f46, 12, 0)
  case object OL extends VR(0x4f4c, 12, 0)
  case object OV extends VR(0x4f56, 12, 0)
  case object OW extends VR(0x4f57, 12, 0)
  case object PN extends VR(0x504e, 8, ' ')
  case object SH extends VR(0x5348, 8, ' ')
  case object SL extends VR(0x534c, 8, 0)
  case object SQ extends VR(0x5351, 12, 0)
  case object SS extends VR(0x5353, 8, 0)
  case object ST extends VR(0x5354, 8, ' ')
  case object SV extends VR(0x5356, 12, 0)
  case object TM extends VR(0x544d, 8, ' ')
  case object UC extends VR(0x5543, 12, ' ')
  case object UI extends VR(0x5549, 8, 0)
  case object UL extends VR(0x554c, 8, 0)
  case object UN extends VR(0x554e, 12, 0)
  case object UR extends VR(0x5552, 12, ' ')
  case object US extends VR(0x5553, 8, 0)
  case object UT extends VR(0x5554, 12, ' ')
  case object UV extends VR(0x5556, 12, 0)
}
