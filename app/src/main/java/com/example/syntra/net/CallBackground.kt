package com.example.syntra.net

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import livekit.org.webrtc.JavaI420Buffer
import livekit.org.webrtc.VideoFrame
import livekit.org.webrtc.VideoProcessor
import livekit.org.webrtc.VideoSink
import java.nio.ByteBuffer

/**
 * Replaces or blurs what is behind you on a video call.
 *
 * Sits in the capture path as a WebRTC [VideoProcessor], so the effect is applied
 * once, before encoding — everyone in the call sees it, and it costs one pass rather
 * than one per viewer.
 *
 * Three decisions keep this affordable on a cheap phone:
 *
 *  1. **It works in YUV, never in Bitmap.** Frames arrive as I420 and must leave as
 *     I420. Converting to ARGB and back for every frame would cost more than the
 *     effect itself, so the compositing is done straight on the Y/U/V planes.
 *  2. **Segmentation runs on a fraction of the frames.** A person does not move much
 *     in 40 ms, so the mask from the last segmented frame is reused for the ones in
 *     between. Segmentation is the expensive part; the compositing is cheap.
 *  3. **It is completely bypassed when off.** A call with no background selected
 *     hands the frame straight through and never touches ML Kit at all.
 */
object CallBackground : VideoProcessor {

    /** What to put behind the person. */
    enum class Mode(val label: String) {
        NONE("Tidak ada"),
        BLUR("Buram"),
        ROOM("Ruangan"),
        SPACE("Luar angkasa"),
        GRADIENT("Gradasi"),
    }

    /** The current choice. Compose state so the picker reflects it live. */
    var mode by mutableStateOf(Mode.NONE)
        private set

    fun select(m: Mode) {
        mode = m
        // Drop the cached background: it is rendered for one mode at one size.
        bgY = null
    }

    /** Turned off when a call ends, so the next call starts clean. */
    fun reset() {
        mode = Mode.NONE
        bgY = null
        mask = null
        frameCount = 0
    }

    private var sink: VideoSink? = null

    private val segmenter by lazy {
        Segmentation.getClient(
            SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
                .build(),
        )
    }

    // Last mask and the size it was produced at. Reused between segmented frames.
    @Volatile private var mask: FloatArray? = null
    @Volatile private var maskW = 0
    @Volatile private var maskH = 0
    @Volatile private var busy = false
    private var frameCount = 0

    /** Segment every Nth frame. At 24fps that is ~8 masks/second — plenty for a head. */
    private const val SEGMENT_EVERY = 3

    // The background, pre-rendered into I420 planes at the frame size, built once per
    // (mode, size) rather than per frame.
    private var bgY: ByteArray? = null
    private var bgU: ByteArray? = null
    private var bgV: ByteArray? = null
    private var bgW = 0
    private var bgH = 0

    override fun setSink(sink: VideoSink?) {
        this.sink = sink
    }

    override fun onCapturerStarted(success: Boolean) = Unit

    override fun onCapturerStopped() {
        mask = null
        frameCount = 0
    }

    override fun onFrameCaptured(frame: VideoFrame) {
        val out = sink ?: return
        if (mode == Mode.NONE) {
            out.onFrame(frame)
            return
        }
        val processed = runCatching { apply(frame) }.getOrNull()
        if (processed == null) {
            // Never drop a frame because the effect failed — a frozen picture is worse
            // than an un-blurred one.
            out.onFrame(frame)
        } else {
            out.onFrame(processed)
            processed.release()
        }
    }

