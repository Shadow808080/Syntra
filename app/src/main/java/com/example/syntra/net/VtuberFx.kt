package com.example.syntra.net

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import livekit.org.webrtc.CapturerObserver
import livekit.org.webrtc.SurfaceTextureHelper
import livekit.org.webrtc.VideoCapturer
import livekit.org.webrtc.VideoFrame
import livekit.org.webrtc.VideoSink
import kotlin.math.sin

/**
 * "Mode VTuber" for a voice room: a cute 2D character published in place of the
 * camera, its mouth driven by how loud you're talking (lip-sync) rather than by the
 * camera. It rides the SAME LiveKit video path as the camera — [VoiceEngine] publishes
 * the rendered avatar as a CAMERA-source track — so every participant's existing grid
 * renders it with no backend change and no camera permission.
 *
 * Nothing here is the network plumbing; that lives in [VoiceEngine]. This file is the
 * shared state ([VtuberFx]), the drawing ([AvatarRenderer]) and the WebRTC frame source
 * ([AvatarVideoCapturer]).
 */

/** The characters a user can wear. Kept small and hand-drawn — no external assets. */
enum class VtuberAvatar(val id: String, val label: String, val emoji: String) {
    KUCING("cat", "Kucing", "🐱"),
    ANJING("dog", "Anjing", "🐶"),
    ROBOT("robot", "Robot", "🤖"),
    HANTU("ghost", "Hantu", "👻"),
    PANDA("panda", "Panda", "🐼"),
}

/**
 * Shared, process-wide state for the avatar. The capturer's render loop reads [level]
 * and [avatar] every frame (hence @Volatile / plain reads), while the UI observes the
 * Compose-backed [enabled]/[avatar] to keep the picker in sync.
 */
object VtuberFx {
    /** Whether the avatar is currently being published. Mirrors [VoiceEngine.avatarOn]. */
    var enabled by mutableStateOf(false)

    /** The chosen character. Changing it is picked up on the very next rendered frame. */
    var avatar by mutableStateOf(VtuberAvatar.KUCING)

    /** Latest mic loudness 0..1 (fed by [VoiceEngine]); drives the mouth. */
    @Volatile
    var level: Float = 0f

    fun feedLevel(v: Float) { level = v.coerceIn(0f, 1f) }

    /** Back to the default when a room ends, so the next room starts fresh. */
    fun reset() {
        enabled = false
        level = 0f
    }
}

/**
 * Draws the current avatar onto a plain [Canvas]. Stateful only for smoothing: the
 * mouth follows the loudness with a little attack/decay so it doesn't chatter, and the
 * eyes blink on a slow timer. One instance per capture session.
 */
class AvatarRenderer {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var smoothMouth = 0f

    fun draw(canvas: Canvas, w: Int, h: Int, avatar: VtuberAvatar, level: Float, tMs: Long) {
        // Loudness → mouth open, smoothed. audioLevel is small even when speaking, so
        // lift it before clamping; then ease toward the target every frame.
        val target = (level * 2.6f).coerceIn(0f, 1f)
        smoothMouth += (target - smoothMouth) * 0.45f
        val mouth = smoothMouth

        // A slow blink every ~3.2s, closing and opening over ~160ms.
        val cyc = tMs % 3200L
        val eyeOpen = when {
            cyc < 80L -> 1f - (cyc / 80f) * 0.88f
            cyc < 160L -> 0.12f + ((cyc - 80L) / 80f) * 0.88f
            else -> 1f
        }
        // Gentle breathing bob so an idle avatar still feels alive.
        val bob = sin(tMs / 700.0).toFloat() * (h * 0.012f)

        drawBackground(canvas, w, h, avatar)
        canvas.save()
        canvas.translate(0f, bob)
        when (avatar) {
            VtuberAvatar.KUCING -> drawCat(canvas, w, h, mouth, eyeOpen)
            VtuberAvatar.ANJING -> drawDog(canvas, w, h, mouth, eyeOpen)
            VtuberAvatar.ROBOT -> drawRobot(canvas, w, h, mouth, eyeOpen, tMs)
            VtuberAvatar.HANTU -> drawGhost(canvas, w, h, mouth, eyeOpen, tMs)
            VtuberAvatar.PANDA -> drawPanda(canvas, w, h, mouth, eyeOpen)
        }
        canvas.restore()
    }

    // --- backgrounds -------------------------------------------------------

