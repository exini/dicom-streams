package com.exini.dicom.data

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class CompressionTest extends AnyFlatSpec with Matchers {

  "Compressing a dataset" should "reduce its size" in {
    val bytes   = Random.nextBytes(256) ++ Array.fill[Byte](256)(127)
    val zipped1 = Compression.compress(bytes)
    val zipped2 = Compression.compress(bytes, gzip = true)
    zipped1.length should be < bytes.length
    zipped2.length should be < bytes.length
  }

  it should "be perfectly restored when decompressed again" in {
    val bytes     = Random.nextBytes(256) ++ Array.fill[Byte](256)(127)
    val zipped1   = Compression.compress(bytes)
    val zipped2   = Compression.compress(bytes, gzip = true)
    val restored1 = Compression.decompress(zipped1)
    val restored2 = Compression.decompress(zipped2, gzip = true)
    restored1 shouldBe bytes
    restored2 shouldBe bytes
  }
}
