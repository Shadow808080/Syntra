package com.example.syntra

import android.content.Context

/**
 * Per-conversation flags kept on the device.
 *
 * The backend has no archive/pin/block endpoints (`/conversations/{id}/archive`,
 * `/pin`, `/users/{u}/block` all answer "endpoint tidak ditemukan"), so these are
 * local view preferences. They change what this device shows, nothing more.
 */
object ChatFlags {
    private const val PREFS = "syntra_settings"
    private const val KEY_ARCHIVED = "archived_ids"
    private const val KEY_PINNED = "pinned_ids"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun archived(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ARCHIVED, emptySet()) ?: emptySet()

    fun pinned(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_PINNED, emptySet()) ?: emptySet()

    fun setArchived(context: Context, ids: Collection<String>, archived: Boolean) {
        val next = if (archived) archived(context) + ids else archived(context) - ids.toSet()
        prefs(context).edit().putStringSet(KEY_ARCHIVED, next).apply()
    }

    fun setPinned(context: Context, ids: Collection<String>, pinned: Boolean) {
        val next = if (pinned) pinned(context) + ids else pinned(context) - ids.toSet()
        prefs(context).edit().putStringSet(KEY_PINNED, next).apply()
    }
}
