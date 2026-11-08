package com.example.persiandub

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/** Decodes the audio track of a video to 16 kHz mono PCM16 and streams it in chunks. */
object AudioExtractor {
    fun extractPcm16k(context: Context, uri: Uri, onChunk: (ByteArray, Int) -> Unit) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        var track = -1
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) { track = i; break }
        }
        require(track >= 0) { "این ویدیو صدا ندارد" }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        var srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val info = MediaCodec.BufferInfo()
        val out = ByteArray(8000)
        var outLen = 0
        var sum = 0L; var n = 0; var acc = 0
        var inputDone = false; var outputDone = false

        fun emit(s: Short) {
            out[outLen++] = (s.toInt() and 0xFF).toByte()
            out[outLen++] = ((s.toInt() shr 8) and 0xFF).toByte()
            if (outLen == out.size) { onChunk(out, outLen); outLen = 0 }
        }

        while (!outputDone) {
            if (!inputDone) {
                val i = codec.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val buf = codec.getInputBuffer(i)!!
                    val size = extractor.readSampleData(buf, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(i, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 10_000)
            when {
                o >= 0 -> {
                    val buf = codec.getOutputBuffer(o)!!
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    val shorts = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    while (shorts.remaining() >= channels) {
                        var s = 0
                        repeat(channels) { s += shorts.get() }
                        sum += s / channels; n++
                        acc += 16000
                        if (acc >= srcRate) {
                            acc -= srcRate
                            emit((sum / n).toShort())
                            sum = 0; n = 0
                        }
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
                o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = codec.outputFormat
                    srcRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
        }
        if (outLen > 0) onChunk(out, outLen)
        codec.stop(); codec.release(); extractor.release()
    }
}
