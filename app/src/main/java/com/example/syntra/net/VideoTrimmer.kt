package com.example.syntra.net

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Cuts a video to a time range WITHOUT re-encoding — it copies the already-compressed
 * samples straight into a new MP4 (MediaExtractor → MediaMuxer). That's fast and
 * lossless, at the cost of keyframe-level precision: the cut-in snaps back to the
 * nearest keyframe at or before the requested start, so the clip may begin a fraction
 * of a second earlier than asked. This is the usual trade every quick trimmer makes.
 *
 * Output is always MP4 (H.264/H.265 + AAC — what phone cameras record). A source the
 * MP4 muxer can't hold (e.g. VP8/WebM) makes [trim] return null; the caller then falls
 * back to sending the clip untrimmed rather than failing the send.
 */
object VideoTrimmer {

    /** Total duration in milliseconds, or 0 if it can't be read. */
    fun durationMs(context: Context, uri: Uri): Long = runCatching {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(context, uri)
        val d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        mmr.release()
        d
    }.getOrDefault(0L)

    /**
     * Trims [uri] to [[startMs], [endMs]] and returns the new MP4 file, or null on any
     * failure (the caller should then send the original untouched).
     */
    suspend fun trim(context: Context, uri: Uri, startMs: Long, endMs: Long): File? = withContext(Dispatchers.IO) {
        val startUs = startMs * 1000
        val endUs = endMs * 1000
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        val outFile = File(context.cacheDir, "trim-${System.currentTimeMillis()}.mp4")
        try {
            extractor.setDataSource(context, uri, null)
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Select every audio/video track and mirror it into the muxer, remembering
            // the source→dest index mapping so each sample lands in the right track.
            val indexMap = HashMap<Int, Int>()
            var maxInputSize = 1 shl 20
            var rotation = 0
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    indexMap[i] = muxer.addTrack(format)
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        maxInputSize = maxOf(maxInputSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                    }
                    if (mime.startsWith("video/") && format.containsKey(MediaFormat.KEY_ROTATION)) {
                        rotation = format.getInteger(MediaFormat.KEY_ROTATION)
                    }
                }
            }
            if (indexMap.isEmpty()) return@withContext null

            // Keep the clip upright: carry the source rotation onto the output.
            if (rotation == 0) {
                runCatching {
                    val mmr = MediaMetadataRetriever()
                    mmr.setDataSource(context, uri)
                    rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    mmr.release()
                }
            }
            runCatching { muxer.setOrientationHint(rotation) }

            val buffer = ByteBuffer.allocate(maxInputSize)
            val info = MediaCodec.BufferInfo()
            muxer.start()

            // Start from the keyframe at/just before the cut-in so decoding is clean.
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            // Rebase every timestamp on the first sample we actually write, so the clip
            // begins near t=0 instead of carrying the source's absolute offset.
            var baseUs = -1L
            var wroteAny = false
            while (true) {
                val sampleTime = extractor.sampleTime
                if (sampleTime < 0) break // end of stream
                if (sampleTime > endUs) break
                val src = extractor.sampleTrackIndex
                val dst = indexMap[src]
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                if (dst != null) {
                    if (baseUs < 0) baseUs = sampleTime
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = (sampleTime - baseUs).coerceAtLeast(0)
                    info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                    muxer.writeSampleData(dst, buffer, info)
                    wroteAny = true
                }
                extractor.advance()
            }
            muxer.stop()
            if (wroteAny && outFile.length() > 0) outFile else null.also { outFile.delete() }
        } catch (_: Exception) {
            runCatching { outFile.delete() }
            null
        } finally {
            runCatching { extractor.release() }
            runCatching { muxer?.release() }
        }
    }
}
