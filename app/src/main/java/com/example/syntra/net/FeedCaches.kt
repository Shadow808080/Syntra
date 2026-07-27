package com.example.syntra.net

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Disk-backed caches for the two screens that used to greet the user with a spinner
 * on every cold start: the Shorts feed and a profile.
 *
 * Both follow the same contract — **render what we had, then refresh behind it**. The
 * cached copy is never treated as truth for long; it exists so the screen has
 * something real to draw in the first frame instead of a loading state. On a slow
 * connection that is the difference between an app that feels instant and one that
 * feels broken.
 */

/** JSON <-> [NetReel], shared by the feed and profile caches. */
private fun reelToJson(r: NetReel) = JSONObject().apply {
    put("id", r.id)
    put("media_url", r.mediaUrl)
    put("caption", r.caption)
    put("author_id", r.authorId)
    put("author_username", r.creatorUsername)
    put("author_name", r.creatorName)
    r.creatorAvatarUrl?.let { put("author_avatar_url", it) }
    put("like_count", r.likeCount)
    put("comment_count", r.commentCount)
    put("view_count", r.viewCount)
    put("is_liked", r.isLiked)
    put("is_saved", r.isSaved)
    put("is_following", r.isFollowing)
}

private fun reelFromJson(o: JSONObject) = NetReel(
    id = o.getString("id"),
    mediaUrl = o.optString("media_url"),
    caption = o.optString("caption"),
    authorId = o.optString("author_id"),
    creatorUsername = o.optString("author_username"),
    creatorName = o.optString("author_name"),
    creatorAvatarUrl = o.optString("author_avatar_url", "").ifBlank { null },
    likeCount = o.optInt("like_count"),
    commentCount = o.optInt("comment_count"),
    viewCount = o.optInt("view_count"),
    isLiked = o.optBoolean("is_liked"),
    isSaved = o.optBoolean("is_saved"),
    isFollowing = o.optBoolean("is_following"),
)

private fun reelsToJson(reels: List<NetReel>): String =
    JSONArray().apply { reels.forEach { put(reelToJson(it)) } }.toString()

private fun reelsFromJson(raw: String): List<NetReel> = runCatching {
    val arr = JSONArray(raw)
    (0 until arr.length()).map { reelFromJson(arr.getJSONObject(it)) }
}.getOrDefault(emptyList())

/**
 * The Shorts feed, kept both in memory (for a tab switch) and on disk (for a cold
 * start). Only the first [MAX_CACHED] are stored: the point is to have something to
 * show immediately, not to keep an offline copy of the whole feed — and every entry
 * we persist is a video URL that the pager might try to prefetch.
 */
object ShortsFeedCache {
    private const val KEY = "shorts:feed"
    private const val MAX_CACHED = 30

    /** In-memory copy, valid for the life of the process. */
    var reels: List<NetReel> = emptyList()

    /** Seed from disk. Safe to call repeatedly; the in-memory copy always wins. */
    fun warm(context: Context) {
        if (reels.isNotEmpty()) return
        val raw = DiskJsonCache.read(context, KEY) ?: return
        reels = reelsFromJson(raw)
    }

    /** Remember [list] for the next cold start. */
    fun persist(context: Context, list: List<NetReel>) {
        reels = list
        DiskJsonCache.write(context, KEY, reelsToJson(list.take(MAX_CACHED)))
    }

    fun clear() { reels = emptyList() }

    fun clear(context: Context) {
        reels = emptyList()
        DiskJsonCache.remove(context, KEY)
    }
}

/** What a profile page needs in order to draw itself before the network answers. */
data class CachedProfile(val user: NetUser, val reels: List<NetReel>)

/**
 * Last-seen profile header + shorts grid, per user.
 *
 * Opening a profile used to fire three or four sequential requests behind a
 * full-screen spinner, so on a bad connection tapping someone's name meant staring at
 * nothing. Now the last known version paints immediately and is replaced in place when
 * the fresh copy lands.
 */
object ProfileCache {
    private fun key(username: String) = "profile:$username"

    fun read(context: Context, username: String): CachedProfile? = runCatching {
        val raw = DiskJsonCache.read(context, key(username)) ?: return null
        val o = JSONObject(raw)
        val user = userFromJson(o.getJSONObject("user"))
        CachedProfile(user, reelsFromJson(o.optJSONArray("reels")?.toString() ?: "[]"))
    }.getOrNull()

    fun write(context: Context, username: String, user: NetUser, reels: List<NetReel>) {
        runCatching {
            val o = JSONObject().apply {
                put("user", userToJson(user))
                put("reels", JSONArray(reelsToJson(reels.take(30))))
            }
            DiskJsonCache.write(context, key(username), o.toString())
        }
    }

    fun remove(context: Context, username: String) = DiskJsonCache.remove(context, key(username))

    // Only the fields the header actually draws are persisted — counts and flags like
    // follow state are re-fetched anyway, and caching a stale "following" would make
    // the button lie for a moment.
    private fun userToJson(u: NetUser) = JSONObject().apply {
        put("id", u.id)
        put("username", u.username)
        put("display_name", u.displayName)
        u.avatarMediaId?.let { put("avatar_media_id", it) }
        u.coverUrl?.let { put("cover_url", it) }
        put("follower_count", u.followerCount)
        put("following_count", u.followingCount)
        put("is_self", u.isSelf)
    }

    // followStatus is deliberately NOT cached: showing a stale "Mengikuti" for the
    // moment before the network answers would be the button telling a lie.
    private fun userFromJson(o: JSONObject) = NetUser(
        id = o.optString("id"),
        username = o.optString("username"),
        displayName = o.optString("display_name"),
        avatarMediaId = o.optString("avatar_media_id", "").ifBlank { null },
        coverUrl = o.optString("cover_url", "").ifBlank { null },
        followerCount = o.optInt("follower_count"),
        followingCount = o.optInt("following_count"),
        isSelf = o.optBoolean("is_self"),
    )
}
