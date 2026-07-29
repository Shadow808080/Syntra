package com.example.syntra

import com.example.syntra.net.UploadCenter
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Loads [UploadCenter] BEFORE any test installs a Main dispatcher — deliberately.
 *
 * Gradle runs the whole unit-test source set in one forked JVM, and the class name is
 * chosen so this sorts first. It exists purely as a tripwire for the trap that used to
 * live in `UploadCenter`: the scope captured `Dispatchers.Main.immediate` once, in the
 * object initializer, so whichever test class happened to touch the object first fixed
 * the dispatcher for the entire run. Every later `Dispatchers.setMain` was ignored and
 * `UploadCenterTest` failed with "Module with the Main dispatcher had failed to
 * initialize" — an error pointing nowhere near the cause.
 *
 * If someone reverts the scope to a `val`, this class going first makes the whole
 * upload suite fail, which is exactly the signal that was missing before.
 */
class AaaUploadCenterTouchedFirstTest {

    @Test
    fun `reading upload state without a Main dispatcher is harmless`() {
        assertFalse(UploadCenter.reelBusy)
        assertFalse(UploadCenter.musicBusy)
    }
}
