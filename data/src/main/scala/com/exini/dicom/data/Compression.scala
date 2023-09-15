package com.exini.dicom.data

import java.util.zip.{ Deflater, Inflater }

object Compression {

  def compress(bytes: Bytes, compressor: Deflater): Bytes = {
    compressor.setInput(bytes.unwrap)
    val buffer = new Array[Byte](bytes.length)
    var out    = emptyBytes
    var n      = 1
    while (n > 0) {
      n = compressor.deflate(buffer, 0, buffer.length, Deflater.FULL_FLUSH)
      out = out ++ buffer.take(n)
    }
    out
  }

  def compress(bytes: Bytes, gzip: Boolean = false): Bytes = {
    val compressor = if (gzip) new Deflater() else new Deflater(-1, true)
    compress(bytes, compressor)
  }

  def decompress(bytes: Bytes, decompressor: Inflater): Bytes = {
    decompressor.setInput(bytes.unwrap)
    val buffer: Array[Byte] = new Array[Byte](bytes.length)
    var out                 = emptyBytes
    var n                   = 1
    while (n > 0) {
      n = decompressor.inflate(buffer)
      out = out ++ buffer.take(n)
    }
    out
  }

  def decompress(bytes: Bytes, gzip: Boolean = false): Bytes = {
    val decompressor: Inflater = new Inflater(!gzip)
    decompress(bytes, decompressor)
  }

}
