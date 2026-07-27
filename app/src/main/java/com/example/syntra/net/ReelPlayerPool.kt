package com.example.syntra.net

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * A tiny pool of reusable [ExoPlayer] instances for the Shorts feed.
 *
 * WHY A POOL. Building an ExoPlayer allocates a renderer stack and, on `prepare()`,
 * acquires a hardware `MediaCodec`. On a low-end phone that handshake costs a good
 * fraction of a second — and the feed used to pay it on **every single swipe**,
 * because each page built its own player and released it on the way out. That is the
 * stall people feel as "the video takes a moment to appear". Recycling the instance
 * keeps the codec warm, so a swipe only has to point an existing player at a new URL.
 *
 * WHY ONLY [MAX_IDLE] OF THEM. Cheap devices commonly expose **one or two** hardware
 * AVC decoders in total. Holding more prepared players than that doesn't buy
 * smoothness, it buys `MediaCodec` "insufficient resources" failures. Two — the reel
 * you're watching and the one you're about to — is the sweet spot.
 *
 * Every player is built with buffering tuned for short vertical video (see
 * [loadControl]) and reads through [ReelCache], so playback is download-once.
 */
@OptIn(UnstableApi::class)
object ReelPlayerPool {

    /** Retained-but-unbound players. See the class doc for why this is deliberately small. */
    private const val MAX_IDLE = 2

    private val idle = ArrayDeque<ExoPlayer>()

    /**
     * Buffering sized for 15–60 second clips instead of ExoPlayer's default, which is
     * built for long-form video and will happily hold ~50 seconds of it in memory.
     *
     * The number that matters most on a slow phone is [BUFFER_FOR_PLAYBACK_MS]: it is
     * how much has to arrive before the first frame plays. At 300 ms a reel starts
     * almost immediately; the default 2.5 s is most of the "why is it not playing yet"
     * feeling. The small ceiling also keeps three pages' worth of buffers from
     * competing for a modest heap.
     */
    private fun loadControl() = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 2_000,
            /* maxBufferMs = */ 10_000,
            /* bufferForPlaybackMs = */ 300,
            /* bufferForPlaybackAfterRebufferMs = */ 1_500,
        )
        // Time, not bytes, decides when we have enough — a high-bitrate clip should
        // still start after 300 ms of video rather than after a fixed byte count.
        .setPrioritizeTimeOverSizeThresholds(true)
        // No back-buffer: nobody scrubs backwards through a 20-second reel, and it is
        // pure resident memory.
        .setBackBuffer(0, false)
        .build()

    private fun build(context: Context): ExoPlayer =
        ExoPlayer.Builder(context.applicationContext)
            .setLoadControl(loadControl())
            .setRenderersFactory(
                DefaultRenderersFactory(context.applicationContext)
                    // If the hardware decoder is busy or chokes on a stream, drop to a
                    // software one instead of failing the reel outright. Old devices
                    // hit this often enough to matter.
                    .setEnableDecoderFallback(true),
            )
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(ReelCache.dataSourceFactory(context)),
            )
            .build()

    /**
     * A player ready to be pointed at a URL — recycled if one is free, freshly built
     * otherwise. The caller owns it until it hands it back to [recycle].
     */
    @Synchronized
    fun acquire(context: Context): ExoPlayer =
        idle.removeFirstOrNull() ?: build(context)

    /**
     * Hand a player back. It is stopped and emptied (so it holds no codec and no
     * buffers) but kept alive for the next page — unless the shelf is full, in which
     * case it is genuinely released.
     *
     * Callers MUST remove their own listeners first; the pool cannot know about them.
     */
    @Synchronized
    fun recycle(player: ExoPlayer) {
        val reusable = runCatching {
            player.playWhenReady = false
            player.stop()
            player.clearMediaItems()
            player.clearVideoSurface()
        }.isSuccess
        if (!reusable || idle.size >= MAX_IDLE) {
            runCatching { player.release() }
            return
        }
        idle.addLast(player)
    }

    /** Tear the shelf down — leaving the Shorts tab shouldn't hold decoders open. */
    @Synchronized
    fun releaseAll() {
        idle.forEach { runCatching { it.release() } }
        idle.clear()
    }
}
