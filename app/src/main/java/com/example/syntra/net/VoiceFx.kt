package com.example.syntra.net

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.livekit.android.audio.AudioProcessorInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor

/**
 * "Mode suara" — a voice changer for outgoing voice notes.
 *
 * Each preset is a pitch multiplier applied to the recorded audio WHILE PRESERVING
 * its duration (so a 5-second note stays 5 seconds, only the pitch changes). This is a
 * stylised/novelty effect, not studio-grade: it raises a voice to sound higher
 * ("perempuan") or lowers it to sound deeper ("pria"), etc.
 */
enum class VoiceEffect(val id: String, val label: String, val emoji: String, val pitch: Float) {
    NORMAL("normal", "Normal", "🎙️", 1.0f),
    PEREMPUAN("perempuan", "Perempuan", "👩", 1.32f),
    PRIA("pria", "Pria", "👨", 0.80f),
    ANAK("anak", "Anak-anak", "🧒", 1.55f),
    BERAT("berat", "Berat", "🐻", 0.70f),
    ;

    companion object {
        fun byId(id: String?): VoiceEffect = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

/** Remembers the chosen voice mode on this device (applies to every voice note). */
object VoiceFxStore {
    private const val PREFS = "syntra_voice_fx"
    private const val KEY = "effect"

    fun get(context: Context): VoiceEffect {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return VoiceEffect.byId(id)
    }

    fun set(context: Context, effect: VoiceEffect) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, effect.id).apply()
    }
}

/**
 * Applies a [VoiceEffect] to an AAC/m4a voice note, returning a new .m4a file.
 *
 * Pipeline: decode AAC → 16-bit PCM (MediaExtractor + MediaCodec), pitch-shift the PCM
 * (per channel) keeping the duration, then re-encode PCM → AAC/m4a (MediaCodec +
 * MediaMuxer). On any failure it returns null so the caller can fall back to sending
 * the untouched recording.
 */
object VoiceProcessor {

    /** Decoded 16-bit PCM plus its layout. */
    private data class Pcm(val samples: ShortArray, val sampleRate: Int, val channels: Int)

    fun process(context: Context, input: File, effect: VoiceEffect): File? {
        if (effect == VoiceEffect.NORMAL) return input
        return runCatching {
            val pcm = decode(input) ?: return null
            val shifted = pitchShiftInterleaved(pcm.samples, pcm.channels, effect.pitch)
            val out = File(context.cacheDir, "voicefx-${System.currentTimeMillis()}.m4a")
            encode(Pcm(shifted, pcm.sampleRate, pcm.channels), out)
            out
        }.getOrNull()
    }

    // --- Decode -------------------------------------------------------------

