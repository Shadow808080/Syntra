package com.example.syntra.net

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * A device-local collection of the user's own chat stickers — the "install your own
 * sticker" tray, WhatsApp-style.
 *
 * A sticker is copied into the app's private storage (`filesDir/stickers`) so it
 * survives for as long as the app is installed; the ordered list of filenames lives
 * in SharedPreferences. Nothing here touches the network — a sticker is uploaded only
 * when it is actually SENT, through the normal media pipeline (like a GIF).
 *
 * The in-memory [SnapshotStateList] is what the picker observes, so adding or removing
 * a sticker refreshes the grid live.
 */
object StickerStore {

    /** One saved sticker. [path] is an absolute file path; [animated] marks a GIF. */
    data class Sticker(val id: String, val path: String, val animated: Boolean)

    private const val PREFS = "syntra_stickers"
    private const val KEY_ORDER = "order" // newline-separated "id.ext" filenames, newest first
    private const val MAX_DIM = 512       // WhatsApp-sized: stills are capped to this

    private var cache: SnapshotStateList<Sticker>? = null

    private fun dir(context: Context): File =
        File(context.filesDir, "stickers").apply { mkdirs() }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The live, newest-first list the picker renders. Loaded once, then observed. */
    fun stickers(context: Context): SnapshotStateList<Sticker> {
        cache?.let { return it }
        val list = mutableStateListOf<Sticker>()
        prefs(context).getString(KEY_ORDER, "").orEmpty()
            .split("\n").filter { it.isNotBlank() }
            .forEach { name ->
                val f = File(dir(context), name)
                if (f.exists()) list.add(Sticker(name.substringBeforeLast('.'), f.absolutePath, name.endsWith(".gif")))
            }
        cache = list
        return list
    }

    private fun persist(context: Context, list: List<Sticker>) {
        prefs(context).edit()
            .putString(KEY_ORDER, list.joinToString("\n") { File(it.path).name })
            .apply()
    }

    /**
     * Copies [uri] into the collection and returns the new sticker (placed first).
     *
     * A GIF is stored verbatim so it keeps animating; a still image is downsampled to
     * at most [MAX_DIM] and saved as PNG so any transparency survives. Returns null if
     * the image can't be read. The file work runs off the main thread; the observed
     * list is then updated on the caller's dispatcher.
     */
    suspend fun add(context: Context, uri: Uri): Sticker? {
        val list = stickers(context)
        val sticker = withContext(Dispatchers.IO) {
            val cr = context.contentResolver
            val animated = cr.getType(uri) == "image/gif"
            val id = UUID.randomUUID().toString()
            val file = File(dir(context), "$id." + if (animated) "gif" else "png")
            val ok = runCatching {
                if (animated) {
                    cr.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                        ?: return@runCatching false
                } else {
                    val bmp = decodeDownsampled(context, uri) ?: return@runCatching false
                    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bmp.recycle()
                }
                true
            }.getOrDefault(false)
            if (!ok || file.length() == 0L) {
                file.delete()
                null
            } else {
                Sticker(id, file.absolutePath, animated)
            }
        } ?: return null
        list.add(0, sticker)
        persist(context, list)
        return sticker
    }

    /** Removes a sticker from the collection and deletes its file. */
    fun remove(context: Context, id: String) {
        val list = stickers(context)
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        runCatching { File(list[idx].path).delete() }
        list.removeAt(idx)
        persist(context, list)
    }

    private fun decodeDownsampled(context: Context, uri: Uri): Bitmap? {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_DIM || bounds.outHeight / sample > MAX_DIM) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
}
