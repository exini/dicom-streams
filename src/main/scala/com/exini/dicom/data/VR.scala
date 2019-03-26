/*
 * Copyright 2018 Lars Edenbrandt
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

import scala.language.implicitConversions

object VR extends Enumeration {
  protected final case class Val(code: Int, headerLength: Int, paddingByte: Byte) extends super.Val
  type VR = Val
  implicit def valueToVR(x: Value): VR = x.asInstanceOf[VR]

  final val AE = Val(0x4145, 8, ' ')
  final val AS = Val(0x4153, 8, ' ')
  final val AT = Val(0x4154, 8, 0)
  final val CS = Val(0x4353, 8, ' ')
  final val DA = Val(0x4441, 8, ' ')
  final val DS = Val(0x4453, 8, ' ')
  final val DT = Val(0x4454, 8, ' ')
  final val FD = Val(0x4644, 8, 0)
  final val FL = Val(0x464c, 8, 0)
  final val IS = Val(0x4953, 8, ' ')
  final val LO = Val(0x4c4f, 8, ' ')
  final val LT = Val(0x4c54, 8, ' ')
  final val OB = Val(0x4f42, 12, 0)
  final val OD = Val(0x4f44, 12, 0)
  final val OF = Val(0x4f46, 12, 0)
  final val OL = Val(0x4f4c, 12, 0)
  final val OW = Val(0x4f57, 12, 0)
  final val PN = Val(0x504e, 8, ' ')
  final val SH = Val(0x5348, 8, ' ')
  final val SL = Val(0x534c, 8, 0)
  final val SQ = Val(0x5351, 12, 0)
  final val SS = Val(0x5353, 8, 0)
  final val ST = Val(0x5354, 8, ' ')
  final val TM = Val(0x544d, 8, ' ')
  final val UC = Val(0x5543, 12, ' ')
  final val UI = Val(0x5549, 8, 0)
  final val UL = Val(0x554c, 8, 0)
  final val UN = Val(0x554e, 12, 0)
  final val UR = Val(0x5552, 12, ' ')
  final val US = Val(0x5553, 8, 0)
  final val UT = Val(0x5554, 12, ' ')

  private lazy final val valueOfMap: Map[Int, Value] = VR.values.map(vr => vr.code -> vr).toMap
  def valueOf(code: Int): VR = try valueOfMap(code) catch { case _: Throwable => null }
}
