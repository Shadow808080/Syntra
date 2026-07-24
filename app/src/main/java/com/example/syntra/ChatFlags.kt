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
    private const val KEY_WATCHED_OWN_STORY = "watched_own_story_ids"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Stories a user watches on their OWN status. The backend never records an
    // author viewing their own story (it must not inflate the view count), so it
    // always returns viewed=false for them. We remember it here so the ring stays
    // dimmed across refreshes — a local-only "seen" mark, no public effect.
    fun watchedOwnStories(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_WATCHED_OWN_STORY, emptySet()) ?: emptySet()

    fun markOwnStoryWatched(context: Context, storyId: String) {
        val next = watchedOwnStories(context) + storyId
        prefs(context).edit().putStringSet(KEY_WATCHED_OWN_STORY, next).apply()
    }

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
