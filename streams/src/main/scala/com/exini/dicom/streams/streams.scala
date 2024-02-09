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

package com.exini.dicom

import com.exini.dicom.data.DicomParts.DicomPart
import com.exini.dicom.data._
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{ Flow, Source }
import org.apache.pekko.util.ByteString

import scala.concurrent.duration.{ DurationInt, FiniteDuration }
import scala.concurrent.{ Await, ExecutionContext, Future }

package object streams {

  import ElementFlows.elementFlow
  import ElementSink.elementSink
  import ParseFlow.parseFlow

  type PartFlow = Flow[DicomPart, DicomPart, NotUsed]

  final val partFlow: PartFlow = Flow[DicomPart]

  def toElements(source: Source[ByteString, Any])(implicit ec: ExecutionContext, mat: Materializer): Future[Elements] =
    source
      .via(parseFlow)
      .via(elementFlow)
      .runWith(elementSink)

  def toElementsBlocking(source: Source[ByteString, Any], d: FiniteDuration = 10.seconds)(implicit
      ec: ExecutionContext,
      mat: Materializer
  ): Elements =
    Await.result(toElements(source), d)

  implicit class RichByteString(val s: ByteString) extends AnyVal {
    def toBytes: Bytes = s.toArrayUnsafe().wrap
  }

  implicit class BytesWithByteStringSupport(val bytes: Bytes) extends AnyVal {
    def toByteString: ByteString = ByteString(bytes)
  }
}
