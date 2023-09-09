package com.exini.dicom.data

import akka.util.ByteString

import java.util.zip.{ Deflater, Inflater }

object Compression {

  def compress(bytes: ByteString, compressor: Deflater): ByteString = {
    compressor.setInput(bytes.toArray)
    val buffer = new Array[Byte](bytes.length)
    var out    = ByteString.empty
    var n      = 1
    while (n > 0) {
      n = compressor.deflate(buffer, 0, buffer.length, Deflater.FULL_FLUSH)
      out = out ++ ByteString.fromArray(buffer, 0, n)
    }
    out
  }

  def compress(bytes: ByteString, gzip: Boolean = false): ByteString = {
    val compressor = if (gzip) new Deflater() else new Deflater(-1, true)
    compress(bytes, compressor)
  }

  def decompress(bytes: ByteString, decompressor: Inflater): ByteString = {
    decompressor.setInput(bytes.toArrayUnsafe())
    val buffer: Array[Byte] = new Array[Byte](bytes.length)
    var out                 = ByteString.empty
    var n                   = 1
    while (n > 0) {
      n = decompressor.inflate(buffer)
      out = out ++ ByteString.fromArray(buffer, 0, n)
    }
    out
  }

  def decompress(bytes: ByteString, gzip: Boolean = false): ByteString = {
    val decompressor: Inflater = new Inflater(!gzip)
    decompress(bytes, decompressor)
  }

}