    private fun apply(frame: VideoFrame): VideoFrame? {
        val i420 = frame.buffer.toI420() ?: return null
        try {
            val w = i420.width
            val h = i420.height
            if (w <= 0 || h <= 0) return null

            // Copy the planes out into arrays we can index cheaply. The incoming
            // buffers are direct and may have a row stride wider than the width.
            val y = plane(i420.dataY, i420.strideY, w, h)
            val cw = (w + 1) / 2
            val ch = (h + 1) / 2
            val u = plane(i420.dataU, i420.strideU, cw, ch)
            val v = plane(i420.dataV, i420.strideV, cw, ch)

            maybeSegment(y, u, v, w, h)
            val m = mask ?: return null

            ensureBackground(y, u, v, w, h)
            val by = bgY ?: return null
            val bu = bgU ?: return null
            val bv = bgV ?: return null

            // Luma: blend per pixel using the mask (1 = person, 0 = background).
            for (py in 0 until h) {
                val mrow = (py * maskH / h) * maskW
                val row = py * w
                for (px in 0 until w) {
                    val a = m[mrow + px * maskW / w]
                    if (a >= 0.98f) continue // solidly the person: leave it alone
                    val src = y[row + px].toInt() and 0xFF
                    val dst = by[row + px].toInt() and 0xFF
                    y[row + px] = (dst + (src - dst) * a).toInt().coerceIn(0, 255).toByte()
                }
            }
            // Chroma at half resolution, which is all I420 carries anyway.
            for (py in 0 until ch) {
                val mrow = (py * 2 * maskH / h) * maskW
                val row = py * cw
                for (px in 0 until cw) {
                    val a = m[mrow + (px * 2) * maskW / w]
                    if (a >= 0.98f) continue
                    val su = u[row + px].toInt() and 0xFF
                    val sv = v[row + px].toInt() and 0xFF
                    val du = bu[row + px].toInt() and 0xFF
                    val dv = bv[row + px].toInt() and 0xFF
                    u[row + px] = (du + (su - du) * a).toInt().coerceIn(0, 255).toByte()
                    v[row + px] = (dv + (sv - dv) * a).toInt().coerceIn(0, 255).toByte()
                }
            }

            val buffer = JavaI420Buffer.allocate(w, h)
            copyIn(buffer.dataY, buffer.strideY, y, w, h)
            copyIn(buffer.dataU, buffer.strideU, u, cw, ch)
            copyIn(buffer.dataV, buffer.strideV, v, cw, ch)
            return VideoFrame(buffer, frame.rotation, frame.timestampNs)
        } finally {
            i420.release()
        }
    }

    /** Reads a possibly-strided plane into a tight w*h array. */
    private fun plane(src: ByteBuffer, stride: Int, w: Int, h: Int): ByteArray {
        val out = ByteArray(w * h)
        val dup = src.duplicate()
        for (row in 0 until h) {
            dup.position(row * stride)
            dup.get(out, row * w, w)
        }
        return out
    }

    private fun copyIn(dst: ByteBuffer, stride: Int, src: ByteArray, w: Int, h: Int) {
        for (row in 0 until h) {
            dst.position(row * stride)
            dst.put(src, row * w, w)
        }
    }

    /**
     * Runs ML Kit on every [SEGMENT_EVERY]th frame, on the calling thread.
     *
     * Deliberately blocking rather than fire-and-forget: an async mask that lands two
     * frames later is a mask for where the person WAS, and the halo that produces is
     * far more noticeable than the mask being a frame or two coarse.
     */
    private fun maybeSegment(y: ByteArray, u: ByteArray, v: ByteArray, w: Int, h: Int) {
        frameCount++
        if (mask != null && frameCount % SEGMENT_EVERY != 0) return
        if (busy) return
        busy = true
        try {
            // ML Kit takes NV21: Y plane as-is, then interleaved V,U.
            val nv21 = ByteArray(w * h + ((w + 1) / 2) * ((h + 1) / 2) * 2)
            System.arraycopy(y, 0, nv21, 0, w * h)
            var o = w * h
            val cw = (w + 1) / 2
            val ch = (h + 1) / 2
            for (i in 0 until cw * ch) {
                nv21[o++] = v[i]
                nv21[o++] = u[i]
            }
            val image = InputImage.fromByteArray(nv21, w, h, 0, InputImage.IMAGE_FORMAT_NV21)
            val result: SegmentationMask =
                com.google.android.gms.tasks.Tasks.await(segmenter.process(image))
            val buf = result.buffer
            buf.rewind()
            val mw = result.width
            val mh = result.height
            val arr = FloatArray(mw * mh)
            for (i in 0 until mw * mh) arr[i] = buf.float
            mask = arr
            maskW = mw
            maskH = mh
        } catch (t: Throwable) {
            // Keep whatever mask we had; a stale mask beats a hole in the picture.
        } finally {
            busy = false
        }
    }

    /** Builds the background planes for the current mode, once per mode and size. */
    private fun ensureBackground(y: ByteArray, u: ByteArray, v: ByteArray, w: Int, h: Int) {
        val cw = (w + 1) / 2
        val ch = (h + 1) / 2
        if (mode == Mode.BLUR) {
            // A blur has to follow the live picture, so it is rebuilt every frame —
            // but it is a cheap box blur on a downscaled copy, not a real gaussian.
            bgY = boxBlur(y, w, h, 12)
            bgU = boxBlur(u, cw, ch, 6)
            bgV = boxBlur(v, cw, ch, 6)
            bgW = w; bgH = h
            return
        }
        if (bgY != null && bgW == w && bgH == h) return
        val (py, pu, pv) = renderScene(mode, w, h)
        bgY = py; bgU = pu; bgV = pv; bgW = w; bgH = h
    }