    private fun drawBackground(canvas: Canvas, w: Int, h: Int, avatar: VtuberAvatar) {
        val (top, bottom) = when (avatar) {
            VtuberAvatar.KUCING -> 0xFF2A2350.toInt() to 0xFF12102A.toInt()
            VtuberAvatar.ANJING -> 0xFF23404F.toInt() to 0xFF0F1C25.toInt()
            VtuberAvatar.ROBOT -> 0xFF1E2A3A.toInt() to 0xFF0B121C.toInt()
            VtuberAvatar.HANTU -> 0xFF2C2140.toInt() to 0xFF120C1E.toInt()
            VtuberAvatar.PANDA -> 0xFF243447.toInt() to 0xFF0E161F.toInt()
        }
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
    }

    // --- shared helpers ----------------------------------------------------

    private fun fill(color: Int) { paint.style = Paint.Style.FILL; paint.color = color }
    private fun stroke(color: Int, width: Float) {
        paint.style = Paint.Style.STROKE; paint.color = color; paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND
    }

    /** Two eyes at [ey], each [er] radius, squashed by [open] (blink). */
    private fun drawEyes(canvas: Canvas, cx: Float, ey: Float, dx: Float, er: Float, open: Float, color: Int) {
        fill(color)
        for (sx in floatArrayOf(-1f, 1f)) {
            val ex = cx + sx * dx
            canvas.drawOval(RectF(ex - er, ey - er * open, ex + er, ey + er * open), paint)
        }
        // A little catchlight so the eyes look alive.
        fill(Color.WHITE)
        for (sx in floatArrayOf(-1f, 1f)) {
            val ex = cx + sx * dx
            if (open > 0.5f) canvas.drawCircle(ex + er * 0.3f, ey - er * 0.3f, er * 0.22f, paint)
        }
    }

    /** Mouth as a rounded shape that grows from a line to an open oval with [open]. */
    private fun drawMouth(canvas: Canvas, cx: Float, my: Float, halfW: Float, open: Float, color: Int) {
        val hh = (halfW * 0.15f) + open * halfW * 0.95f
        fill(color)
        canvas.drawRoundRect(RectF(cx - halfW, my - hh, cx + halfW, my + hh), hh, hh, paint)
        if (open > 0.25f) {
            fill(0xFFE86A7C.toInt()) // tongue peeking through
            val th = hh * 0.5f
            canvas.drawRoundRect(RectF(cx - halfW * 0.55f, my + hh - th, cx + halfW * 0.55f, my + hh), th, th, paint)
        }
    }

    /** Round rosy cheeks. */
    private fun drawCheeks(canvas: Canvas, cx: Float, cy: Float, dx: Float, r: Float) {
        fill(0x66FF7EA3)
        canvas.drawCircle(cx - dx, cy, r, paint)
        canvas.drawCircle(cx + dx, cy, r, paint)
    }

    // --- characters --------------------------------------------------------

    private fun drawCat(canvas: Canvas, w: Int, h: Int, mouth: Float, eyeOpen: Float) {
        val cx = w / 2f
        val cy = h * 0.54f
        val fr = h * 0.30f
        val fur = 0xFFF3C08A.toInt()
        // Ears.
        fill(fur)
        for (sx in floatArrayOf(-1f, 1f)) {
            val p = Path()
            val bx = cx + sx * fr * 0.7f
            p.moveTo(bx - fr * 0.28f, cy - fr * 0.75f)
            p.lineTo(bx + sx * fr * 0.35f, cy - fr * 1.25f)
            p.lineTo(bx + fr * 0.28f, cy - fr * 0.7f)
            p.close()
            canvas.drawPath(p, paint)
        }
        fill(0xFFE59BB0.toInt())
        for (sx in floatArrayOf(-1f, 1f)) {
            val bx = cx + sx * fr * 0.72f
            canvas.drawCircle(bx + sx * fr * 0.03f, cy - fr * 0.85f, fr * 0.16f, paint)
        }
        // Face.
        fill(fur)
        canvas.drawCircle(cx, cy, fr, paint)
        drawCheeks(canvas, cx, cy + fr * 0.18f, fr * 0.62f, fr * 0.16f)
        drawEyes(canvas, cx, cy - fr * 0.12f, fr * 0.42f, fr * 0.17f, eyeOpen, 0xFF2B2B33.toInt())
        // Nose + whiskers.
        fill(0xFFD9788F.toInt())
        canvas.drawCircle(cx, cy + fr * 0.16f, fr * 0.07f, paint)
        stroke(0xCCFFFFFF.toInt(), h * 0.006f)
        for (sy in floatArrayOf(-1f, 1f)) for (sx in floatArrayOf(-1f, 1f)) {
            val yy = cy + fr * 0.2f + sy * fr * 0.12f
            canvas.drawLine(cx + sx * fr * 0.28f, yy, cx + sx * fr * 0.9f, yy - sy * fr * 0.05f, paint)
        }
        drawMouth(canvas, cx, cy + fr * 0.42f, fr * 0.22f, mouth, 0xFF6B3A2E.toInt())
    }

