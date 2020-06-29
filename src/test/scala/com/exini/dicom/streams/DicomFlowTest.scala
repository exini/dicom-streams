package com.exini.dicom.streams

import java.io.File

import akka.actor.ActorSystem
import akka.stream.Attributes
import akka.stream.scaladsl.{ FileIO, Flow, Sink, Source }
import akka.stream.stage.GraphStageLogic
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestKit
import akka.util.ByteString
import com.exini.dicom.data.TagPath.EmptyTagPath
import com.exini.dicom.data.{ Tag, TagPath, _ }
import com.exini.dicom.streams.ParseFlow.parseFlow
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ Await, ExecutionContextExecutor }

class DicomFlowTest
    extends TestKit(ActorSystem("DicomFlowSpec"))
    with AnyFlatSpecLike
    with Matchers
    with BeforeAndAfterAll {

  import DicomFlows._
  import TestUtils._
  import com.exini.dicom.data.DicomParts._
  import com.exini.dicom.data.TestData._

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  override def afterAll(): Unit = system.terminate()

  "The dicom flow" should "call the correct events for streamed dicom parts" in {
    val bytes = preamble ++ fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++
      personNameJohnDoe() ++ sequence(
      Tag.DerivationCodeSequence
    ) ++ item() ++ studyDate() ++ itemDelimitation() ++ sequenceDelimitation() ++
      pixeDataFragments() ++ item(4) ++ ByteString(1, 2, 3, 4) ++ sequenceDelimitation()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(new DicomFlow[DicomPart] {
        override def createLogic(attr: Attributes): GraphStageLogic =
          new DicomLogic {
            override def onFragments(part: FragmentsPart): List[DicomPart] = TestPart("Fragments Start") :: Nil
            override def onHeader(part: HeaderPart): List[DicomPart]       = TestPart("Header") :: Nil
            override def onPreamble(part: PreamblePart): List[DicomPart]   = TestPart("Preamble") :: Nil
            override def onSequenceDelimitation(part: SequenceDelimitationPart): List[DicomPart] =
              TestPart("Sequence End") :: Nil
            override def onItemDelimitation(part: ItemDelimitationPart): List[DicomPart] = TestPart("Item End") :: Nil
            override def onItem(part: ItemPart): List[DicomPart]                         = TestPart("Item Start") :: Nil
            override def onSequence(part: SequencePart): List[DicomPart]                 = TestPart("Sequence Start") :: Nil
            override def onValueChunk(part: ValueChunk): List[DicomPart]                 = TestPart("Value Chunk") :: Nil
            override def onDeflatedChunk(part: DeflatedChunk): List[DicomPart]           = Nil
            override def onUnknown(part: UnknownPart): List[DicomPart]                   = Nil
            override def onPart(part: DicomPart): List[DicomPart]                        = Nil
          }
      })

    source
      .runWith(TestSink.probe[DicomPart])
      .expectTestPart("Preamble")
      .expectTestPart("Header")
      .expectTestPart("Value Chunk")
      .expectTestPart("Header")
      .expectTestPart("Value Chunk")
      .expectTestPart("Header")
      .expectTestPart("Value Chunk")
      .expectTestPart("Sequence Start")
      .expectTestPart("Item Start")
      .expectTestPart("Header")
      .expectTestPart("Value Chunk")
      .expectTestPart("Item End")
      .expectTestPart("Sequence End")
      .expectTestPart("Fragments Start")
      .expectTestPart("Item Start")
      .expectTestPart("Value Chunk")
      .expectTestPart("Sequence End")
      .expectDicomComplete()
  }

  "The InSequence support" should "keep track of sequence depth" in {

    var expectedDepths = List(0, 0, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 1, 1, 0, 0, 0)

    def check(depth: Int, inSequence: Boolean): Unit = {
      depth shouldBe expectedDepths.head
      if (depth > 0) inSequence shouldBe true else inSequence shouldBe false
      expectedDepths = expectedDepths.tail
    }

    val bytes = studyDate() ++
      sequence(
        Tag.EnergyWindowInformationSequence
      ) ++ item() ++ studyDate() ++ itemDelimitation() ++ item() ++             // sequence
      sequence(Tag.EnergyWindowRangeSequence, 24) ++ item(16) ++ studyDate() ++ // nested sequence (determinate length)
      itemDelimitation() ++ sequenceDelimitation() ++
      personNameJohnDoe() // attribute

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(new DeferToPartFlow[DicomPart] with InSequence[DicomPart] {
        override def createLogic(attr: Attributes): GraphStageLogic =
          new DeferToPartLogic with InSequenceLogic {
            override def onPart(part: DicomPart): List[DicomPart] = {
              check(sequenceDepth, inSequence)
              part :: Nil
            }
          }
      })

    source
      .runWith(TestSink.probe[DicomPart])
      .request(18 - 2) // two events inserted
      .expectNextN(18 - 2)
  }

  "The guaranteed delimitation flow" should "call delimitation events at the end of sequences and items with determinate length" in {
    val bytes =
      sequence(Tag.DerivationCodeSequence, 56) ++ item(
        16
      ) ++ studyDate() ++ item() ++ studyDate() ++ itemDelimitation() ++
        sequence(Tag.AbstractPriorCodeSequence) ++ item() ++ studyDate() ++ itemDelimitation() ++ item(
        16
      ) ++ studyDate() ++ sequenceDelimitation()

    var expectedDelimitationLengths = List(0, 8, 0, 8, 0, 8)

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(new IdentityFlow with GuaranteedDelimitationEvents[DicomPart] {
        override def createLogic(attr: Attributes): GraphStageLogic =
          new IdentityLogic with GuaranteedDelimitationEventsLogic {
            override def onItemDelimitation(part: ItemDelimitationPart): List[DicomPart] = {
              part.bytes.length shouldBe expectedDelimitationLengths.head
              expectedDelimitationLengths = expectedDelimitationLengths.tail
              super.onItemDelimitation(part)
            }
            override def onSequenceDelimitation(part: SequenceDelimitationPart): List[DicomPart] = {
              part.bytes.length shouldBe expectedDelimitationLengths.head
              expectedDelimitationLengths = expectedDelimitationLengths.tail
              super.onSequenceDelimitation(part)
            }
          }
      })

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence, 56)
      .expectItem(1, 16)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      //.expectItemDelimitation() // delimitations not emitted by default
      .expectItem(2)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation()
      //.expectSequenceDelimitation()
      .expectSequence(Tag.AbstractPriorCodeSequence, -1)
      .expectItem(1, -1)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectItem(2, 16)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      //.expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "handle sequences that end with an item delimitation" in {
    val bytes =
      sequence(Tag.DerivationCodeSequence, 32) ++ item() ++ studyDate() ++ itemDelimitation()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(toIndeterminateLengthSequences)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem(1)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "work in datasets with nested sequences" in {
    val bytes = studyDate() ++ sequence(Tag.DerivationCodeSequence, 60) ++ item(52) ++ studyDate() ++
      sequence(Tag.DerivationCodeSequence, 24) ++ item(16) ++ studyDate() ++ personNameJohnDoe()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(toIndeterminateLengthSequences)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem(1)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem(1)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "handle empty sequences and items" in {
    val bytes =
      sequence(Tag.DerivationCodeSequence, 52) ++ item(16) ++ studyDate() ++ item(0) ++ item(12) ++ sequence(
        Tag.DerivationCodeSequence,
        0
      )

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(toIndeterminateLengthSequences)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem(1)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectItem(2)
      .expectItemDelimitation()
      .expectItem(3)
      .expectSequence(Tag.DerivationCodeSequence)
      .expectSequenceDelimitation()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "handle empty elements in sequences" in {
    val bytes =
      sequence(Tag.DerivationCodeSequence, 44) ++ item(36) ++ emptyPatientName() ++
        sequence(Tag.DerivationCodeSequence, 16) ++ item(8) ++ emptyPatientName()

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(toIndeterminateLengthSequences)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem(1)
      .expectHeader(Tag.PatientName)
      .expectSequence(Tag.DerivationCodeSequence)
      .expectItem(1)
      .expectHeader(Tag.PatientName)
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }

  it should "call event only once when used twice in flow" in {
    val bytes = sequence(Tag.DerivationCodeSequence, 24) ++ item(16) ++ personNameJohnDoe()

    var nItemDelims = 0
    var nSeqDelims  = 0

    val flow1 = new IdentityFlow with GuaranteedDelimitationEvents[DicomPart]
    val flow2 = new IdentityFlow with GuaranteedDelimitationEvents[DicomPart] {
      override def createLogic(attr: Attributes): GraphStageLogic =
        new IdentityLogic with GuaranteedDelimitationEventsLogic {
          override def onItemDelimitation(part: ItemDelimitationPart): List[DicomPart] = {
            nItemDelims += 1
            super.onItemDelimitation(part)
          }
          override def onSequenceDelimitation(part: SequenceDelimitationPart): List[DicomPart] = {
            nSeqDelims += 1
            super.onSequenceDelimitation(part)
          }
        }
    }

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(flow1)
      .via(flow2)

    Await.ready(source.runWith(Sink.ignore), 5.seconds)

    nItemDelims shouldBe 1
    nSeqDelims shouldBe 1
  }

  "The guaranteed value flow" should "call onValueChunk callback also after length zero headers" in {
    val bytes = personNameJohnDoe() ++ emptyPatientName()

    var expectedChunkLengths = List(8, 0)

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(new IdentityFlow with GuaranteedValueEvent[DicomPart] {
        override def createLogic(attr: Attributes): GraphStageLogic =
          new IdentityLogic with GuaranteedValueEventLogic {
            override def onValueChunk(part: ValueChunk): List[DicomPart] = {
              part.bytes.length shouldBe expectedChunkLengths.head
              expectedChunkLengths = expectedChunkLengths.tail
              super.onValueChunk(part)
            }
          }
      })

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.PatientName)
      .expectValueChunk(8)
      .expectHeader(Tag.PatientName)
      .expectDicomComplete()
  }

  it should "call event only once when flow is used twice" in {
    val bytes = emptyPatientName()

    var nEvents = 0

    val flow1 = new IdentityFlow with GuaranteedValueEvent[DicomPart]
    val flow2 = new IdentityFlow with GuaranteedValueEvent[DicomPart] {
      override def createLogic(attr: Attributes): GraphStageLogic =
        new IdentityLogic with GuaranteedValueEventLogic {
          override def onValueChunk(part: ValueChunk): List[DicomPart] = {
            nEvents += 1
            super.onValueChunk(part)
          }
        }
    }

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(flow1)
      .via(flow2)

    Await.ready(source.runWith(Sink.ignore), 5.seconds)
    nEvents shouldBe 1
  }

  "The start event flow" should "notify when dicom stream starts" in {
    val bytes = personNameJohnDoe()

    val flow = new IdentityFlow with StartEvent[DicomPart] {
      override def createLogic(attr: Attributes): GraphStageLogic =
        new IdentityLogic with StartEventLogic {
          override def onStart(): List[DicomPart] = DicomStartMarker :: Nil
        }
    }

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(flow)

    source
      .runWith(TestSink.probe[DicomPart])
      .request(1)
      .expectNextChainingPF {
        case DicomStartMarker => true
      }
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "call onStart for all combined flow stages" in {
    val flow = new DeferToPartFlow[DicomPart] with StartEvent[DicomPart] {
      override def createLogic(attr: Attributes): GraphStageLogic =
        new DeferToPartLogic with StartEventLogic {
          var state = 1
          override def onStart(): List[DicomPart] = {
            state = 0
            super.onStart()
          }
          override def onPart(part: DicomPart): List[DicomPart] = {
            state shouldBe 0
            part :: Nil
          }
        }
    }

    val source = Source.single(DicomEndMarker).via(flow).via(flow).via(flow)

    source
      .runWith(TestSink.probe[DicomPart])
      .request(1)
      .expectNextChainingPF {
        case DicomEndMarker => true
      }
      .expectDicomComplete()
  }

  it should "call onStart once for flows with more than one capability using the onStart event" in {
    val flow = new DeferToPartFlow[DicomPart] with GuaranteedDelimitationEvents[DicomPart] with StartEvent[DicomPart] {
      override def createLogic(attr: Attributes): GraphStageLogic =
        new DeferToPartLogic with GuaranteedDelimitationEventsLogic with StartEventLogic {
          var nCalls = 0
          override def onStart(): List[DicomPart] = {
            nCalls += 1
            super.onStart()
          }
          override def onPart(part: DicomPart): List[DicomPart] = {
            nCalls shouldBe 1
            part :: Nil
          }
        }
    }

    val source = Source.single(DicomEndMarker).via(flow)

    source
      .runWith(TestSink.probe[DicomPart])
      .request(1)
      .expectNextChainingPF {
        case DicomEndMarker => true
      }
      .expectDicomComplete()
  }

  "The end event flow" should "notify when dicom stream ends" in {
    val bytes = personNameJohnDoe()

    val flow = new IdentityFlow with EndEvent[DicomPart] {
      override def createLogic(attr: Attributes): GraphStageLogic =
        new IdentityLogic with EndEventLogic {
          override def onEnd(): List[DicomPart] = DicomEndMarker :: Nil
        }
    }

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(flow)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .request(1)
      .expectNextChainingPF {
        case DicomEndMarker => true
      }
      .expectDicomComplete()
  }

  "DICOM flows with tag path tracking" should "update the tag path through attributes, sequences and fragments" in {
    val bytes = preamble ++ fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ // FMI
      studyDate() ++
      sequence(
        Tag.EnergyWindowInformationSequence
      ) ++ item() ++ studyDate() ++ itemDelimitation() ++ item() ++             // sequence
      sequence(Tag.EnergyWindowRangeSequence, 24) ++ item(16) ++ studyDate() ++ // nested sequence (determinate length)
      itemDelimitation() ++ sequenceDelimitation() ++
      personNameJohnDoe() ++ // attribute
      pixeDataFragments() ++ item(4) ++ ByteString(1, 2, 3, 4) ++ sequenceDelimitation()

    var expectedPaths = List(
      EmptyTagPath,                                                                    // preamble
      TagPath.fromTag(Tag.FileMetaInformationGroupLength),                             // FMI group length header
      TagPath.fromTag(Tag.FileMetaInformationGroupLength),                             // FMI group length value
      TagPath.fromTag(Tag.TransferSyntaxUID),                                          // Transfer syntax header
      TagPath.fromTag(Tag.TransferSyntaxUID),                                          // Transfer syntax value
      TagPath.fromTag(Tag.StudyDate),                                                  // Patient name header
      TagPath.fromTag(Tag.StudyDate),                                                  // Patient name value
      TagPath.fromSequence(Tag.EnergyWindowInformationSequence),                       // sequence start
      TagPath.fromItem(Tag.EnergyWindowInformationSequence, 1),                        // item start
      TagPath.fromItem(Tag.EnergyWindowInformationSequence, 1).thenTag(Tag.StudyDate), // study date header
      TagPath.fromItem(Tag.EnergyWindowInformationSequence, 1).thenTag(Tag.StudyDate), // study date value
      TagPath.fromItemEnd(Tag.EnergyWindowInformationSequence, 1),                     // item end
      TagPath.fromItem(Tag.EnergyWindowInformationSequence, 2),                        // item start
      TagPath
        .fromItem(Tag.EnergyWindowInformationSequence, 2)
        .thenSequence(Tag.EnergyWindowRangeSequence),                                                      // sequence start
      TagPath.fromItem(Tag.EnergyWindowInformationSequence, 2).thenItem(Tag.EnergyWindowRangeSequence, 1), // item start
      TagPath
        .fromItem(Tag.EnergyWindowInformationSequence, 2)
        .thenItem(Tag.EnergyWindowRangeSequence, 1)
        .thenTag(Tag.StudyDate), // Study date header
      TagPath
        .fromItem(Tag.EnergyWindowInformationSequence, 2)
        .thenItem(Tag.EnergyWindowRangeSequence, 1)
        .thenTag(Tag.StudyDate), // Study date value
      TagPath
        .fromItem(Tag.EnergyWindowInformationSequence, 2)
        .thenItemEnd(Tag.EnergyWindowRangeSequence, 1), //  item end (inserted)
      TagPath
        .fromItem(Tag.EnergyWindowInformationSequence, 2)
        .thenSequenceEnd(Tag.EnergyWindowRangeSequence),            // sequence end (inserted)
      TagPath.fromItemEnd(Tag.EnergyWindowInformationSequence, 2),  // item end
      TagPath.fromSequenceEnd(Tag.EnergyWindowInformationSequence), // sequence end
      TagPath.fromTag(Tag.PatientName),                             // Patient name header
      TagPath.fromTag(Tag.PatientName),                             // Patient name value
      TagPath.fromTag(Tag.PixelData),                               // fragments start
      TagPath.fromTag(Tag.PixelData),                               // item start
      TagPath.fromTag(Tag.PixelData),                               // fragment data
      TagPath.fromTag(Tag.PixelData)                                // fragments end
    )

    def check(tagPath: TagPath): Unit = {
      tagPath shouldBe expectedPaths.head
      expectedPaths = expectedPaths.tail
    }

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(new DeferToPartFlow[DicomPart] with TagPathTracking[DicomPart] {
        override def createLogic(attr: Attributes): GraphStageLogic =
          new DeferToPartLogic with TagPathTrackingLogic {
            override def onPart(part: DicomPart): List[DicomPart] = {
              check(tagPath)
              part :: Nil
            }
          }
      })

    source
      .runWith(TestSink.probe[DicomPart])
      .request(27 - 2) // two events inserted
      .expectNextN(27 - 2)
  }

  it should "support using tracking more than once within a flow" in {
    val bytes = sequence(Tag.DerivationCodeSequence, 24) ++ item(16) ++ personNameJohnDoe()

    // must be def, not val
    val flow = new IdentityFlow with TagPathTracking[DicomPart]

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(flow)
      .via(flow)

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence, 24)
      .expectItem(1, 16)
      .expectHeader(Tag.PatientName)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "support sequences and items with explicit length" in {
    val bytes = personNameJohnDoe() ++
      sequence(Tag.DigitalSignaturesSequence, 680) ++
      item(672) ++
      element(Tag.MACIDNumber, ByteString(1, 1), bigEndian = false, explicitVR = true) ++
      element(Tag.DigitalSignatureUID, "1" * 54) ++
      element(Tag.CertificateType, "A" * 14) ++
      element(Tag.CertificateOfSigner, ByteString(new Array[Byte](426)), bigEndian = false, explicitVR = true) ++
      element(Tag.Signature, ByteString(new Array[Byte](128)), bigEndian = false, explicitVR = true)

    var expectedPaths = List(
      TagPath.fromTag(Tag.PatientName),
      TagPath.fromTag(Tag.PatientName),
      TagPath.fromSequence(Tag.DigitalSignaturesSequence), // sequence start
      TagPath.fromItem(Tag.DigitalSignaturesSequence, 1),  // item start
      TagPath.fromItem(Tag.DigitalSignaturesSequence, 1).thenTag(Tag.MACIDNumber),
      TagPath.fromItem(Tag.DigitalSignaturesSequence, 1).thenTag(Tag.MACIDNumber),
      TagPath.fromItem(Tag.DigitalSignaturesSequence, 1).thenTag(Tag.DigitalSignatureUID),
      TagPath.fromItem(Tag.DigitalSignaturesSequence, 1).thenTag(Tag.DigitalSignatureUID),
      TagPath.fromItem(Tag.DigitalSignaturesSequence, 1).thenTag(Tag.CertificateType),
      TagPath.fromItem(Tag.DigitalSignaturesSequence, 1).thenTag(Tag.CertificateType),
      TagPath.fromItem(Tag.DigitalSignaturesSequence, 1).thenTag(Tag.CertificateOfSigner),
      TagPath.fromItem(Tag.DigitalSignaturesSequence, 1).thenTag(Tag.CertificateOfSigner),
      TagPath.fromItem(Tag.DigitalSignaturesSequence, 1).thenTag(Tag.Signature),
      TagPath.fromItem(Tag.DigitalSignaturesSequence, 1).thenTag(Tag.Signature),
      TagPath.fromItemEnd(Tag.DigitalSignaturesSequence, 1), // item end (inserted)
      TagPath.fromSequenceEnd(Tag.DigitalSignaturesSequence)
    ) // sequence end (inserted)

    def check(tagPath: TagPath): Unit = {
      tagPath shouldBe expectedPaths.head
      expectedPaths = expectedPaths.tail
    }

    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(new DeferToPartFlow[DicomPart] with TagPathTracking[DicomPart] {
        override def createLogic(attr: Attributes): GraphStageLogic =
          new DeferToPartLogic with TagPathTrackingLogic {
            override def onPart(part: DicomPart): List[DicomPart] = {
              check(tagPath)
              part :: Nil
            }
          }
      })

    source
      .runWith(TestSink.probe[DicomPart])
      .request(12)
      .expectNextN(12)
  }

  it should "track an entire file without exception" in {
    val file = new File(getClass.getResource("../data/test001.dcm").toURI)
    val source = FileIO
      .fromPath(file.toPath)
      .via(parseFlow)
      .via(new IdentityFlow with TagPathTracking[DicomPart])

    Await.result(source.runWith(Sink.ignore), 5.seconds)
    succeed
  }

  "Prepending elements in combined flows" should "not insert everything at the beginning" in {
    val source = Source.single(1)
    val flow   = Flow[Int].map(_ + 1).prepend(Source.single(0))

    val combined = source.via(flow).via(flow) // 1 -> 0 2 -> 0 1 3

    combined
      .runWith(TestSink.probe[Int])
      .request(3)
      .expectNextChainingPF {
        case 0 => true
      }
      .expectNextChainingPF {
        case 1 => true
      }
      .expectNextChainingPF {
        case 3 => true
      }
      .expectComplete()
  }

  "The group length warnings flow" should "issue a warning when a group length attribute is encountered" in {
    val bytes = preamble ++ fmiGroupLength(transferSyntaxUID()) ++ transferSyntaxUID() ++ groupLength(
      8,
      studyDate().length
    ) ++ studyDate()
    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(new IdentityFlow with GroupLengthWarnings[DicomPart])

    source
      .runWith(TestSink.probe[DicomPart])
      .expectPreamble()
      .expectHeader(Tag.FileMetaInformationGroupLength)
      .expectValueChunk()
      .expectHeader(Tag.TransferSyntaxUID)
      .expectValueChunk()
      .expectHeader(0x00080000, VR.UL, 4)
      .expectValueChunk()
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "issue a warning when determinate length sequences and items are encountered" in {
    val bytes = sequence(Tag.DerivationCodeSequence, 24) ++ item(16) ++ studyDate()
    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(new IdentityFlow with GroupLengthWarnings[DicomPart])

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence, 24)
      .expectItem(1, 16)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "not warn when silent" in {
    val bytes = sequence(Tag.DerivationCodeSequence, 24) ++ item(16) ++ studyDate()
    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(new IdentityFlow with GroupLengthWarnings[DicomPart] {
        override def createLogic(attr: Attributes): GraphStageLogic =
          new IdentityLogic with GroupLengthWarningsLogic {
            silent = true
          }
      })

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence, 24)
      .expectItem(1, 16)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectDicomComplete()
  }

  it should "not warn after re-encoding to indeterminate length sequences and items" in {
    val bytes = sequence(Tag.DerivationCodeSequence, 24) ++ item(16) ++ studyDate()
    val source = Source
      .single(bytes)
      .via(parseFlow)
      .via(toIndeterminateLengthSequences)
      .via(new IdentityFlow with GroupLengthWarnings[DicomPart])

    source
      .runWith(TestSink.probe[DicomPart])
      .expectSequence(Tag.DerivationCodeSequence, indeterminateLength)
      .expectItem(1, indeterminateLength)
      .expectHeader(Tag.StudyDate)
      .expectValueChunk()
      .expectItemDelimitation()
      .expectSequenceDelimitation()
      .expectDicomComplete()
  }
}
