package com.example.syntra.net

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The sound of the logo assembling — synthesized, not shipped as an audio file.
 *
 * WHY SYNTHESIZED. A launch sound is three short tones and a chord; shipping that as
 * an .ogg would add a binary asset nobody can review in a diff, and we just spent
 * real effort getting the APK from 46 MB to 8 MB. This is a few hundred lines of
 * float maths that weighs nothing and can be tuned by reading it.
 *
 * THE SOUND. Each piece locking plays one note of an A-major triad — A4, C#5, E5 —
 * so the assembly is literally a chord being built one piece at a time. When the last
 * piece lands, all three ring together an octave up. Each note is a sine with a
 * gentle attack and an exponential decay (a plucked-bell shape, not a beep), plus a
 * quiet second harmonic so it has a body rather than a hollow tone.
 *
 * Deliberately quiet ([GAIN]) and short. A splash sound that startles is a splash
 * sound people disable.
 */
object SplashSound {

    private const val SAMPLE_RATE = 44_100
    private const val GAIN = 0.22f

    /** A-major triad: the three pieces, then all of them an octave up. */
    private const val NOTE_CHAT = 440.00f    // A4
    private const val NOTE_SHORTS = 554.37f  // C#5
    private const val NOTE_ROOMS = 659.25f   // E5

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Which piece just locked into place. */
    enum class Piece { CHAT, SHORTS, ROOMS }

    fun play(piece: Piece) {
        val freq = when (piece) {
            Piece.CHAT -> NOTE_CHAT
            Piece.SHORTS -> NOTE_SHORTS
            Piece.ROOMS -> NOTE_ROOMS
        }
        emit(durationMs = 420) { t, dur -> bell(t, dur, freq) }
    }

    /** The moment the S is whole: the full triad, an octave up, ringing longer. */
    fun playComplete() {
        emit(durationMs = 900) { t, dur ->
            (bell(t, dur, NOTE_CHAT * 2f) +
                bell(t, dur, NOTE_SHORTS * 2f) +
                bell(t, dur, NOTE_ROOMS * 2f)) / 3f
        }
    }

    /**
     * One struck note: a sine plus a quieter octave, under an attack/decay envelope.
     *
     * The 6 ms attack is what stops it clicking — jumping straight to full amplitude
     * puts a step in the waveform, and a step is a click.
     */
    private fun bell(t: Float, duration: Float, freq: Float): Float {
        val attack = 0.006f
        val env = when {
            t < attack -> t / attack
            else -> exp(-3.2f * (t - attack) / duration)
        }
        val w = 2f * PI.toFloat() * freq
        return env * (sin(w * t) + 0.28f * sin(2f * w * t))
    }

    /**
     * Renders [sample] to PCM and plays it once, off the main thread.
     *
     * Every call gets its own AudioTrack and releases it on completion, so overlapping
     * notes simply mix in the system output — no shared state to get out of step, and
     * nothing left holding an audio device after the splash is gone.
     */
    private fun emit(durationMs: Int, sample: (t: Float, duration: Float) -> Float) {
        scope.launch {
            runCatching {
                val duration = durationMs / 1000f
                val count = (SAMPLE_RATE * duration).toInt()
                val pcm = ShortArray(count)
                for (i in 0 until count) {
                    val t = i / SAMPLE_RATE.toFloat()
                    val v = (sample(t, duration) * GAIN).coerceIn(-1f, 1f)
                    pcm[i] = (v * Short.MAX_VALUE).toInt().toShort()
                }

                val bytes = pcm.size * 2
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(bytes)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.setNotificationMarkerPosition(pcm.size)
                track.setPlaybackPositionUpdateListener(
                    object : AudioTrack.OnPlaybackPositionUpdateListener {
                        override fun onMarkerReached(t: AudioTrack?) {
                            runCatching { t?.release() }
                        }

                        override fun onPeriodicNotification(t: AudioTrack?) = Unit
                    },
                )
                track.play()
            }
        }
    }
}