    /**
     * A separable box blur, run on a downscaled copy and stretched back.
     *
     * Blurring at full resolution would be the most expensive thing in this file.
     * Shrinking by 8, blurring, then sampling back up gives a soft field that reads
     * exactly the same behind a person, for a sixty-fourth of the pixels.
     */
    private fun boxBlur(src: ByteArray, w: Int, h: Int, radius: Int): ByteArray {
        val sw = (w / 8).coerceAtLeast(1)
        val sh = (h / 8).coerceAtLeast(1)
        val small = ByteArray(sw * sh)
        for (yy in 0 until sh) {
            val sy = yy * h / sh
            for (xx in 0 until sw) small[yy * sw + xx] = src[sy * w + xx * w / sw]
        }
        val tmp = ByteArray(sw * sh)
        val r = (radius / 8).coerceAtLeast(1)
        // Horizontal then vertical — two 1D passes instead of one 2D kernel.
        for (yy in 0 until sh) {
            for (xx in 0 until sw) {
                var sum = 0
                var n = 0
                for (k in -r..r) {
                    val x2 = xx + k
                    if (x2 in 0 until sw) { sum += small[yy * sw + x2].toInt() and 0xFF; n++ }
                }
                tmp[yy * sw + xx] = (sum / n).toByte()
            }
        }
        for (xx in 0 until sw) {
            for (yy in 0 until sh) {
                var sum = 0
                var n = 0
                for (k in -r..r) {
                    val y2 = yy + k
                    if (y2 in 0 until sh) { sum += tmp[y2 * sw + xx].toInt() and 0xFF; n++ }
                }
                small[yy * sw + xx] = (sum / n).toByte()
            }
        }
        val out = ByteArray(w * h)
        for (yy in 0 until h) {
            val sy = yy * sh / h
            for (xx in 0 until w) out[yy * w + xx] = small[sy * sw + xx * sw / w]
        }
        return out
    }

    /** Draws one of the fixed scenes straight into YUV planes. */
    private fun renderScene(m: Mode, w: Int, h: Int): Triple<ByteArray, ByteArray, ByteArray> {
        val cw = (w + 1) / 2
        val ch = (h + 1) / 2
        val py = ByteArray(w * h)
        val pu = ByteArray(cw * ch)
        val pv = ByteArray(cw * ch)
        when (m) {
            Mode.SPACE -> {
                // Near-black with a scatter of stars. Deterministic seed so the sky
                // does not twinkle between rebuilds.
                java.util.Arrays.fill(py, 16.toByte())
                java.util.Arrays.fill(pu, 128.toByte())
                java.util.Arrays.fill(pv, 128.toByte())
                val rnd = java.util.Random(7)
                repeat(w * h / 900) {
                    val x = rnd.nextInt(w)
                    val yy = rnd.nextInt(h)
                    py[yy * w + x] = (180 + rnd.nextInt(70)).toByte()
                }
            }
            Mode.ROOM -> {
                // A warm wall with a soft vertical falloff — a room, not a photo.
                for (yy in 0 until h) {
                    val t = yy.toFloat() / h
                    val lum = (120 - 45 * t).toInt().coerceIn(16, 235)
                    java.util.Arrays.fill(py, yy * w, yy * w + w, lum.toByte())
                }
                java.util.Arrays.fill(pu, 118.toByte()) // slightly warm
                java.util.Arrays.fill(pv, 138.toByte())
            }
            else -> {
                // Brand gradient, top-left bright to bottom-right deep.
                for (yy in 0 until h) {
                    val t = yy.toFloat() / h
                    val lum = (150 - 90 * t).toInt().coerceIn(16, 235)
                    java.util.Arrays.fill(py, yy * w, yy * w + w, lum.toByte())
                }
                java.util.Arrays.fill(pu, 150.toByte()) // blue-leaning
                java.util.Arrays.fill(pv, 110.toByte())
            }
        }
        return Triple(py, pu, pv)
    }

    /** Unused hook kept for API completeness. */
    @Suppress("unused")
    fun warmUp(context: Context) {
        runCatching { segmenter }
    }

    @Suppress("unused")
    private fun unusedBitmapHook(b: Bitmap) = Unit
}
