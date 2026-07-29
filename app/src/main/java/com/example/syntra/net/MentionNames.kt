package com.example.syntra.net

import androidx.compose.runtime.mutableStateMapOf

/**
 * Maps `@username` to the person's display name, so a mention reads as a name.
 *
 * The body text stored on the server has to stay `@username` — that is what the
 * backend scans to raise the mention notification, and a display name is neither
 * unique nor stable. So the substitution happens at RENDER time only: the comment
 * says `@reza`, the reader sees `@Reza Ramadhan`, and the link still carries `reza`.
 *
 * Backed by Compose state, so a name that arrives from the network a moment later
 * redraws the comment that needed it instead of waiting for the next scroll.
 *
 * Process-scoped: the same handful of people get mentioned over and over across
 * different reels, and re-fetching them per sheet would be one request per mention.
 */
object MentionNames {
    private val cache = mutableStateMapOf<String, String>()

    /** Cached display name for [username], or null if we have never seen them. */
    fun known(username: String): String? = cache[username.lowercase()]

    /** Records a name we already had — comment authors, tag-picker results. Free. */
    fun remember(username: String, displayName: String) {
        val key = username.lowercase()
        if (key.isBlank() || displayName.isBlank()) return
        cache[key] = displayName
    }

    /**
     * Looks a handle up if it is not already known.
     *
     * A miss is cached as the handle itself, so an `@typo` nobody owns is asked about
     * once rather than on every recomposition of every comment that contains it.
     */
    suspend fun resolve(username: String) {
        val key = username.lowercase()
        if (key.isBlank() || cache.containsKey(key)) return
        if (!ApiConfig.ENABLED) return
        val name = runCatching { SyntraClient.getUser(username) }
            .getOrNull()
            ?.displayName
            ?.takeIf { it.isNotBlank() }
        cache[key] = name ?: username
    }

    /** Every `@handle` in [text], lowercased and de-duplicated. */
    fun handlesIn(text: String): List<String> =
        MENTION.findAll(text).map { it.value.substring(1).trimEnd('.') }
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }
            .distinct()
            .toList()

    private val MENTION = Regex("@[A-Za-z0-9_.]+")
}
