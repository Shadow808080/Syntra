package com.example.syntra.net

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
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

    /**
     * The queue the current track came from — now readable, so the now-playing screen
     * can show what is coming instead of making people go back to the list to find out.
     */
    var queue by mutableStateOf<List<MusicTrack>>(emptyList())
        private set
    var index by mutableStateOf(-1)
        private set

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

    /**
     * How the queue plays, as ONE setting rather than two switches.
     *
     * Shuffle and repeat used to be separate toggles, which let you set combinations
     * that contradict each other and made you read two controls to know what would
     * happen next. This is a single cycle: play through · shuffle · loop the queue ·
     * loop this track.
     */
    enum class Mode { ORDER, SHUFFLE, REPEAT_ALL, REPEAT_ONE }

    var mode by mutableStateOf(Mode.ORDER)
        private set

    /** True while the queue should be traversed at random. */
    val shuffle: Boolean get() = mode == Mode.SHUFFLE

    fun cycleMode() {
        mode = when (mode) {
            Mode.ORDER -> Mode.SHUFFLE
            Mode.SHUFFLE -> Mode.REPEAT_ALL
            Mode.REPEAT_ALL -> Mode.REPEAT_ONE
            Mode.REPEAT_ONE -> Mode.ORDER
        }
    }

    /**
     * 0f..1f, the app's own level — independent of the device volume keys, so turning
     * music down doesn't take call audio with it.
     *
     * The scale is not linear-to-the-player: the MIDDLE is the phone's own level.
     * Below 0.5 attenuates the stream; above 0.5 there is nothing left to give (the
     * player clamps at 1.0), so the extra comes from a [LoudnessEnhancer] on the
     * session, up to +6 dB — roughly twice as loud — at the top of the slider.
     */
    const val UNITY = 0.5f

    private var volumeState by mutableStateOf(UNITY)
    var volume: Float
        get() = volumeState
        set(v) {
            volumeState = v.coerceIn(0f, 1f)
            applyVolume()
        }

    /** Extra gain above the device level. 600 mB = +6 dB ≈ double the loudness. */
    private const val MAX_BOOST_MB = 600

    private var booster: LoudnessEnhancer? = null

    private fun applyVolume() {
        val p = player ?: return
        val v = volumeState
        runCatching {
            if (v <= UNITY) p.setVolume(v / UNITY, v / UNITY) else p.setVolume(1f, 1f)
        }
        val gain = if (v <= UNITY) 0 else (((v - UNITY) / (1f - UNITY)) * MAX_BOOST_MB).toInt()
        runCatching {
            // Built lazily: an enhancer at 0 mB is a no-op, so most sessions never
            // need one at all, and the effect can legitimately fail to attach on
            // devices that don't offer it — the base volume still works either way.
            if (booster == null && gain > 0) {
                booster = LoudnessEnhancer(p.audioSessionId).apply { enabled = true }
            }
            booster?.setTargetGain(gain)
        }
    }

    private fun releaseBooster() {
        runCatching { booster?.release() }
        booster = null
    }

    val hasNext: Boolean get() = queue.isNotEmpty() && index < queue.lastIndex
    val hasPrevious: Boolean get() = queue.isNotEmpty() && index > 0

    /**
     * Whether [next] will actually move — the skip button's enabled look comes from
     * here, not from [hasNext], because next wraps at the end of the queue. Reading
     * "is there a later track" would grey out a button that still works.
     */
    val canNext: Boolean get() = queue.size > 1 || hasNext

    /** Jumps straight to a track already in the queue (tapped in the queue list). */
    fun playAt(context: Context, position: Int) {
        if (position !in queue.indices) return
        if (position == index && player != null) { togglePlayPause(); return }
        index = position
        start(context, queue[position])
    }

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
        index = when {
            shuffle && queue.size > 1 -> randomOtherIndex()
            hasNext -> index + 1
            // Pressing "next" on the last track wraps, whatever the repeat mode says.
            // Repeat governs what happens when a track ENDS on its own; an explicit
            // skip is a request, and answering it with nothing reads as a dead button.
            queue.size > 1 -> 0
            else -> return
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
        // The enhancer is bound to this player's audio session, so it goes first.
        releaseBooster()
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
        // Same as stop(): the effect must not outlive the session it is attached to.
        releaseBooster()
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
                // Carry the chosen level onto the new player — it belongs to the
                // session, not to whichever track happens to be loaded.
                applyVolume()
                p.start()
                isPlaying = true
            }
            mp.setOnCompletionListener {
                when {
                    // Repeat the same track.
                    mode == Mode.REPEAT_ONE -> { seekTo(0); player?.start(); isPlaying = true }
                    // Shuffle or sequential advance.
                    shuffle && queue.size > 1 -> next(appCtx)
                    hasNext -> next(appCtx)
                    // End of the queue: wrap when looping, stop when playing through.
                    mode == Mode.REPEAT_ALL && queue.size > 1 -> { index = 0; start(appCtx, queue[0]) }
                    mode == Mode.REPEAT_ALL -> { seekTo(0); player?.start(); isPlaying = true }
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
