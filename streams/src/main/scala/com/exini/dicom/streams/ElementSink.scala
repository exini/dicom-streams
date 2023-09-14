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

package com.exini.dicom.streams

import akka.stream.scaladsl.{ Flow, Keep, Sink }
import com.exini.dicom.data.DicomElements.Element
import com.exini.dicom.data.{ Elements, ElementsBuilder }

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Flow, Sink etc that combine DICOM parts into data element aggregates.
  */
object ElementSink {

  /**
    * A `Sink` that combines data elements into an `Elements` structure. If the `SpecificCharacterSet` element occurs,
    * the character sets of the `Elements` structure is updated accordingly. If the `TimezoneOffsetFromUTC` element
    * occurs, the zone offset is updated accordingly.
    */
  def elementSink(implicit ec: ExecutionContext): Sink[Element, Future[Elements]] =
    Flow[Element]
      .toMat(
        Sink
          .fold[ElementsBuilder, Element](Elements.newBuilder()) { case (builder, element) => builder += element }
          .mapMaterializedValue(_.map(_.build()))
      )(Keep.right)
}
