package com.example.syntra

import com.example.syntra.net.UploadCenter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the upload state machine.
 *
 * This exists because of a real bug: uploads were owned by the screen that started
 * them, so swiping to another tab cancelled the job mid-file — the card simply
 * vanished and the reel never arrived, with no error anywhere. The fix was to move
 * ownership to a process-scoped object, and these lock in the behaviour that fix
 * depends on: a job that outlives its caller, a card that stays until the work really
 * finishes, and a failure that is reported instead of swallowed.
 *
 * UploadCenter dispatches on Main, so the test substitutes it — otherwise this would
 * have to be an instrumented test just to observe a coroutine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UploadCenterTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // It is a singleton, so one test's leftovers are the next test's state.
        UploadCenter.clearReel()
        UploadCenter.clearMusic()
    }

    @After
    fun tearDown() {
        UploadCenter.clearReel()
        UploadCenter.clearMusic()
        Dispatchers.resetMain()
    }

    @Test
    fun `card appears immediately and clears when the work finishes`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        UploadCenter.startReel("Menerbitkan reel", 6000L) { gate.await() }

        // Visible before the work is anywhere near done — that card IS the feedback.
        assertTrue(UploadCenter.reelBusy)
        assertEquals("Menerbitkan reel", UploadCenter.reel?.label)

        gate.complete(Unit)
        advanceUntilIdle()

        assertNull(UploadCenter.reel)
        assertFalse(UploadCenter.reelBusy)
    }

    @Test
    fun `completion counter bumps so a screen that was gone can still react`() = runTest(dispatcher) {
        val before = UploadCenter.reelCompleted
        UploadCenter.startReel("x", 1L) { }
        advanceUntilIdle()
        assertEquals(before + 1, UploadCenter.reelCompleted)
    }

    @Test
    fun `a second start while busy is ignored, not queued`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        UploadCenter.startReel("pertama", 1L) { gate.await() }
        UploadCenter.startReel("kedua", 1L) { error("must not run") }

        // The first upload keeps the slot; the second is dropped rather than
        // clobbering the card or racing it.
        assertEquals("pertama", UploadCenter.reel?.label)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `failure is kept on the card instead of vanishing`() = runTest(dispatcher) {
        UploadCenter.startReel("gagal", 1L) { throw IllegalStateException("jaringan mati") }
        advanceUntilIdle()

        // The whole point: a failed upload must still be ON SCREEN, saying why.
        assertNotNull(UploadCenter.reel)
        assertEquals("jaringan mati", UploadCenter.reel?.failed)
        assertTrue(UploadCenter.reelBusy)
    }

    @Test
    fun `a failed upload does not count as completed`() = runTest(dispatcher) {
        val before = UploadCenter.reelCompleted
        UploadCenter.startReel("gagal", 1L) { throw IllegalStateException("boom") }
        advanceUntilIdle()
        assertEquals(before, UploadCenter.reelCompleted)
    }

    @Test
    fun `clearing dismisses a failed card and frees the slot`() = runTest(dispatcher) {
        UploadCenter.startReel("gagal", 1L) { throw IllegalStateException("boom") }
        advanceUntilIdle()
        assertTrue(UploadCenter.reelBusy)

        UploadCenter.clearReel()
        assertNull(UploadCenter.reel)
        assertFalse(UploadCenter.reelBusy)
    }

    @Test
    fun `reel and music uploads do not share a slot`() = runTest(dispatcher) {
        val reelGate = CompletableDeferred<Unit>()
        val musicGate = CompletableDeferred<Unit>()
        UploadCenter.startReel("reel", 1L) { reelGate.await() }
        UploadCenter.startMusic("lagu", 1L) { musicGate.await() }

        assertTrue(UploadCenter.reelBusy)
        assertTrue(UploadCenter.musicBusy)

        reelGate.complete(Unit)
        advanceUntilIdle()
        assertFalse(UploadCenter.reelBusy)
        // Finishing the reel must not take the music card down with it.
        assertTrue(UploadCenter.musicBusy)

        musicGate.complete(Unit)
        advanceUntilIdle()
        assertFalse(UploadCenter.musicBusy)
    }

    @Test
    fun `updateReel edits the live card and is a no-op when idle`() = runTest(dispatcher) {
        UploadCenter.updateReel { it.copy(label = "tidak ada kartu") }
        assertNull(UploadCenter.reel)

        val gate = CompletableDeferred<Unit>()
        UploadCenter.startReel("awal", 1000L) { gate.await() }
        UploadCenter.updateReel { it.copy(etaMs = 9999L) }
        assertEquals(9999L, UploadCenter.reel?.etaMs)
        assertEquals("awal", UploadCenter.reel?.label)

        gate.complete(Unit)
        advanceUntilIdle()
    }
}