    private fun drawDog(canvas: Canvas, w: Int, h: Int, mouth: Float, eyeOpen: Float) {
        val cx = w / 2f
        val cy = h * 0.52f
        val fr = h * 0.30f
        val fur = 0xFFC79968.toInt()
        // Floppy ears behind the face.
        fill(0xFF8A6440.toInt())
        for (sx in floatArrayOf(-1f, 1f)) {
            val ex = cx + sx * fr * 0.95f
            canvas.drawOval(RectF(ex - fr * 0.34f, cy - fr * 0.85f, ex + fr * 0.34f, cy + fr * 0.55f), paint)
        }
        // Face.
        fill(fur)
        canvas.drawCircle(cx, cy, fr, paint)
        // Muzzle.
        fill(0xFFF0DCC2.toInt())
        canvas.drawOval(RectF(cx - fr * 0.5f, cy + fr * 0.05f, cx + fr * 0.5f, cy + fr * 0.85f), paint)
        drawEyes(canvas, cx, cy - fr * 0.15f, fr * 0.4f, fr * 0.16f, eyeOpen, 0xFF2B2B33.toInt())
        // Nose.
        fill(0xFF2B2B33.toInt())
        canvas.drawOval(RectF(cx - fr * 0.14f, cy + fr * 0.12f, cx + fr * 0.14f, cy + fr * 0.3f), paint)
        drawMouth(canvas, cx, cy + fr * 0.55f, fr * 0.2f, mouth, 0xFF6B3A2E.toInt())
    }

    private fun drawRobot(canvas: Canvas, w: Int, h: Int, mouth: Float, eyeOpen: Float, tMs: Long) {
        val cx = w / 2f
        val cy = h * 0.54f
        val fr = h * 0.30f
        // Antenna with a blinking light.
        stroke(0xFF8FA6C4.toInt(), h * 0.012f)
        canvas.drawLine(cx, cy - fr * 0.95f, cx, cy - fr * 1.35f, paint)
        fill(if ((tMs / 500L) % 2L == 0L) 0xFF57E6C3.toInt() else 0xFF2C6F63.toInt())
        canvas.drawCircle(cx, cy - fr * 1.4f, fr * 0.1f, paint)
        // Head plate.
        fill(0xFFB9C6D8.toInt())
        canvas.drawRoundRect(RectF(cx - fr, cy - fr, cx + fr, cy + fr), fr * 0.28f, fr * 0.28f, paint)
        fill(0xFF8496AC.toInt())
        canvas.drawRoundRect(RectF(cx - fr * 0.86f, cy - fr * 0.86f, cx + fr * 0.86f, cy + fr * 0.86f), fr * 0.22f, fr * 0.22f, paint)
        // Glowing screen eyes.
        drawEyes(canvas, cx, cy - fr * 0.12f, fr * 0.42f, fr * 0.18f, eyeOpen, 0xFF57E6C3.toInt())
        // Grille mouth that opens.
        drawMouth(canvas, cx, cy + fr * 0.42f, fr * 0.34f, mouth, 0xFF2C333F.toInt())
        stroke(0xFF57E6C3.toInt(), h * 0.006f)
        if (mouth < 0.2f) {
            canvas.drawLine(cx - fr * 0.34f, cy + fr * 0.42f, cx + fr * 0.34f, cy + fr * 0.42f, paint)
        }
    }

    private fun drawGhost(canvas: Canvas, w: Int, h: Int, mouth: Float, eyeOpen: Float, tMs: Long) {
        val cx = w / 2f
        val cy = h * 0.5f
        val bw = h * 0.28f
        val top = cy - bw * 1.05f
        val bottom = cy + bw * 1.15f
        val p = Path()
        p.moveTo(cx - bw, bottom - bw * 0.2f)
        p.lineTo(cx - bw, top + bw)
        p.cubicTo(cx - bw, top, cx + bw, top, cx + bw, top + bw)
        p.lineTo(cx + bw, bottom - bw * 0.2f)
        // Wavy skirt, drifting over time.
        val waves = 4
        val step = (bw * 2f) / waves
        val phase = sin(tMs / 300.0).toFloat() * (bw * 0.06f)
        for (i in 0 until waves) {
            val x0 = cx + bw - i * step
            val x1 = x0 - step
            val dip = if (i % 2 == 0) bw * 0.22f + phase else bw * 0.05f - phase
            p.quadTo((x0 + x1) / 2f, bottom + dip, x1, bottom - bw * 0.2f)
        }
        p.close()
        fill(0xFFF3F1FB.toInt())
        canvas.drawPath(p, paint)
        drawEyes(canvas, cx, cy - bw * 0.15f, bw * 0.4f, bw * 0.18f, eyeOpen, 0xFF2B2B44.toInt())
        drawMouth(canvas, cx, cy + bw * 0.35f, bw * 0.2f, mouth, 0xFF2B2B44.toInt())
    }

