package com.exini.dicom.data

import java.util.zip.{ Deflater, Inflater }

object Compression {

  def compress(bytes: Array[Byte], compressor: Deflater): Array[Byte] = {
    compressor.setInput(bytes)
    val buffer = new Array[Byte](bytes.length)
    var out    = Array.emptyByteArray
    var n      = 1
    while (n > 0) {
      n = compressor.deflate(buffer, 0, buffer.length, Deflater.FULL_FLUSH)
      out = out ++ buffer.take(n)
    }
    out
  }

  def compress(bytes: Array[Byte], gzip: Boolean = false): Array[Byte] = {
    val compressor = if (gzip) new Deflater() else new Deflater(-1, true)
    compress(bytes, compressor)
  }

  def decompress(bytes: Array[Byte], decompressor: Inflater): Array[Byte] = {
    decompressor.setInput(bytes)
    val buffer: Array[Byte] = new Array[Byte](bytes.length)
    var out                 = Array.emptyByteArray
    var n                   = 1
    while (n > 0) {
      n = decompressor.inflate(buffer)
      out = out ++ buffer.take(n)
    }
    out
  }

  def decompress(bytes: Array[Byte], gzip: Boolean = false): Array[Byte] = {
    val decompressor: Inflater = new Inflater(!gzip)
    decompress(bytes, decompressor)
  }

}
