package com.example.syntra.net

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Device-local app lock: a numeric PIN (optionally unlocked with a fingerprint).
 *
 * Everything lives on this phone — the backend has no notion of an app lock, so it
 * never leaves the device and is not synced. The PIN is never stored in the clear:
 * we keep a random salt plus a stretched SHA-256 hash and compare hashes, so reading
 * the prefs file reveals nothing usable.
 */
object AppLockStore {
    private const val PREFS = "syntra_app_lock"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_SALT = "pin_salt"
    private const val KEY_BIO = "biometric_enabled"
    private const val KEY_AUTOLOCK = "autolock_seconds"

    /** Iterations of SHA-256 — cheap on device, but slows a brute-force of the file. */
    private const val STRETCH = 12_000

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The lock is on exactly when a PIN has been set. */
    fun isEnabled(context: Context): Boolean = prefs(context).contains(KEY_HASH)

    fun biometricEnabled(context: Context): Boolean =
        isEnabled(context) && prefs(context).getBoolean(KEY_BIO, false)

    /**
     * How long the app may sit in the background before it re-locks, in seconds.
     * 0 = lock as soon as you leave (subject only to the short picker grace).
     */
    fun autoLockSeconds(context: Context): Int =
        prefs(context).getInt(KEY_AUTOLOCK, 0)

    fun setAutoLockSeconds(context: Context, seconds: Int) {
        prefs(context).edit().putInt(KEY_AUTOLOCK, seconds).apply()
    }

    /** The auto-lock delay in millis (0 for "segera"). */
    fun autoLockMs(context: Context): Long = autoLockSeconds(context) * 1000L

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BIO, enabled).apply()
    }

    /** Set (or change) the PIN. Marks the current session unlocked so we don't lock the user out. */
    fun setPin(context: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(context).edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
            .apply()
        AppLock.unlocked = true
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val p = prefs(context)
        val saltB = p.getString(KEY_SALT, null) ?: return false
        val hashB = p.getString(KEY_HASH, null) ?: return false
        val salt = Base64.decode(saltB, Base64.NO_WRAP)
        val expected = Base64.decode(hashB, Base64.NO_WRAP)
        // Constant-time compare so a wrong PIN can't be timed byte-by-byte.
        return MessageDigest.isEqual(hash(pin, salt), expected)
    }

    /** Turn the lock off entirely and clear all secrets. */
    fun disable(context: Context) {
        prefs(context).edit()
            .remove(KEY_HASH)
            .remove(KEY_SALT)
            .remove(KEY_BIO)
            .apply()
        AppLock.unlocked = true
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        var out = MessageDigest.getInstance("SHA-256").run {
            update(salt)
            update(pin.toByteArray(Charsets.UTF_8))
            digest()
        }
        repeat(STRETCH) {
            out = MessageDigest.getInstance("SHA-256").run {
                update(salt)
                update(out)
                digest()
            }
        }
        return out
    }
}

/**
 * In-memory lock state for the current process. [unlocked] gates the whole app: the
 * root gate shows the lock screen whenever the lock is enabled and this is false.
 *
 * We re-lock when the app has been in the background — but only past a short grace
 * window, so bouncing out to the gallery/camera picker (which briefly stops the
 * activity) doesn't demand the PIN again the instant you come back.
 */
object AppLock {
    var unlocked by mutableStateOf(false)

    private const val GRACE_MS = 1500L
    private var backgroundedAt = 0L

    /**
     * Set while the app itself is showing a SYSTEM dialog — a runtime permission
     * request, a file picker, the biometric prompt.
     *
     * Those stop the activity exactly like the user leaving, but they are not the user
     * leaving, and [GRACE_MS] cannot tell the difference: 1.5s is shorter than anyone
     * takes to read a permission dialog, so granting mic access re-locked the app. That
     * was not merely annoying — it dropped MainTabs out of the composition, which tore
     * down the live call inside it. A flag is used rather than a longer window because
     * the answer must not depend on how fast the user reads.
     */
    @Volatile private var awaitingSystemDialog = false

    /** Call immediately BEFORE launching a permission request / picker / biometric prompt. */
    fun expectSystemDialog() {
        awaitingSystemDialog = true
    }

    fun onBackground() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun onForeground(context: Context) {
        if (!AppLockStore.isEnabled(context)) {
            unlocked = true
            awaitingSystemDialog = false
            return
        }
        // Returning from our own system dialog: keep whatever state we had, however
        // long it took. Consumed here so a later real background stint still locks.
        if (awaitingSystemDialog) {
            awaitingSystemDialog = false
            return
        }
        // Cold start (no recorded background time) stays locked; a quick round-trip
        // to a picker within the grace window keeps the current unlocked state. The
        // user's chosen auto-lock delay extends this window (min the picker grace, so
        // "segera" still survives our own pickers).
        val grace = maxOf(GRACE_MS, AppLockStore.autoLockMs(context))
        val away = SystemClock.elapsedRealtime() - backgroundedAt
        if (backgroundedAt != 0L && away < grace) return
        unlocked = false
    }
}
