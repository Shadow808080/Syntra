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

    /** Shuffle auto-advance / skip-next through the queue. */
    var shuffle by mutableStateOf(false)
        private set
    /** Repeat the current track when it ends. */
    var repeatOne by mutableStateOf(false)
        private set

    val hasNext: Boolean get() = queue.isNotEmpty() && index < queue.lastIndex
    val hasPrevious: Boolean get() = queue.isNotEmpty() && index > 0

    fun toggleShuffle() { shuffle = !shuffle }
    fun toggleRepeat() { repeatOne = !repeatOne }

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

    /**
     * Pause because another audio source is starting — a reel/story video, a voice
     * note, or a call. Music does not auto-resume afterwards (the user taps play), so
     * the two never talk over each other. No-op if nothing is playing.
     */
    fun pauseForExternalAudio() {
        val p = player ?: return
        if (isPlaying) { runCatching { p.pause() }; isPlaying = false }
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
        if (queue.isEmpty()) return
        index = if (shuffle && queue.size > 1) randomOtherIndex() else {
            if (!hasNext) return
            index + 1
        }
        start(context, queue[index])
    }

    private fun randomOtherIndex(): Int {
        if (queue.size <= 1) return index
        var n = index
        while (n == index) n = (queue.indices).random()
        return n
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
            val src = track.previewUrl
            if (src.startsWith("content://") || src.startsWith("file://")) {
                // A local file the user picked from device storage.
                mp.setDataSource(appCtx, android.net.Uri.parse(src))
            } else {
                // Play the on-disk copy when we already have one, and warm the cache
                // otherwise. Music was the last thing still re-downloading itself on
                // every single play — replaying a track you like cost the full stream
                // each time, and each replay started with a buffering pause.
                val local = VideoCache.cachedFile(appCtx, src)
                if (local != null) {
                    mp.setDataSource(local.absolutePath)
                } else {
                    mp.setDataSource(src)
                    VideoCache.prefetch(appCtx, src)
                }
            }
            mp.setOnPreparedListener { p ->
                preparing = false
                durationMs = runCatching { p.duration }.getOrDefault(0)
                p.start()
                isPlaying = true
            }
            mp.setOnCompletionListener {
                when {
                    // Repeat the same track.
                    repeatOne -> { seekTo(0); player?.start(); isPlaying = true }
                    // Shuffle or sequential advance; stop cleanly at the very end.
                    shuffle && queue.size > 1 -> next(appCtx)
                    hasNext -> next(appCtx)
                    else -> { isPlaying = false; progress = 1f }
                }
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
