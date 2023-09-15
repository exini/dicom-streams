# dicom-streams

Service | Status | Description
------- | ------ | -----------
Gitter            | [![Join the chat at https://gitter.im/exini/dicom-streams](https://badges.gitter.im/exini/dicom-streams.svg)](https://gitter.im/exini/dicom-streams?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) | Chatroom

The purpose of this project is to create a streaming API for reading and processing DICOM data using [akka-streams](http://doc.akka.io/docs/akka/current/scala/stream/index.html). 

Advantages of streaming DICOM data include better control over resource allocation such as memory via strict bounds on
DICOM data chunk size and network utilization using back-pressure as specified in the
[Reactive Streams](http://www.reactive-streams.org/) protocol.

The library is split in two projects. The `data` project defines data structures and common functionality with minimal
dependencies and offers synchronous parsing of (possibly chunked) binary data. The `streams` project provides 
functionality for streaming DICOM data and pulls in Akka as a required dependency.

The logic of parsing and handling DICOM data is inspired by [dcm4che](https://github.com/dcm4che/dcm4che)
which provides a far more complete (albeit blocking and synchronous) implementation of the DICOM standard.

### Setup

The dicom-streams library is deployed to Sonatype. You need to include the Sonatype resolvers to find the package.

```scala
resolvers ++= Seq(Resolver.sonatypeRepo("releases"), Resolver.sonatypeRepo("snapshots"))
```

The library is included by
```scala
libraryDependencies += "com.exini" %% "dicom-streams" % "x.y.z"
```
If you want to use the basic data package without the streams functionality use
```scala
libraryDependencies += "com.exini" %% "dicom-data" % "x.y.z"
```

### Data Model

Streaming binary DICOM data may originate from many different sources such as files, a HTTP POST request, or a read from
a database. Akka Streams provide a multitude of connectors for streaming binary data. Streaming data arrives in chunks. 
In the Akka Stream nomenclature, chunks originate from _sources_, they are processed in _flows_ and folded into a 
non-streaming plain objects using _sinks_. 

This library provides flows for parsing binary DICOM data into DICOM parts (represented by the `DicomPart` interface) - 
small objects representing a part of a data element. These DICOM parts are bounded in size by a user specified chunk 
size parameter. Flows of DICOM parts can be processed using a series of flows in this library. There are flows for 
filtering based on tag path conditions, flows for converting between transfer syntaxes, flows for re-encoding sequences 
and items, etc. 

The `Element` interface provides a set of higher level data classes, each roughly corresponding to one row in a textual
dump of a DICOM files. Here, chunks are aggregated into complete data elements. There are representations for standard
tag-value elements, sequence and item start elements, sequence and item delimitation elements, fragments start elements,
etc. A `DicomPart` stream is transformed into an `Element` stream via the `elementFlow` flow.

A flow of `Element`s can be materialized into a representation of a dataset called an `Elements` using the `elementSink`
sink. For processing of large sets of data, one should strive for a fully streaming DICOM pipeline, however, in some 
cases it can be convenient to work with a plain dataset; `Elements` serves this purpose. Internally, the sink aggregates
`Element`s into `ElementSet`s, each with an asssociated tag number (value elements, sequences and fragments). `Elements`
implements a straight-forward data hierarchy:
* An `Elements` holds a list of `ElementSet`s (`ValueElement`, `Sequence` and `Fragments`)
* A `ValueElement` is a standard attribute with tag number and binary value
* A `Sequence` holds a list of `Item`s
  * An `Item` contains zero or one `Elements` (note the recursion)
* A `Fragments` holds a list of `Fragment`s
  * A `Fragment` holds a binary value. 

The following diagram shows an overview of the data model at the `DicomPart`, `Element` and `ElementSet` levels.

![Data model](README/data-model.png)

As seen, a standard attribute, represented by the `ValueElement` class is composed by one `HeaderPart` followed by zero,
one or more `ValueChunk`s of data. Likewise, ecapsulated data such as a jpeg image is composed by one `FragmentsPart`
followed by, for each fragment, one `ItemPart` followed by `ValueChunk`s of data, and ends with a
`SequenceDelimitationPart`.

### Examples

The following example reads a DICOM file from disk, validates that it is a DICOM file, discards all private elements
and writes it to a new file.

```scala
FileIO.fromPath(Paths.get("source-file.dcm"))
  .via(parseFlow)
  .via(tagFilter(tagPath => tagPath.toList.map(_.tag).exists(isPrivate))) // no private elements anywhere on tag path
  .map(_.bytes)
  .runWith(FileIO.toPath(Paths.get("target-file.dcm")))
```

Care should be taken when modifying DICOM data so that the resulting data is still valid. For instance, group length
tags may need to be removed or updated after modifying elements. Here is an example that modifies the `PatientName`
and `SOPInstanceUID` attributes. To ensure the resulting data is valid, group length tags in the dataset are removed and
the meta information group tag is updated.

```scala
val updatedSOPInstanceUID = padToEvenLength(createUID().utf8Bytes, VR.UI)

FileIO.fromPath(Paths.get("source-file.dcm"))
  .via(parseFlow)
  .via(groupLengthDiscardFilter) // discard group length elements in dataset
  .via(modifyFlow(
    Seq(
      TagModification.endsWith(TagPath.fromTag(Tag.PatientName), _ => padToEvenLength("John Doe".utf8Bytes, VR.PN)),
      TagModification.endsWith(TagPath.fromTag(Tag.MediaStorageSOPInstanceUID), _ => updatedSOPInstanceUID)
    ), 
    Seq(
      TagInsertion(TagPath.fromTag(Tag.SOPInstanceUID), _ => updatedSOPInstanceUID)
    )
  ))
  .via(fmiGroupLengthFlow) // update group length in meta information, if present
  .map(_.bytes)
  .runWith(FileIO.toPath(Paths.get("target-file.dcm")))
```

### Custom Processing
New non-trivial DICOM flows can be built using a modular system of capabilities that are mixed in as appropriate with a 
core class implementing a common base interface. The base interface for DICOM flows is `DicomFlow`. It provides an
associated logic class `DicomLogic` that should be similarly extended and is where the state and logic of the flow should
reside. The `DicomLogic` base class defines a series of events, one for each type of `DicomPart` that is produced when 
parsing DICOM data with `DicomParseFlow`. The core events are:
```scala
  def onPreamble(part: PreamblePart): List[DicomPart]
  def onHeader(part: HeaderPart): List[DicomPart]
  def onValueChunk(part: ValueChunk): List[DicomPart]
  def onSequence(part: SequencePart): List[DicomPart]
  def onSequenceDelimitation(part: SequenceDelimitationPart): List[DicomPart]
  def onFragments(part: FragmentsPart): List[DicomPart]
  def onItem(part: ItemPart): List[DicomPart]
  def onItemDelimitation(part: ItemDelimitationPart): List[DicomPart]
  def onDeflatedChunk(part: DeflatedChunk): List[DicomPart]
  def onUnknown(part: UnknownPart): List[DicomPart]
  def onPart(part: DicomPart): List[DicomPart]
```
Default behavior to these events are implemented in core classes. The most natural behavior is to simply pass parts on
down the stream, e.g. 
```scala
  def onPreamble(part: PreamblePart): List[DicomPart] = part :: Nil
  def onHeader(part: HeaderPart): List[DicomPart] = part :: Nil
  ...
```
This behavior is implemented in the `IdentityFlow` core class. Another option is to defer handling to the `onPart` method
which is implemented in the `DeferToPartFlow` core class. This is appropriate for flows which define a common 
behavior for all part types. 

To give an example of a custom flow, here is the implementation of a filter that removes 
nested sequences from a dataset. We define a nested dataset as a sequence with `depth > 1` given that the root dataset 
has `depth = 0`.
```scala
  def nestedSequencesFilter() = new DeferToPartFlow[DicomPart] with TagPathTracking[DicomPart] {
    override def createLogic(attr: Attributes): GraphStageLogic = new DeferToPartLogic with TagPathTrackingLogic {
      override def onPart(part: DicomPart): List[DicomPart] = if (tagPath.depth > 1) Nil else part :: Nil
    }
  }
```
In this example, we chose to use `DeferToPartFlow` as the core class and mixed in the `TagPathTracking` capability
which gives access to a `tagPath: TagPath` variable at all times which is automatically updated as the flow progresses.

### Releasing

The plugin [sbt-ci-release](https://github.com/olafurpg/sbt-ci-release) is used to manage releasing to Sonatype. The
library is automatically released on each push to the master branch. The version is derived from the tag closet to the
latest commit to master. Given this, the following procedure should be used:
1. Merge develop into master via a new branch
1. Tag the new release as `vx.y.z`, e.g. using `git tag v3.2.1`, following semantic versioning recommendations
1. Push tags to origin by `git push --tags`
1. Push release commit to origin by `git push`, this will trigger the release using the tagged version
1. From the Github project page, create a new release using the existing release tag describing your changes
1. Merge master into develop via a new branch.

### License

This project is released under the [Apache License, version 2.0](./LICENSE).
