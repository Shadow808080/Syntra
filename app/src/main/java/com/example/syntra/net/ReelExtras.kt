package com.example.syntra.net

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Bridge for picture-in-picture. The Activity is the only thing that can actually
 * enter PiP, so it registers [enter] here and Compose (the Shorts settings sheet)
 * calls [request]. [inPip] is observed by the feed so it can strip its overlays
 * down to just the video while the little window is up.
 */
object PipController {
    /** True while the app is showing in the floating PiP window. */
    var inPip by mutableStateOf(false)

    /** Set by MainActivity; invokes `enterPictureInPictureMode`. */
    var enter: (() -> Unit)? = null

    fun request() {
        enter?.invoke()
    }
}

/**
 * "Layar bersih" (clean screen) for Shorts: strips all the overlays (caption, username,
 * action rail, progress) so you see just the video. Turned on from the long-press sheet
 * and cleared by a single tap or by scrolling to another reel.
 */
object CleanScreen {
    var on by mutableStateOf(false)
}

/**
 * Device-local "not interested" list for reels. There is no backend notion of this,
 * so we simply remember the ids the user dismissed and filter them out of the feed.
 * Cleared on sign-out so the next account starts fresh.
 */
object NotInterestedStore {
    private const val PREFS = "syntra_not_interested"
    private const val KEY = "ids"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mark(context: Context, reelId: String) {
        if (reelId.isBlank()) return
        val set = prefs(context).getStringSet(KEY, emptySet())!!.toMutableSet()
        set.add(reelId)
        prefs(context).edit().putStringSet(KEY, set).apply()
    }

    fun isHidden(context: Context, reelId: String): Boolean =
        prefs(context).getStringSet(KEY, emptySet())?.contains(reelId) == true

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}

/**
 * Saves a reel's video to the phone's public gallery (Movies/Syntra), so it survives
 * outside the app. On Android 10+ this uses scoped storage (no permission needed); on
 * older versions it writes a real file and asks the media scanner to index it (needs
 * WRITE_EXTERNAL_STORAGE, granted only up to API 28 in the manifest).
 *
 * Best-effort: returns false on any failure so the caller can show a toast.
 */
object ReelDownloader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun saveVideo(context: Context, url: String, displayName: String): Boolean =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) return@withContext false
            runCatching {
                val bytes = client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext false
                    resp.body?.bytes() ?: return@withContext false
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveScoped(context, displayName, bytes)
                } else {
                    saveLegacy(context, displayName, bytes)
                }
            }.getOrDefault(false)
        }

    private fun saveScoped(context: Context, name: String, bytes: ByteArray): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Syntra")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return false
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return true
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, name: String, bytes: ByteArray): Boolean {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Syntra")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, name)
        file.writeBytes(bytes)
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("video/mp4"), null)
        return true
    }
}