    private fun decode(input: File): Pcm? {
        val extractor = MediaExtractor()
        extractor.setDataSource(input.absolutePath)
        var trackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) { trackIndex = i; break }
        }
        if (trackIndex < 0) { extractor.release(); return null }
        extractor.selectTrack(trackIndex)
        val inFormat = extractor.getTrackFormat(trackIndex)
        val mime = inFormat.getString(MediaFormat.KEY_MIME)!!
        var sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(inFormat, null, null, 0)
        codec.start()

        val pcmBytes = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false
        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val inBuf = codec.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(inBuf, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEOS = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            if (outIndex >= 0) {
                if (info.size > 0) {
                    val outBuf = codec.getOutputBuffer(outIndex)!!
                    outBuf.position(info.offset)
                    outBuf.limit(info.offset + info.size)
                    val chunk = ByteArray(info.size)
                    outBuf.get(chunk)
                    pcmBytes.write(chunk)
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEOS = true
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val f = codec.outputFormat
                sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            }
        }
        codec.stop(); codec.release(); extractor.release()

        val bytes = pcmBytes.toByteArray()
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asShortBuffer().get(shorts)
        return Pcm(shorts, sampleRate, channels)
    }

    // --- Encode -------------------------------------------------------------

    private fun encode(pcm: Pcm, out: File) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, pcm.sampleRate, pcm.channels)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val pcmBytes = ByteArray(pcm.samples.size * 2)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.nativeOrder()).asShortBuffer().put(pcm.samples)

        val bytesPerFrame = 2 * pcm.channels
        val info = MediaCodec.BufferInfo()
        var trackIndex = -1
        var muxerStarted = false
        var inputOffset = 0
        var presentationTimeUs = 0L
        var sawInputEOS = false
        var sawOutputEOS = false
        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val inBuf = codec.getInputBuffer(inIndex)!!
                    inBuf.clear()
                    val remaining = pcmBytes.size - inputOffset
                    if (remaining <= 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEOS = true
                    } else {
                        val chunk = minOf(inBuf.remaining(), remaining)
                        inBuf.put(pcmBytes, inputOffset, chunk)
                        codec.queueInputBuffer(inIndex, 0, chunk, presentationTimeUs, 0)
                        inputOffset += chunk
                        val frames = chunk / bytesPerFrame
                        presentationTimeUs += frames * 1_000_000L / pcm.sampleRate
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            if (outIndex >= 0) {
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                if (info.size > 0 && muxerStarted) {
                    val encoded = codec.getOutputBuffer(outIndex)!!
                    encoded.position(info.offset)
                    encoded.limit(info.offset + info.size)
                    muxer.writeSampleData(trackIndex, encoded, info)
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEOS = true
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                trackIndex = muxer.addTrack(codec.outputFormat)
                muxer.start(); muxerStarted = true
            }
        }
        codec.stop(); codec.release()
        runCatching { muxer.stop() }
        muxer.release()
    }

    // --- Pitch shift (duration-preserving) ----------------------------------

    /** Splits interleaved PCM into channels, pitch-shifts each, re-interleaves. */
    private fun pitchShiftInterleaved(samples: ShortArray, channels: Int, pitch: Float): ShortArray {
        if (channels <= 1) return floatToShort(pitchShift(shortToFloat(samples), pitch))
        val frames = samples.size / channels
        val out = ShortArray(samples.size)
        for (c in 0 until channels) {
            val chan = FloatArray(frames)
            for (i in 0 until frames) chan[i] = samples[i * channels + c] / 32768f
            val shifted = pitchShift(chan, pitch)
            val n = minOf(shifted.size, frames)
            for (i in 0 until n) {
                out[i * channels + c] = (shifted[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }
        }
        return out
    }

    /**
     * Shift pitch by [pitch] (>1 = higher) keeping the duration: time-stretch the signal
     * by [pitch] with WSOLA, then resample it back down by [pitch]. Net effect is the
     * same number of samples at a scaled pitch.
     */
    private fun pitchShift(x: FloatArray, pitch: Float): FloatArray {
        if (x.size < WIN * 2 || pitch == 1.0f) return x
        val stretched = wsola(x, pitch)
        return resample(stretched, pitch, x.size)
    }

    private const val WIN = 1024
    private const val HOP = WIN / 2 // 50% overlap
    private const val SEARCH = 256 // ± samples the WSOLA correlation may shift a frame

    /**
     * WSOLA time-stretch by [s] (output length ≈ x.size * s).
     *
     * Plain overlap-add smears a tonal signal because adjacent frames land out of phase;
     * WSOLA fixes that by sliding each analysis frame within ±[SEARCH] to the position
     * whose samples best continue the previous frame (max cross-correlation), so the
     * waveform stays phase-continuous and the pitch is preserved precisely.
     */
    private fun wsola(x: FloatArray, s: Float): FloatArray {
        val win = FloatArray(WIN) { 0.5f - 0.5f * cos(2.0 * Math.PI * it / (WIN - 1)).toFloat() }
        val anaHop = HOP / s
        val outLen = (x.size * s).toInt() + WIN
        val out = FloatArray(outLen)
        val norm = FloatArray(outLen)
        // The input samples the next frame should smoothly continue from (WIN long).
        var target: FloatArray? = null
        var outPos = 0
        var ana = 0f
        while (true) {
            val center = Math.round(ana)
            if (center + WIN + SEARCH >= x.size || outPos + WIN >= outLen) break
            var best = 0
            val t = target
            if (t != null) {
                var bestScore = -Float.MAX_VALUE
                var off = maxOf(-SEARCH, -center)
                while (off <= SEARCH) {
                    var score = 0f
                    val base = center + off
                    for (k in 0 until WIN) score += x[base + k] * t[k]
                    if (score > bestScore) { bestScore = score; best = off }
                    off += 4
                }
            }
            val st = center + best
            for (k in 0 until WIN) {
                out[outPos + k] += x[st + k] * win[k]
                norm[outPos + k] += win[k]
            }
            val tgtStart = st + HOP
            target = if (tgtStart + WIN <= x.size) x.copyOfRange(tgtStart, tgtStart + WIN) else null
            outPos += HOP
            ana += anaHop
        }
        for (n in out.indices) if (norm[n] > 1e-4f) out[n] /= norm[n]
        val expected = (x.size * s).toInt().coerceAtLeast(1)
        return out.copyOf(minOf(expected, out.size))
    }

    /** Linear resample by [ratio] (>1 = fewer samples), capped to [targetLen]. */
    private fun resample(x: FloatArray, ratio: Float, targetLen: Int): FloatArray {
        val outLen = minOf((x.size / ratio).toInt(), targetLen).coerceAtLeast(1)
        val out = FloatArray(outLen)
        for (j in 0 until outLen) {
            val pos = j * ratio
            val i0 = pos.toInt()
            if (i0 >= x.size - 1) { out[j] = x[x.size - 1]; continue }
            val frac = pos - i0
            out[j] = x[i0] + (x[i0 + 1] - x[i0]) * frac
        }
        return out
    }

    private fun shortToFloat(s: ShortArray): FloatArray = FloatArray(s.size) { s[it] / 32768f }

    private fun floatToShort(f: FloatArray): ShortArray =
        ShortArray(f.size) { (f[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
}

// ---------------------------------------------------------------------------
// "Mode suara" for VOICE ROOMS — real-time pitch shift on the outgoing mic.
// ---------------------------------------------------------------------------

/**
 * The voice mode currently applied to MY microphone in a voice room.
 *
 * Unlike the chat voice note (which processes a finished file), a room is live: the
 * pitch is read by the audio thread every frame via [pitch], while [effect] drives the
 * picker UI. A room disguise is per-session — [reset] on leaving a room.
 */
object RoomVoiceFx {
    var effect by mutableStateOf(VoiceEffect.NORMAL)
        private set

    /** Read on the real-time audio thread — keep it a plain volatile float. */
    @Volatile
    var pitch: Float = 1f
        private set

    fun set(e: VoiceEffect) {
        effect = e
        pitch = e.pitch
    }

    fun reset() = set(VoiceEffect.NORMAL)
}

/**
 * LiveKit capture post-processor that pitch-shifts the outgoing microphone in real time,
 * so the room hears the chosen [RoomVoiceFx] voice. Bypasses entirely at Normal pitch.
 *
 * Uses a streaming granular (two-tap crossfading delay line) pitch shifter: it keeps the
 * tempo, needs no look-ahead beyond one grain (~40 ms), and carries state across the
 * 10 ms frames LiveKit hands it. A novelty-grade disguise, not studio quality.
 */
class RoomVoicePitchProcessor : AudioProcessorInterface {
    private var channels = 1
    private var shifters: Array<GranularPitch> = arrayOf(GranularPitch(48_000))

    override fun isEnabled(): Boolean = true
    override fun getName(): String = "syntra-room-voice-fx"

    override fun initializeAudioProcessing(sampleRateHz: Int, numChannels: Int) {
        channels = numChannels.coerceAtLeast(1)
        shifters = Array(channels) { GranularPitch(sampleRateHz) }
    }

    override fun resetAudioProcessing(newRate: Int) {
        shifters = Array(channels) { GranularPitch(newRate) }
    }

    override fun processAudio(numBands: Int, numFrames: Int, buffer: ByteBuffer) {
        val p = RoomVoiceFx.pitch
        // Normal (or near it): leave the mic untouched — zero cost, zero artefacts.
        if (p in 0.99f..1.01f) return

        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val view = buffer.asShortBuffer()
        val total = view.remaining()
        if (total <= 0) return
        val arr = ShortArray(total)
        view.get(arr)
        if (channels <= 1) {
            shifters[0].process(arr, 0, 1, total, p)
        } else {
            for (c in 0 until channels) shifters[c].process(arr, c, channels, total, p)
        }
        view.rewind()
        view.put(arr)
    }
}

/**
 * A real-time, tempo-preserving pitch shifter over one channel.
 *
 * Classic two-tap crossfading delay line: two read pointers, half a grain apart, sweep
 * the delay buffer at rate `pitch`; triangular windows (which sum to 1) crossfade them
 * so the wrap-around discontinuity is inaudible. Reading faster than writing raises the
 * pitch; slower lowers it. All state lives here so it survives frame boundaries.
 */
private class GranularPitch(sampleRate: Int) {
    private val grain: Int = (sampleRate * 0.040f).toInt().coerceAtLeast(256)
    private val bufSize: Int = Integer.highestOneBit(grain * 4).let { if (it < grain * 4) it * 2 else it }
    private val mask: Int = bufSize - 1
    private val buf = FloatArray(bufSize)
    private var writeIdx = 0
    private var delay = 0f

    /** Shift `data[offset], data[offset+stride], …` (one channel) in place. */
    fun process(data: ShortArray, offset: Int, stride: Int, total: Int, pitch: Float) {
        val half = grain / 2f
        var i = offset
        while (i < total) {
            buf[writeIdx and mask] = data[i] / 32768f
            val d1 = delay
            var d2 = delay + half
            if (d2 >= grain) d2 -= grain
            val out = tap(d1) * window(d1, half) + tap(d2) * window(d2, half)
            data[i] = (out.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            // Read pointer advances at `pitch` per output sample (readPos = writeIdx - delay).
            delay += (1f - pitch)
            if (delay >= grain) delay -= grain
            if (delay < 0f) delay += grain
            writeIdx++
            i += stride
        }
    }

    /** Fractionally-interpolated sample `delay` behind the write head. */
    private fun tap(d: Float): Float {
        val pos = writeIdx - d
        val i0 = floor(pos).toInt()
        val frac = pos - i0
        val a = buf[i0 and mask]
        val b = buf[(i0 + 1) and mask]
        return a + (b - a) * frac
    }

    /** Triangular window: 0 at the grain edges, 1 at its centre. */
    private fun window(d: Float, half: Float): Float = (1f - abs(d - half) / half).coerceIn(0f, 1f)
}
