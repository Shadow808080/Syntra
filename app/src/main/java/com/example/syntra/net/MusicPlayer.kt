package com.example.syntra.net

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App-wide music playback. A single [MediaPlayer] plays one preview at a time,
 * with a queue so "next / previous" work and a track auto-advances when it ends.
 *
 * State is exposed as Compose state so the mini-player and the now-playing screen
 * recompose as playback moves — the same pattern as CallEngine/VoiceEngine. Mount
 * the mini-player once at the app root; it renders whenever [current] is non-null.
 */
object MusicPlayer {

    private var player: MediaPlayer? = null

    /** The queue the current track came from, for next/previous. */
    private var queue: List<MusicTrack> = emptyList()
    private var index: Int = -1

    var current by mutableStateOf<MusicTrack?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var preparing by mutableStateOf(false)
        private set
    /** 0f..1f playback position, driven by [tick]. */
    var progress by mutableStateOf(0f)
        private set
    var positionMs by mutableStateOf(0)
        private set
    var durationMs by mutableStateOf(0)
        private set

    val hasNext: Boolean get() = queue.isNotEmpty() && index < queue.lastIndex
    val hasPrevious: Boolean get() = queue.isNotEmpty() && index > 0

    /**
     * Plays [track], setting [queue] as the surrounding list so next/previous
     * traverse it. Re-tapping the current track toggles play/pause instead of
     * restarting.
     */
    fun play(context: Context, track: MusicTrack, queue: List<MusicTrack> = listOf(track)) {
        if (current?.id == track.id && player != null) {
            togglePlayPause()
            return
        }
        this.queue = queue.ifEmpty { listOf(track) }
        this.index = this.queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        start(context, track)
    }

    fun togglePlayPause() {
        val p = player ?: return
        runCatching {
            if (p.isPlaying) {
                p.pause(); isPlaying = false
            } else {
                p.start(); isPlaying = true
            }
        }
    }

    fun next(context: Context) {
        if (!hasNext) return
        index++
        start(context, queue[index])
    }

    fun previous(context: Context) {
        // Like most players: >3s in, restart the current track; otherwise go back.
        if (positionMs > 3000 || !hasPrevious) {
            seekTo(0)
            player?.start()
            isPlaying = true
            return
        }
        index--
        start(context, queue[index])
    }

    /** Seek to a fraction 0f..1f of the preview. */
    fun seekToFraction(fraction: Float) {
        val p = player ?: return
        val d = durationMs
        if (d <= 0) return
        val target = (fraction.coerceIn(0f, 1f) * d).toInt()
        runCatching { p.seekTo(target) }
        positionMs = target
        progress = fraction.coerceIn(0f, 1f)
    }

    private fun seekTo(ms: Int) {
        runCatching { player?.seekTo(ms) }
        positionMs = ms
        progress = if (durationMs > 0) ms.toFloat() / durationMs else 0f
    }

    /** Poll the real position — call ~5×/sec from a LaunchedEffect while playing. */
    fun tick() {
        val p = player ?: return
        runCatching {
            positionMs = p.currentPosition
            if (durationMs > 0) progress = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        }
    }

    fun stop() {
        runCatching { player?.release() }
        player = null
        current = null
        isPlaying = false
        preparing = false
        progress = 0f
        positionMs = 0
        durationMs = 0
        queue = emptyList()
        index = -1
    }

    private fun start(context: Context, track: MusicTrack) {
        runCatching { player?.release() }
        player = null
        current = track
        isPlaying = false
        preparing = true
        progress = 0f
        positionMs = 0
        durationMs = 0

        if (track.previewUrl.isBlank()) { preparing = false; return }

        val appCtx = context.applicationContext
        runCatching {
            // A local var, not apply{}: inside an apply block `isPlaying` would bind to
            // MediaPlayer.isPlaying (a read-only val), not this object's state.
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            mp.setDataSource(track.previewUrl)
            mp.setOnPreparedListener { p ->
                preparing = false
                durationMs = runCatching { p.duration }.getOrDefault(0)
                p.start()
                isPlaying = true
            }
            mp.setOnCompletionListener {
                // Auto-advance through the queue; stop cleanly at the end.
                if (hasNext) next(appCtx) else { isPlaying = false; progress = 1f }
            }
            mp.setOnErrorListener { p, _, _ ->
                preparing = false; isPlaying = false
                runCatching { p.reset() }
                true
            }
            player = mp
            mp.prepareAsync()
        }.onFailure { preparing = false }
    }
}
