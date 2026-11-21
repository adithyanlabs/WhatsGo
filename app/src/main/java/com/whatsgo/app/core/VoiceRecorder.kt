package com.whatsgo.app.core

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

class VoiceRecorder(private val context: Context) {
    class Result(val path: String, val seconds: Int, val waveform: ByteArray)

    private val rate = 16000
    private val frameSamples = 320            // 20 ms at 16 kHz

    private var job: Job? = null
    @Volatile private var running = false
    @Volatile private var result: Result? = null

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        if (running) return false

        val minBuf = AudioRecord.getMinBufferSize(
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return false
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, frameSamples * 2 * 8)
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return false }

        val codec = try {
            val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, rate, 1)
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, 24000)
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).apply {
                configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "codec init failed", e)
            rec.release()
            return false
        }

        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.ogg")
        result = null
        running = true

        job = scope.launch(Dispatchers.IO) {
            val ogg = OggOpusWriter(file, rate)
            val peaks = ArrayList<Int>()
            val info = MediaCodec.BufferInfo()
            val pcm = ByteArray(frameSamples * 2)
            var ptsUs = 0L
            var eosSent = false
            var done = false
            var packets = 0
            try {
                rec.startRecording()
                while (!done) {
                    if (!eosSent) {
                        val n = if (running) rec.read(pcm, 0, pcm.size) else 0
                        val idx = codec.dequeueInputBuffer(10_000)
                        if (idx >= 0) {
                            if (!running) {
                                codec.queueInputBuffer(idx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                eosSent = true
                            } else {
                                val size = n.coerceAtLeast(0)
                                if (size > 0) {
                                    codec.getInputBuffer(idx)!!.apply { clear(); put(pcm, 0, size) }
                                    var peak = 0
                                    var i = 0
                                    while (i + 1 < size) {
                                        val s = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
                                        peak = maxOf(peak, abs(s.toShort().toInt()))
                                        i += 2
                                    }
                                    peaks.add(peak)
                                }
                                codec.queueInputBuffer(idx, 0, size, ptsUs, 0)
                                ptsUs += (size / 2) * 1_000_000L / rate
                            }
                        }
                    }
                    while (true) {
                        val o = codec.dequeueOutputBuffer(info, if (eosSent) 10_000 else 0)
                        if (o < 0) break
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                            val b = codec.getOutputBuffer(o)!!
                            b.position(info.offset); b.limit(info.offset + info.size)
                            val pkt = ByteArray(info.size)
                            b.get(pkt)
                            ogg.packet(pkt)
                            packets++
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) { done = true; break }
                    }
                }
                ogg.close()
                if (packets > 0) {
                    val secs = ((ptsUs + 500_000) / 1_000_000).toInt().coerceAtLeast(1)
                    result = Result(file.absolutePath, secs, buildWaveform(peaks))
                    // Debug: copy to external storage for verification
                    val exportDir = context.getExternalFilesDir(null)
                    if (exportDir != null) {
                        val exportFile = File(exportDir, "voice_export_${System.currentTimeMillis()}.ogg")
                        runCatching { file.copyTo(exportFile) }
                            .onSuccess { Log.d("VoiceRecorder", "Exported: ${exportFile.absolutePath}") }
                    }
                } else file.delete()
            } catch (e: Exception) {
                Log.e("VoiceRecorder", "record failed", e)
                runCatching { ogg.close() }
                file.delete()
            } finally {
                runCatching { rec.stop() }
                rec.release()
                runCatching { codec.stop() }
                codec.release()
            }
        }
        return true
    }

    fun stop(): Result? {
        running = false
        val j = job ?: return null
        runBlocking { j.join() }
        job = null
        val r = result
        result = null
        return r
    }

    private fun buildWaveform(src: List<Int>): ByteArray {
        val n = 64
        if (src.isEmpty()) return ByteArray(n) { 10 }
        val peak = (src.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val out = ByteArray(n)
        for (i in 0 until n) {
            val from = (i * src.size / n).coerceAtMost(src.size - 1)
            val to = ((i + 1) * src.size / n).coerceAtLeast(from + 1).coerceAtMost(src.size)
            val m = src.subList(from, to).maxOrNull() ?: 0
            out[i] = (sqrt(m / peak) * 100f).toInt().coerceIn(4, 100).toByte()
        }
        return out
    }
}

/** Minimal Ogg/Opus writer: OpusHead + OpusTags + audio pages. 20 ms frames, 48 kHz granule clock. */
internal class OggOpusWriter(file: File, inputRate: Int) {
    private val os = FileOutputStream(file)
    private val serial = Random.nextInt()
    private var seq = 0
    private var granule = 0L
    private val pending = ArrayList<ByteArray>()
    private var segs = 0

    init {
        val head = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN)
            .put("OpusHead".toByteArray())
            .put(1)                       // version
            .put(1)                       // channels
            .putShort(3840)                // pre-skip (48 kHz samples)
            .putInt(48000)
            .putShort(0)                  // gain
            .put(0)                       // mapping family
            .array()
        writePage(listOf(head), 0L, 0x02)  // BOS

        val vendor = "WhatsGo".toByteArray()
        val tags = ByteBuffer.allocate(8 + 4 + vendor.size + 4).order(ByteOrder.LITTLE_ENDIAN)
            .put("OpusTags".toByteArray())
            .putInt(vendor.size).put(vendor)
            .putInt(0)
            .array()
        writePage(listOf(tags), 0L, 0)
        granule = 3840L
    }

    fun packet(p: ByteArray) {
        val s = p.size / 255 + 1
        if (segs + s > 255 || pending.size >= 20) flush(0)
        pending.add(p); segs += s
        granule += 960                     // 20 ms at 48 kHz
    }

    fun close() {
        flush(0x04)                        // EOS
        os.close()
    }

    private fun flush(type: Int) {
        writePage(ArrayList(pending), granule, type)
        pending.clear(); segs = 0
    }

    private fun writePage(packets: List<ByteArray>, gp: Long, type: Int) {
        val table = ArrayList<Int>()
        for (p in packets) {
            var left = p.size
            while (left >= 255) { table.add(255); left -= 255 }
            table.add(left)
        }
        val dataLen = packets.sumOf { it.size }
        val buf = ByteBuffer.allocate(27 + table.size + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("OggS".toByteArray())
        buf.put(0)
        buf.put(type.toByte())
        buf.putLong(gp)
        buf.putInt(serial)
        buf.putInt(seq++)
        buf.putInt(0)                      // crc placeholder at offset 22
        buf.put(table.size.toByte())
        table.forEach { buf.put(it.toByte()) }
        packets.forEach { buf.put(it) }
        val arr = buf.array()
        val crc = crc32(arr)
        ByteBuffer.wrap(arr).order(ByteOrder.LITTLE_ENDIAN).putInt(22, crc)
        os.write(arr)
    }

    companion object {
        private val TABLE = IntArray(256) { i ->
            var r = i shl 24
            repeat(8) { r = if (r and 0x80000000.toInt() != 0) (r shl 1) xor 0x04C11DB7 else r shl 1 }
            r
        }
        fun crc32(b: ByteArray): Int {
            var c = 0
            for (x in b) c = (c shl 8) xor TABLE[((c ushr 24) xor (x.toInt() and 0xFF)) and 0xFF]
            return c
        }
    }
}