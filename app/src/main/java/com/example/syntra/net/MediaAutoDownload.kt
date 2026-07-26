package com.example.syntra.net

import android.content.Context
import com.example.syntra.SettingsStore

/**
 * Which media kinds may download themselves inside a chat.
 *
 * A kind that is switched off is not fetched when the message lands — the bubble
 * shows a tap-to-download placeholder instead, so the user decides what spends their
 * data. Everything defaults to ON (the behaviour people expect); turning things off
 * is the deliberate act.
 */
object MediaAutoDownload {

    fun photo(context: Context) = SettingsStore.getBool(context, SettingsStore.AUTO_DL_PHOTO, true)
    fun video(context: Context) = SettingsStore.getBool(context, SettingsStore.AUTO_DL_VIDEO, true)
    fun sticker(context: Context) = SettingsStore.getBool(context, SettingsStore.AUTO_DL_STICKER, true)
    fun gif(context: Context) = SettingsStore.getBool(context, SettingsStore.AUTO_DL_GIF, true)
    fun voice(context: Context) = SettingsStore.getBool(context, SettingsStore.AUTO_DL_VOICE, true)

    /** Media kinds a chat bubble can hold, for picking the right switch. */
    enum class Kind { PHOTO, VIDEO, STICKER, GIF, VOICE }

    /** Whether [kind] is allowed to fetch itself right now. */
    fun allowed(context: Context, kind: Kind): Boolean = when (kind) {
        Kind.PHOTO -> photo(context)
        Kind.VIDEO -> video(context)
        Kind.STICKER -> sticker(context)
        Kind.GIF -> gif(context)
        Kind.VOICE -> voice(context)
    }

    /** Classifies a media URL so the bubble knows which switch applies. */
    fun kindOf(url: String): Kind {
        val u = url.substringBefore('?').lowercase()
        return when {
            u.endsWith(".gif") -> Kind.GIF
            u.endsWith(".m4a") || u.endsWith(".mp3") || u.endsWith(".aac") ||
                u.endsWith(".ogg") || u.endsWith(".wav") -> Kind.VOICE
            u.endsWith(".mp4") || u.endsWith(".mov") || u.endsWith(".webm") ||
                u.endsWith(".mkv") || u.endsWith(".3gp") -> Kind.VIDEO
            else -> Kind.PHOTO
        }
    }
}
