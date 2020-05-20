package com.exini.dicom.streams

import java.util.zip.Deflater

import akka.stream.testkit.TestSubscriber
import akka.util.ByteString
import com.exini.dicom.data.DicomParts._
import com.exini.dicom.data.Elements._
import com.exini.dicom.data.VR.VR
import com.exini.dicom.data._

object TestUtils {

  def deflate(bytes: ByteString, gzip: Boolean = false): ByteString = {
    val deflater = if (gzip) new Deflater() else new Deflater(-1, true)
    deflater.setInput(bytes.toArray)
    val buffer = new Array[Byte](bytes.length)
    var out    = ByteString.empty
    var n      = 1
    while (n > 0) {
      n = deflater.deflate(buffer, 0, buffer.length, Deflater.FULL_FLUSH)
      out = out ++ ByteString.fromArray(buffer.take(n))
    }
    out
  }

  case class TestPart(id: String) extends MetaPart {
    override def toString = s"${getClass.getSimpleName}: $id"
  }

  type PartProbe = TestSubscriber.Probe[DicomPart]

  implicit class DicomPartProbe(probe: PartProbe) {
    def expectPreamble(): PartProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case _: PreamblePart => true
          case p               => throw new RuntimeException(s"Expected PreamblePart, got $p")
        }

    def expectValueChunk(): PartProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case _: ValueChunk => true
          case p             => throw new RuntimeException(s"Expected ValueChunk, got $p")
        }

    def expectValueChunk(length: Int): PartProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case chunk: ValueChunk if chunk.bytes.length == length => true
          case p                                                 => throw new RuntimeException(s"Expected ValueChunk with length = $length, got $p")
        }

    def expectValueChunk(bytes: ByteString): PartProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case chunk: ValueChunk if chunk.bytes == bytes => true
          case chunk: ValueChunk =>
            throw new RuntimeException(s"Expected ValueChunk with bytes = $bytes, got $chunk with bytes ${chunk.bytes}")
          case p => throw new RuntimeException(s"Expected ValueChunk with bytes = $bytes, got $p")
        }

    def expectItem(index: Int): PartProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case item: ItemPart if item.index == index => true
          case p                                     => throw new RuntimeException(s"Expected ItemPart with index = $index, got $p")
        }

    def expectItem(index: Int, length: Int): PartProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case item: ItemPart if item.index == index && item.length == length => true
          case p                                                              => throw new RuntimeException(s"Expected ItemPart with index = $index and length $length, got $p")
        }

    def expectItemDelimitation(): PartProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case _: ItemDelimitationPart => true
          case p                       => throw new RuntimeException(s"Expected ItemDelimitationPart, got $p")
        }

    def expectFragments(): PartProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case _: FragmentsPart => true
          case p                => throw new RuntimeException(s"Expected FragmentsPart, got $p")
        }

    def expectFragment(index: Int, length: Int): PartProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case item: ItemPart if item.index == index && item.length == length => true
          case p                                                              => throw new RuntimeException(s"Expected FragmentsPart with index = $index and length $length, got $p")
        }

    def expectFragmentsDelimitation(): PartProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case _: SequenceDelimitationPart => true
          case p                           => throw new RuntimeException(s"Expected SequenceDelimitationPart, got $p")
        }

    def expectHeader(tag: Int): PartProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case h: HeaderPart if h.tag == tag => true
          case p                             => throw new RuntimeException(s"Expected HeaderPart with tag = ${tagToString(tag)}, got $p")
        }

    def expectHeader(tag: Int, vr: VR, length: Long): PartProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case h: HeaderPart if h.tag == tag && h.vr == vr && h.length == length => true
          case p =>
            throw new RuntimeException(
              s"Expected HeaderPart with tag = ${tagToString(tag)}, VR = $vr and length = $length, got $p"
            )
        }

    def expectSequence(tag: Int): PartProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case h: SequencePart if h.tag == tag => true
          case p                               => throw new RuntimeException(s"Expected SequencePart with tag = ${tagToString(tag)}, got $p")
        }

    def expectSequence(tag: Int, length: Int): PartProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case h: SequencePart if h.tag == tag && h.length == length => true
          case p =>
            throw new RuntimeException(
              s"Expected SequencePart with tag = ${tagToString(tag)} and length = $length, got $p"
            )
        }

    def expectSequenceDelimitation(): PartProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case _: SequenceDelimitationPart => true
          case p                           => throw new RuntimeException(s"Expected SequenceDelimitationPart, got $p")
        }

    def expectUnknownPart(): PartProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case _: UnknownPart => true
          case p              => throw new RuntimeException(s"Expected UnkownPart, got $p")
        }

    def expectDeflatedChunk(): PartProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case _: DeflatedChunk => true
          case p                => throw new RuntimeException(s"Expected DeflatedChunk, got $p")
        }

    def expectDicomComplete(): PartProbe =
      probe
        .request(1)
        .expectComplete()

    def expectDicomError(): Throwable =
      probe
        .request(1)
        .expectError()

    def expectElements(): PartProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case _: ElementsPart => true
          case p               => throw new RuntimeException(s"Expected ElementsPart, got $p")
        }

    def expectElements(elementsPart: ElementsPart): PartProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case p: ElementsPart if p == elementsPart => true
          case p                                    => throw new RuntimeException(s"Expected ElementsPart with part = $elementsPart, got $p")
        }

    def expectTestPart(id: String): PartProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case a: TestPart if a.id == id => true
          case p                         => throw new RuntimeException(s"Expected TestPart with id = $id, got $p")
        }
  }

  type ElementProbe = TestSubscriber.Probe[Element]

  implicit class DicomElementProbe(probe: ElementProbe) {
    def expectElement(tag: Int): ElementProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case e: ValueElement if e.tag == tag     => true
          case e: SequenceElement if e.tag == tag  => true
          case e: FragmentsElement if e.tag == tag => true
          case p                                   => throw new RuntimeException(s"Expected Element with tag $tag, got $p")
        }

    def expectElement(tag: Int, value: ByteString): ElementProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case e: ValueElement if e.tag == tag && e.value.bytes == value => true
          case p                                                         => throw new RuntimeException(s"Expected Element with tag $tag and value $value, got $p")
        }

    def expectFragments(tag: Int): ElementProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case e: FragmentsElement if e.tag == tag => true
          case p                                   => throw new RuntimeException(s"Expected Fragments with tag $tag, got $p")
        }

    def expectFragment(length: Long): ElementProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case e: FragmentElement if e.length == length => true
          case p                                        => throw new RuntimeException(s"Expected Fragment with length $length, got $p")
        }

    def expectSequence(tag: Int, length: Long): ElementProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case e: SequenceElement if e.tag == tag && e.length == length => true
          case p                                                        => throw new RuntimeException(s"Expected SequenceElement, got $p")
        }

    def expectItem(index: Int, length: Long): ElementProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case e: ItemElement if e.index == index && e.length == length => true
          case p                                                        => throw new RuntimeException(s"Expected ItemElement, got $p")
        }

    def expectItemDelimitation(index: Int): ElementProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case e: ItemDelimitationElement if e.index == index => true
          case p                                              => throw new RuntimeException(s"Expected ItemDelimitationElement, got $p")
        }

    def expectSequenceDelimitation(): ElementProbe =
      probe
        .request(1)
        .expectNextChainingPF {
          case _: SequenceDelimitationElement => true
          case p                              => throw new RuntimeException(s"Expected SequencedDelimitationElement, got $p")
        }

    def expectDicomComplete(): ElementProbe =
      probe
        .request(1)
        .expectComplete()
  }

}
