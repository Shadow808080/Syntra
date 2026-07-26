package com.example.syntra.net

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

/**
 * Cuts a slice `[startMs, endMs)` out of any device audio the phone can decode
 * (mp3, m4a, flac, ogg, wav…) and re-encodes it to a clean **AAC/.m4a** file.
 *
 * Re-encoding (decode → PCM → AAC) instead of copying compressed samples is what
 * makes this format-universal and sample-accurate on the start/end — a raw sample
 * copy only works reliably for AAC sources and cuts on sync-frame boundaries.
 *
 * Runs synchronously; call it off the main thread. The result lands in the app
 * cache and is what actually gets uploaded — the original file is never sent, so
 * the user only ever publishes the portion they picked.
 */
object AudioTrimmer {

    /** Hard ceiling shared with the UI so a clip can never exceed 10 minutes. */
    const val MAX_CLIP_MS = 10 * 60 * 1000L

    private const val OUTPUT_BITRATE = 128_000
    private const val TIMEOUT_US = 10_000L

    /**
     * Trims [uri] to `[startMs, endMs)` and returns the encoded .m4a file, or null
     * if the source has no decodable audio track. Throws on codec failure.
     */
    fun trim(context: Context, uri: Uri, startMs: Long, endMs: Long): File? {
        val startUs = (startMs.coerceAtLeast(0)) * 1000
        val endUs = (endMs.coerceAtMost(startMs + MAX_CLIP_MS)) * 1000
        require(endUs > startUs) { "rentang tidak valid" }

        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val trackIndex = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: run { extractor.release(); return null }

        val inFormat = extractor.getTrackFormat(trackIndex)
        val inMime = inFormat.getString(MediaFormat.KEY_MIME)!!
        extractor.selectTrack(trackIndex)

        val outFile = File(context.cacheDir, "trim_${System.currentTimeMillis()}.m4a")

        val decoder = MediaCodec.createDecoderByType(inMime)
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxTrack = -1
        var muxerStarted = false

        try {
            decoder.configure(inFormat, null, null, 0)
            decoder.start()
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val info = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawDecodeEOS = false
            var sawEncodeEOS = false

            // --- feeds one PCM region into the AAC encoder, chunking to fit its
            // input buffers and computing per-chunk timestamps from the byte rate.
            var bytesPerUs = 0.0
            fun drainEncoder() {
                val enc = encoder ?: return
                while (true) {
                    val outIndex = enc.dequeueOutputBuffer(info, TIMEOUT_US)
                    if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) return
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        muxTrack = muxer!!.addTrack(enc.outputFormat)
                        muxer!!.start()
                        muxerStarted = true
                        continue
                    }
                    if (outIndex < 0) continue
                    val encoded = enc.getOutputBuffer(outIndex)!!
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (info.size > 0 && !isConfig && muxerStarted) {
                        encoded.position(info.offset)
                        encoded.limit(info.offset + info.size)
                        muxer!!.writeSampleData(muxTrack, encoded, info)
                    }
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    enc.releaseOutputBuffer(outIndex, false)
                    if (eos) { sawEncodeEOS = true; return }
                }
            }

            fun feedEncoder(pcm: ByteBuffer, ptsUs: Long) {
                val enc = encoder ?: return
                var chunkPts = ptsUs
                while (pcm.hasRemaining()) {
                    var inIndex = enc.dequeueInputBuffer(TIMEOUT_US)
                    while (inIndex < 0) { drainEncoder(); inIndex = enc.dequeueInputBuffer(TIMEOUT_US) }
                    val dst = enc.getInputBuffer(inIndex)!!
                    dst.clear()
                    val n = minOf(dst.remaining(), pcm.remaining())
                    val slice = pcm.duplicate()
                    slice.limit(slice.position() + n)
                    dst.put(slice)
                    pcm.position(pcm.position() + n)
                    enc.queueInputBuffer(inIndex, 0, n, chunkPts, 0)
                    if (bytesPerUs > 0) chunkPts += (n / bytesPerUs).toLong()
                }
            }

            fun signalEncoderEOS() {
                val enc = encoder ?: return
                var inIndex = enc.dequeueInputBuffer(TIMEOUT_US)
                while (inIndex < 0) { drainEncoder(); inIndex = enc.dequeueInputBuffer(TIMEOUT_US) }
                enc.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }

            while (!sawEncodeEOS) {
                // 1) Feed compressed input into the decoder until we pass endUs.
                if (!sawInputEOS) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val dst = decoder.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(dst, 0)
                        val sampleTime = extractor.sampleTime
                        if (size < 0 || sampleTime > endUs) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // 2) Drain decoded PCM; lazily start the encoder from the real output
                // format, then forward only the samples inside [startUs, endUs).
                if (!sawDecodeEOS) {
                    val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val fmt = decoder.outputFormat
                            val sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            val channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            bytesPerUs = sampleRate.toDouble() * channels * 2 / 1_000_000.0
                            val outFormat = MediaFormat.createAudioFormat(
                                MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels,
                            ).apply {
                                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                                setInteger(MediaFormat.KEY_BIT_RATE, OUTPUT_BITRATE)
                                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
                            }
                            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                                configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                                start()
                            }
                            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                        }
                        outIndex >= 0 -> {
                            val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            if (info.size > 0 && info.presentationTimeUs >= startUs &&
                                info.presentationTimeUs < endUs && encoder != null
                            ) {
                                val pcm = decoder.getOutputBuffer(outIndex)!!
                                pcm.position(info.offset)
                                pcm.limit(info.offset + info.size)
                                feedEncoder(pcm, info.presentationTimeUs - startUs)
                            }
                            decoder.releaseOutputBuffer(outIndex, false)
                            if (eos) {
                                sawDecodeEOS = true
                                // If the decoder finished without ever emitting an output
                                // format, the encoder was never created — bail instead of
                                // spinning `while (!sawEncodeEOS)` forever (the old hang
                                // that made an upload "never finish").
                                if (encoder == null) error("Tak ada audio yang bisa dipotong")
                                signalEncoderEOS()
                            }
                        }
                    }
                }

                // 3) Push whatever the encoder produced to the muxer.
                if (encoder != null) drainEncoder()
            }

            // A valid .m4a must have had at least one encoded frame written.
            if (!muxerStarted) error("Gagal memotong audio (tidak ada keluaran)")
            return outFile
        } catch (t: Throwable) {
            runCatching { outFile.delete() }
            throw t
        } finally {
            runCatching { decoder.stop() }
            runCatching { decoder.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }
}
