package com.exini.dicom.streams

import com.exini.dicom.data.ByteParser
import com.exini.dicom.data.ByteParser.{ ByteParserTarget, ByteReader, ParseStep }
import org.apache.pekko.stream._
import org.apache.pekko.stream.stage._
import org.apache.pekko.util.ByteString

abstract class ByteParserFlow[T] extends GraphStage[FlowShape[ByteString, T]] {

  protected val bytesIn: Inlet[ByteString] = Inlet[ByteString]("bytesIn")
  protected val objOut: Outlet[T]          = Outlet[T]("objOut")

  override def initialAttributes: Attributes = Attributes.name("ByteParserFlow")

  final override val shape = FlowShape(bytesIn, objOut)

  class ParsingLogic extends GraphStageLogic(shape) with InHandler with OutHandler with ByteParserTarget[T] {
    protected def isUpstreamClosed: Boolean = isClosed(bytesIn)

    override def next(result: T): Unit =
      push(objOut, result)

    override def fail(ex: Throwable): Unit =
      failStage(ex)

    override def complete(): Unit =
      completeStage()

    override def needMoreData(current: ParseStep[T], reader: ByteReader, acceptNoMoreDataAvailable: Boolean): Unit =
      if (isClosed(bytesIn))
        if (acceptNoMoreDataAvailable) completeStage()
        else current.onTruncation(reader)
      else
        pull(bytesIn)

    protected val parser: ByteParser[T] = new ByteParser[T](this)

    override def onPull(): Unit =
      parser.parse()

    def onPush(): Unit = {
      val chunk = grab(bytesIn)
      parser ++= chunk.toBytes
      parser.parse()
    }

    override def onUpstreamFinish(): Unit =
      if (isAvailable(objOut))
        parser.parse()

    protected def startWith(step: ParseStep[T]): Unit = parser.startWith(step)

    setHandlers(bytesIn, objOut, this)
  }

}
