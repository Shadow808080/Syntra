package com.example.syntra.net

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.LocalContext

/**
 * Last-known profile photo per person, so an avatar that has been seen once never
 * turns back into a coloured letter.
 *
 * THE BUG THIS EXISTS FOR. Most list endpoints (`/conversations`, room participants,
 * realtime participant pushes) don't carry the peer's avatar URL — some send a bare
 * media id, some send nothing. Every screen rendered `url?.takeIf { startsWith("http") }`
 * and fell straight through to the gradient-initial tile when that failed. So the
 * photos looked fine until *anything* rebuilt the list — a pull-to-refresh, an
 * incoming message, a participant joining — and then they all blanked at once. The
 * chat home already had a private in-memory version of this fix; it was lost on every
 * navigation and every process restart, which is why the blanking kept coming back.
 *
 * Backed by SharedPreferences (a few dozen short strings — genuinely small) and
 * mirrored into Compose state, so a photo resolved a second later repaints the tile
 * that was drawn without one.
 */
object AvatarCache {

    private const val PREF = "syntra_avatar_cache"

    private val memo = mutableStateMapOf<String, String>()
    @Volatile private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun ensure(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                prefs(context).all.forEach { (k, v) -> (v as? String)?.let { memo[k] = it } }
            }
            loaded = true
        }
    }

    /** A URL we can actually load, or null. Bare media ids are not usable as-is. */
    private fun usable(url: String?): String? =
        url?.takeIf { it.isNotBlank() && it.startsWith("http") }

    /** The photo remembered for [key], if any. */
    fun get(context: Context, key: String): String? {
        ensure(context)
        return memo[key]
    }

    /** Remember [url] for [key]. No-op for keys we can't use or values we already have. */
    fun put(context: Context, key: String, url: String?) {
        val good = usable(url) ?: return
        if (key.isBlank()) return
        ensure(context)
        if (memo[key] == good) return
        memo[key] = good
        runCatching { prefs(context).edit().putString(key, good).apply() }
    }

    fun clear(context: Context) {
        memo.clear()
        runCatching { prefs(context).edit().clear().apply() }
    }
}

/**
 * The photo to draw for a person: [incoming] when this payload actually carried one,
 * otherwise the last one we saw under any of [keys].
 *
 * TAKING SEVERAL KEYS IS THE POINT. The same person is identified differently
 * depending on the screen — conversations know a username, room participants know only
 * a user id — so a cache keyed one way was invisible to the other, and rooms kept
 * falling back to letters even though the chat list had that person's photo. Pass
 * every id you hold; the URL is stored under all of them, and any one of them finds it.
 *
 * Returns null only when nobody has ever seen this person's picture — the one case
 * where a letter tile is the honest answer.
 */
@Composable
fun rememberAvatarUrl(vararg keys: String?, incoming: String?): String? {
    val context = LocalContext.current
    val usableKeys = keys.filterNotNull().filter { it.isNotBlank() }
    val fresh = incoming?.takeIf { it.isNotBlank() && it.startsWith("http") }
    // Writing during composition would feed a state change back into the same frame,
    // so the remembering happens in an effect.
    LaunchedEffect(usableKeys.joinToString("|"), fresh) {
        if (fresh != null) usableKeys.forEach { AvatarCache.put(context, it, fresh) }
    }
    if (fresh != null) return fresh
    return usableKeys.firstNotNullOfOrNull { AvatarCache.get(context, it) }
}
