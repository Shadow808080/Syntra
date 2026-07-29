package com.example.syntra.net

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs long uploads OUTSIDE any screen's lifetime, and remembers their progress.
 *
 * WHY. Both the reel and the music upload ran in `rememberCoroutineScope()`, which is
 * tied to the composable that created it. Swiping to another tab removes that
 * composable, Compose cancels the scope, and the in-flight upload is aborted mid-file —
 * silently, because a CancellationException looks like an ordinary cancellation. The
 * user came back to a screen with no upload card and no error, and the file had never
 * arrived. Anything that outlives a glance at another tab cannot be owned by a screen.
 *
 * This scope belongs to the process, so an upload survives tab switches, the app going
 * to the background, and the screen being disposed and recreated. Progress lives here
 * too as Compose state — that is what lets the card REAPPEAR, still running, when the
 * user returns, instead of vanishing with the composable that started it.
 *
 * Deliberately not a Service: this must not keep running after the process dies, and
 * an upload the user can no longer see is not worth a foreground notification. It
 * survives navigation, not death.
 */
object UploadCenter {

    /** One upload in flight. */
    data class Progress(
        val label: String,
        /** 0f..1f estimate. Uploads give no real progress, so this is time-based. */
        val startedAt: Long = System.currentTimeMillis(),
        val etaMs: Long = 6000L,
        /** Local thumbnail path/URI for the card, when the caller could produce one. */
        val thumb: String? = null,
        val failed: String? = null,
    )

    /** The reel upload currently running (null = none). Compose state. */
    var reel by mutableStateOf<Progress?>(null)
        private set

    /** The music upload currently running (null = none). Compose state. */
    var music by mutableStateOf<Progress?>(null)
        private set

    private val supervisor = SupervisorJob()

    /**
     * A scope resolved at LAUNCH time, not once when this object is created.
     *
     * `Dispatchers.Main.immediate` snapshots whatever delegate is installed when it is
     * evaluated. Holding it in a `val` meant the very first thing to touch this object
     * fixed the dispatcher forever — which under a unit-test JVM (one fork for the
     * whole source set) is whichever test class happened to load first, so a later
     * `Dispatchers.setMain` was silently ignored and every upload assertion failed with
     * "Module with the Main dispatcher had failed to initialize", pointing nowhere near
     * the cause. They all share [supervisor], so cancellation still works as one unit.
     */
    private fun scope() = CoroutineScope(supervisor + Dispatchers.Main.immediate)

    private var reelJob: Job? = null
    private var musicJob: Job? = null

    /**
     * Bumped when a reel finishes. The screen observes it and reloads — the upload
     * cannot call the screen's own reload(), because by then that composable may be
     * long gone. A counter crosses that boundary safely.
     */
    var reelCompleted by mutableStateOf(0)
        private set

    val reelBusy: Boolean get() = reel != null
    val musicBusy: Boolean get() = music != null

    fun updateReel(transform: (Progress) -> Progress) {
        reel = reel?.let(transform)
    }

    fun updateMusic(transform: (Progress) -> Progress) {
        music = music?.let(transform)
    }

    /**
     * Starts a reel upload. [block] does the work; the card stays visible until it
     * returns. Only one at a time — a second call while busy is ignored.
     */
    fun startReel(label: String, etaMs: Long, block: suspend () -> Unit) {
        if (reelBusy) return
        reel = Progress(label = label, etaMs = etaMs)
        reelJob = scope().launch {
            try {
                block()
                reelCompleted++
                reel = null
            } catch (c: CancellationException) {
                // Cancellation is [clearReel] dismissing this card, and it has already
                // nulled `reel`. Writing a failure here would stamp it onto whatever
                // upload started in the meantime.
                throw c
            } catch (t: Throwable) {
                reel = reel?.copy(failed = t.message ?: "Gagal mengunggah")
            }
        }
    }

    /** Same for music. */
    fun startMusic(label: String, etaMs: Long, block: suspend () -> Unit) {
        if (musicBusy) return
        music = Progress(label = label, etaMs = etaMs)
        musicJob = scope().launch {
            try {
                block()
                music = null
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                music = music?.copy(failed = t.message ?: "Gagal mengunggah")
            }
        }
    }

    /** Dismiss a finished-with-error card. */
    fun clearReel() {
        reelJob?.cancel(); reelJob = null; reel = null
    }

    fun clearMusic() {
        musicJob?.cancel(); musicJob = null; music = null
    }
}