    private fun drawPanda(canvas: Canvas, w: Int, h: Int, mouth: Float, eyeOpen: Float) {
        val cx = w / 2f
        val cy = h * 0.53f
        val fr = h * 0.30f
        // Black ears.
        fill(0xFF222222.toInt())
        for (sx in floatArrayOf(-1f, 1f)) {
            canvas.drawCircle(cx + sx * fr * 0.78f, cy - fr * 0.78f, fr * 0.28f, paint)
        }
        // White face.
        fill(0xFFF7F7F7.toInt())
        canvas.drawCircle(cx, cy, fr, paint)
        // Black eye patches, tilted inward.
        fill(0xFF222222.toInt())
        for (sx in floatArrayOf(-1f, 1f)) {
            canvas.save()
            canvas.rotate(sx * 18f, cx + sx * fr * 0.42f, cy - fr * 0.08f)
            canvas.drawOval(
                RectF(cx + sx * fr * 0.42f - fr * 0.26f, cy - fr * 0.08f - fr * 0.32f,
                    cx + sx * fr * 0.42f + fr * 0.26f, cy - fr * 0.08f + fr * 0.28f), paint,
            )
            canvas.restore()
        }
        drawEyes(canvas, cx, cy - fr * 0.1f, fr * 0.42f, fr * 0.13f, eyeOpen, Color.WHITE)
        fill(0xFF222222.toInt())
        canvas.drawOval(RectF(cx - fr * 0.12f, cy + fr * 0.14f, cx + fr * 0.12f, cy + fr * 0.28f), paint)
        drawMouth(canvas, cx, cy + fr * 0.5f, fr * 0.18f, mouth, 0xFF5A3B34.toInt())
    }
}

/**
 * A WebRTC [VideoCapturer] whose frames are the drawn avatar, not a camera. LiveKit
 * calls [initialize] with a [SurfaceTextureHelper] wired to the room's shared EGL
 * context; we draw onto a [Surface] over that helper's SurfaceTexture with a plain
 * software Canvas, and each posted frame flows to the encoder like any camera frame.
 *
 * Rendering runs on its own [HandlerThread] at a modest frame rate — cheap enough for
 * a mid-range phone, since it's flat 2D shapes at 480×480.
 */
class AvatarVideoCapturer(
    private val width: Int = 480,
    private val height: Int = 480,
    private val fps: Int = 24,
) : VideoCapturer {

    private var helper: SurfaceTextureHelper? = null
    private var observer: CapturerObserver? = null
    private var surface: Surface? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    @Volatile private var running = false
    private val startNs = System.nanoTime()
    private val renderer = AvatarRenderer()

    override fun initialize(sth: SurfaceTextureHelper, ctx: android.content.Context?, obs: CapturerObserver) {
        helper = sth
        observer = obs
    }

    override fun startCapture(w: Int, h: Int, framerate: Int) {
        if (running) return // LiveKit may start us; ignore a redundant second call
        val sth = helper ?: return
        sth.setTextureSize(width, height)
        // The helper turns each posted buffer into a texture-backed VideoFrame; forward
        // it straight to the encoder observer.
        sth.startListening(VideoSink { frame: VideoFrame -> observer?.onFrameCaptured(frame) })
        surface = Surface(sth.surfaceTexture)
        observer?.onCapturerStarted(true)

        running = true
        val t = HandlerThread("AvatarRender").apply { start() }
        thread = t
        val hnd = Handler(t.looper)
        handler = hnd
        val frameMs = (1000L / fps).coerceAtLeast(1L)
        hnd.post(object : Runnable {
            override fun run() {
                if (!running) return
                drawOneFrame()
                hnd.postDelayed(this, frameMs)
            }
        })
    }

    private fun drawOneFrame() {
        val s = surface ?: return
        val canvas = runCatching { s.lockCanvas(null) }.getOrNull() ?: return
        runCatching {
            val tMs = (System.nanoTime() - startNs) / 1_000_000L
            renderer.draw(canvas, width, height, VtuberFx.avatar, VtuberFx.level, tMs)
        }
        runCatching { s.unlockCanvasAndPost(canvas) }
    }

    override fun stopCapture() {
        running = false
        handler?.removeCallbacksAndMessages(null)
        runCatching { helper?.stopListening() }
        observer?.onCapturerStopped()
        thread?.quitSafely()
        thread = null
        handler = null
        runCatching { surface?.release() }
        surface = null
    }

    override fun changeCaptureFormat(w: Int, h: Int, framerate: Int) { /* fixed format */ }

    override fun dispose() {
        runCatching { stopCapture() }
    }

    override fun isScreencast(): Boolean = false
}
