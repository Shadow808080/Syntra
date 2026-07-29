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

    /**
     * One saved sticker. [path] is an absolute file path; [animated] marks a GIF.
     *
     * [source] identifies WHERE it came from — a chat media URL, or the emoji itself
     * for a big-emoji sticker. Without it the app could add the same sticker twice and
     * could never tell a menu whether to offer "add" or "remove", because the saved
     * copy has a fresh UUID with nothing linking it back to the message.
     */
    data class Sticker(
        val id: String,
        val path: String,
        val animated: Boolean,
        val source: String = "",
    )

    private const val PREFS = "syntra_stickers"
    private const val KEY_ORDER = "order" // newline-separated "id.ext" filenames, newest first
    // Filename -> source, as JSON. A separate key so the old KEY_ORDER format keeps
    // loading unchanged for anyone who already has stickers saved.
    private const val KEY_SOURCES = "sources"
    private const val MAX_DIM = 512       // WhatsApp-sized: stills are capped to this

    private var cache: SnapshotStateList<Sticker>? = null

    private fun dir(context: Context): File =
        File(context.filesDir, "stickers").apply { mkdirs() }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The live, newest-first list the picker renders. Loaded once, then observed. */
    fun stickers(context: Context): SnapshotStateList<Sticker> {
        cache?.let { return it }
        val sources = runCatching {
            org.json.JSONObject(prefs(context).getString(KEY_SOURCES, "{}").orEmpty())
        }.getOrDefault(org.json.JSONObject())
        val list = mutableStateListOf<Sticker>()
        prefs(context).getString(KEY_ORDER, "").orEmpty()
            .split("\n").filter { it.isNotBlank() }
            .forEach { name ->
                val f = File(dir(context), name)
                if (f.exists()) {
                    list.add(
                        Sticker(
                            id = name.substringBeforeLast('.'),
                            path = f.absolutePath,
                            animated = name.endsWith(".gif"),
                            source = sources.optString(name, ""),
                        ),
                    )
                }
            }
        cache = list
        return list
    }

    /** The saved copy of [source], if it has already been favourited. */
    fun findBySource(context: Context, source: String): Sticker? {
        if (source.isBlank()) return null
        val key = sourceKey(source)
        return stickers(context).firstOrNull { it.source.isNotBlank() && it.source == key }
    }

    fun isSaved(context: Context, source: String): Boolean = findBySource(context, source) != null

    /**
     * Normalises a source so the same sticker is recognised across sends.
     *
     * Media URLs carry signed query strings that change every time they are handed
     * out, so comparing whole URLs would treat one sticker as a different one on each
     * refresh and let it be saved over and over.
     */
    private fun sourceKey(source: String): String = source.substringBefore('?')

    private fun persist(context: Context, list: List<Sticker>) {
        val sources = org.json.JSONObject()
        list.forEach { s -> if (s.source.isNotBlank()) sources.put(File(s.path).name, s.source) }
        prefs(context).edit()
            .putString(KEY_ORDER, list.joinToString("\n") { File(it.path).name })
            .putString(KEY_SOURCES, sources.toString())
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
        val bytes = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        }
        return addFromBytes(context, bytes)
    }

    /**
     * Adds a sticker straight from bytes — used when saving someone else's sticker to
     * your favourites, where all we have are the downloaded/rendered bytes.
     *
     * GIF-or-not is decided by the file's OWN header (magic bytes), never by a picker's
     * reported mime: trusting the mime meant a real GIF got decoded to a single PNG
     * frame and sent as a still — the "sticker won't move" bug.
     */
    suspend fun addFromBytes(context: Context, bytes: ByteArray?, source: String = ""): Sticker? {
        if (bytes == null || bytes.isEmpty()) return null
        val list = stickers(context)
        // Already favourited: hand back the existing one instead of making a duplicate.
        findBySource(context, source)?.let { return it }
        val sticker = withContext(Dispatchers.IO) {
            val animated = looksLikeGif(bytes)
            val id = UUID.randomUUID().toString()
            val file = File(dir(context), "$id." + if (animated) "gif" else "png")
            val ok = runCatching {
                if (animated) {
                    // Keep the GIF's bytes verbatim so every frame survives.
                    file.writeBytes(bytes)
                } else {
                    val bmp = decodeDownsampled(bytes) ?: return@runCatching false
                    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bmp.recycle()
                }
                true
            }.getOrDefault(false)
            if (!ok || file.length() == 0L) {
                file.delete()
                null
            } else {
                Sticker(id, file.absolutePath, animated, sourceKey(source))
            }
        } ?: return null
        list.add(0, sticker)
        persist(context, list)
        return sticker
    }

    /** Un-favourites whatever was saved from [source]. No-op if it wasn't. */
    fun removeBySource(context: Context, source: String) {
        findBySource(context, source)?.let { remove(context, it.id) }
    }

    /** GIF magic number: the file starts with the ASCII bytes "GIF" (GIF87a/GIF89a). */
    private fun looksLikeGif(b: ByteArray): Boolean =
        b.size >= 3 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte()

    /** Removes a sticker from the collection and deletes its file. */
    fun remove(context: Context, id: String) {
        val list = stickers(context)
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        runCatching { File(list[idx].path).delete() }
        list.removeAt(idx)
        persist(context, list)
    }

    private fun decodeDownsampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_DIM || bounds.outHeight / sample > MAX_DIM) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
