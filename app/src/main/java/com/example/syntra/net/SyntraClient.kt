package com.example.syntra.net

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class ApiException(
    val code: String,
    message: String,
    /** Seconds the server asked us to wait (`Retry-After`) on a 429, if any. */
    val retryAfterSeconds: Long? = null,
) : Exception(message)

/** Something interested in realtime WebSocket events. Register via [SyntraClient.addListener]. */
interface SocketListener {
    fun onReady(userId: String) {}
    fun onMessageNew(message: NetMessage) {}
    fun onPresence(presence: NetPresence) {}
    fun onTyping(conversationId: String, userId: String, typing: Boolean) {}
    fun onReadReceipt(conversationId: String, userId: String, messageId: String) {}
    /**
     * The peer's device received the message (✓✓ grey), independent of whether
     * they read it. Fired even when the peer is only on the chat list, and works
     * regardless of presence-privacy — unlike guessing from online status.
     */
    fun onDeliveredReceipt(conversationId: String, userId: String, messageId: String) {}
    /** A message was deleted for everyone — show it as a "deleted" tombstone. */
    fun onMessageDeleted(conversationId: String, messageId: String) {}
    /** A reaction was added/changed/removed. Empty [emoji] means removed. */
    fun onMessageReaction(conversationId: String, messageId: String, userId: String, emoji: String) {}
    /** A user changed their name/photo — sync it (own devices or a contact). */
    fun onUserUpdated(userId: String, displayName: String, avatarUrl: String?) {}
    /** Someone liked/unliked a reel — bump the counter live. */
    fun onReelLike(reelId: String, userId: String, liked: Boolean) {}
    /** A new comment landed on a reel — bump the count live. */
    fun onReelComment(reelId: String, userId: String, body: String) {}
    /** A new (public) reel was posted anywhere — refresh the Shorts feed live. */
    fun onReelNew(reelId: String, authorId: String) {}
    /** Someone you follow posted a story — refresh the story rail live. */
    fun onStoryNew(storyId: String, authorId: String) {}
    /** A reel was deleted — drop it from the feed live. */
    fun onReelDeleted(reelId: String) {}
    fun onAck(ref: String?, data: Any?) {}
    fun onReconnect() {}
    /** Ephemeral room chat message. */
    fun onRoomMessage(message: NetRoomMessage) {}
    /** Authoritative participant list — replace whatever is held locally. */
    fun onRoomParticipants(roomId: String, participants: List<NetRoomParticipant>) {}
    /** Someone asked to speak. Only delivered to hosts/moderators. */
    fun onRoomSpeakRequest(roomId: String, userId: String, name: String) {}
    /** A role changed; when [needsRejoin] the SFU token must be re-minted. */
    fun onRoomRoleChanged(roomId: String, userId: String, role: String, needsRejoin: Boolean) {}
    /** Host left: tear the room down. */
    fun onRoomEnded(roomId: String) {}
    /** A new public room went live anywhere — refresh the Rooms list live. */
    fun onRoomCreated(roomId: String) {}

    /** A group's title/avatar/members changed — re-read that conversation. */
    fun onConversationUpdated(conversationId: String) {}
    /** My request to join an invite-only room was approved/rejected. */
    fun onRoomJoinDecided(roomId: String, approved: Boolean) {}
    /** A new in-app notification arrived. */
    /**
     * A new in-app notification. [kind] is the type (comment/like/follow…);
     * [actorName]/[actorUsername]/[actorAvatarUrl] identify who did it, so a rich
     * system notification ("budi membalas komentar kamu" + their photo) can be shown.
     */
    fun onNotification(
        kind: String,
        actorName: String = "",
        actorUsername: String = "",
        actorAvatarUrl: String? = null,
    ) {}

    /**
     * A call started in one of my conversations — show the incoming/ongoing UI.
     * The payload already carries everything needed to ring ([callId], [kind],
     * [initiatorId]), so the UI doesn't have to round-trip GET .../call first —
     * that extra fetch used to drop the call entirely when it raced or failed.
     */
    fun onCallIncoming(conversationId: String, callId: String, kind: String, initiatorId: String) {}
    /** The call I'm ringing was picked up. [conversationId] scopes it to one call. */
    fun onCallAnswered(callId: String, conversationId: String) {}
    /**
     * The call ended; [reason] is "declined" / "left" / "disconnected".
     * [callId]/[conversationId] identify WHICH call, so a stray ended event from
     * another chat can't tear down the call currently on screen.
     */
    fun onCallEnded(callId: String, conversationId: String, reason: String) {}
}

/**
 * Single entry point to the Syntra backend: Supabase login, REST endpoints, and
 * the WebSocket. Everything here assumes the contract in `api.md`.
 */
object SyntraClient {

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val main = Handler(Looper.getMainLooper())

    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        // Kept short so app start doesn't sit on a blank splash when the backend
        // is unreachable; a reachable LAN/Tailscale host connects in well under 1s.
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile private var jwt: String? = null
    @Volatile private var refreshToken: String? = null
    @Volatile var myUserId: String? = null
        private set

    /** True once we hold credentials the backend will accept. */
    val hasSession: Boolean
        get() = ApiConfig.useDebugUser || jwt != null

    /** Latest refresh token, so the caller can persist it across restarts. */
    val currentRefreshToken: String? get() = refreshToken

    // Best-effort background scope for calls that must not block the UI and whose
    // result we don't need (e.g. leaving a call as the screen closes).
    private val bg = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Runs [block] on a background scope, swallowing failures. */
    fun fireAndForget(block: suspend () -> Unit) {
        bg.launch { runCatching { block() } }
    }

    // -----------------------------------------------------------------------
    // Auth
    // -----------------------------------------------------------------------

    /** Dev-bypass only; there are no baked-in credentials to log in with. */
    suspend fun login() {
        if (ApiConfig.useDebugUser) {
            myUserId = ApiConfig.DEBUG_USER_ID
            return
        }
        throw ApiException("unauthorized", "Belum masuk.")
    }

    /** `POST /api/v1/auth/login` — the backend brokers the identity provider. */
    suspend fun loginWith(email: String, password: String) = withContext(Dispatchers.IO) {
        authRequest(
            path = "/api/v1/auth/login",
            payload = JSONObject().put("email", email).put("password", password),
            failureMessage = "Email atau kata sandi salah",
        )
    }

    /**
     * `POST /api/v1/auth/register`. The endpoint also accepts `display_name`
     * (api.md §1), so we send it here — this survives the email-confirmation
     * case where no session exists yet to PATCH the profile afterwards.
     */
    suspend fun register(
        email: String,
        password: String,
        username: String,
        displayName: String? = null,
    ) = withContext(Dispatchers.IO) {
        authRequest(
            path = "/api/v1/auth/register",
            payload = JSONObject()
                .put("email", email)
                .put("password", password)
                .put("username", username)
                .apply { if (!displayName.isNullOrBlank()) put("display_name", displayName) },
            failureMessage = "Pendaftaran gagal",
            // When the Supabase project requires email confirmation, register
            // succeeds but returns no session yet. That's not an error — the
            // caller checks [hasSession] and reacts (auto-login or "check email").
            allowNoToken = true,
        )
    }

    /**
     * `POST /api/v1/auth/refresh`. Access tokens are short-lived, so without this
     * every restart would land back on the login screen.
     */
    suspend fun restoreSession(token: String) = withContext(Dispatchers.IO) {
        authRequest(
            path = "/api/v1/auth/refresh",
            payload = JSONObject().put("refresh_token", token),
            failureMessage = "Sesi berakhir, silakan masuk lagi",
        )
    }

    /**
     * Is the backend reachable right now? Hits the unauthenticated /readyz probe.
     * Used by the launch gate to tell "server down / maintenance" apart from "not
     * signed in", so an outage shows a maintenance notice instead of the login
     * screen. Returns true when the backend is intentionally disabled (dev builds).
     */
    suspend fun serverReachable(): Boolean = withContext(Dispatchers.IO) {
        if (!ApiConfig.ENABLED) return@withContext true
        // A tight overall budget so an outage flips to the maintenance screen fast,
        // instead of the user staring at the splash for the full connect timeout.
        val probe = http.newBuilder()
            .callTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        runCatching {
            val req = Request.Builder().url(ApiConfig.BASE_URL + "/readyz").get().build()
            probe.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /** Shared shape for the three auth endpoints; stores the resulting session. */
    private fun authRequest(
        path: String,
        payload: JSONObject,
        failureMessage: String,
        allowNoToken: Boolean = false,
    ) {
        val req = Request.Builder()
            .url(rest(path))
            .post(payload.toString().toRequestBody(JSON))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val root = runCatching { JSONObject(text) }.getOrNull()
                ?: throw ApiException("internal", "Balasan server tidak dikenali")
            if (root.has("error")) {
                val err = root.getJSONObject("error")
                throw ApiException(
                    err.optString("code", "unauthorized"),
                    err.optString("message", failureMessage),
                )
            }
            if (!resp.isSuccessful) throw ApiException("unauthorized", failureMessage)

            val data = root.optJSONObject("data") ?: root
            val token = data.optString("access_token", "")
            myUserId = data.optJSONObject("user")?.optString("id")
                ?: data.optString("user_id", null)
            if (token.isBlank()) {
                // Register with email confirmation on: account created, no session
                // yet. Not an error — leave jwt null and let the caller decide.
                if (allowNoToken) return
                throw ApiException("internal", "Server tidak mengirim access_token")
            }
            jwt = token
            refreshToken = data.optString("refresh_token", null)
        }
    }

    /** Drops the in-memory session and closes the socket. */
    fun logout() {
        jwt = null
        refreshToken = null
        myUserId = null
        disconnect()
    }

    private fun Request.Builder.auth(): Request.Builder = apply {
        if (ApiConfig.useDebugUser) header("X-Debug-User", ApiConfig.DEBUG_USER_ID)
        else jwt?.let { header("Authorization", "Bearer $it") }
    }

    // -----------------------------------------------------------------------
    // REST plumbing
    // -----------------------------------------------------------------------

    private fun rest(path: String) = ApiConfig.BASE_URL + path

    private fun Response.dataElement(): Any {
        val text = body?.string().orEmpty()
        if (text.isBlank()) return JSONObject()
        val root = JSONObject(text)
        if (root.has("error")) {
            val err = root.getJSONObject("error")
            throw ApiException(err.optString("code", "internal"), err.optString("message", "request failed"))
        }
        return root.get("data")
    }

    /**
     * Runs a REST request and honors the server's per-user rate limit: on `429`
     * with a `Retry-After` header, it waits exactly that long (capped) and retries
     * once; a still-limited response surfaces as `rate_limited` carrying the delay.
     */
    private suspend fun execute(req: Request): Any = withContext(Dispatchers.IO) {
        var waited = false
        while (true) {
            val resp = http.newCall(req).execute()
            if (resp.code == 429) {
                val retrySec = resp.header("Retry-After")?.trim()?.toLongOrNull()?.coerceIn(1, 60)
                resp.close()
                if (!waited && retrySec != null) {
                    waited = true
                    delay(retrySec * 1000)
                    continue
                }
                throw ApiException("rate_limited", "Terlalu sering. Coba lagi sebentar.", retrySec)
            }
            return@withContext resp.use { it.dataElement() }
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    private suspend fun getData(path: String): Any =
        execute(Request.Builder().url(rest(path)).auth().get().build())

    private suspend fun postData(path: String, json: JSONObject): Any =
        execute(Request.Builder().url(rest(path)).auth().post(json.toString().toRequestBody(JSON)).build())

    private suspend fun patchData(path: String, json: JSONObject): Any =
        execute(Request.Builder().url(rest(path)).auth().patch(json.toString().toRequestBody(JSON)).build())

    private suspend fun putData(path: String, json: JSONObject): Any =
        execute(Request.Builder().url(rest(path)).auth().put(json.toString().toRequestBody(JSON)).build())

    private suspend fun delete(path: String) {
        execute(Request.Builder().url(rest(path)).auth().delete().build())
    }

    // -----------------------------------------------------------------------
    // REST endpoints (api.md §5–8)
    // -----------------------------------------------------------------------

    suspend fun getConversations(): List<NetConversation> =
        (getData("/api/v1/conversations") as JSONArray).mapObjects { it.toConversation() }

    suspend fun getMessages(conversationId: String, before: String? = null): List<NetMessage> {
        val q = if (before != null) "?before=$before" else ""
        return (getData("/api/v1/conversations/$conversationId/messages$q") as JSONArray)
            .mapObjects { it.toMessage() }
    }

    /**
     * Sends a message. Attach media by passing confirmed media ids (max 10) —
     * the server resolves them into `attachments` URLs and flips `type` to
     * "media" on its own, so a photo must never be pasted into [body] as a URL.
     */
    suspend fun sendMessageRest(
        conversationId: String,
        body: String,
        mediaIds: List<String> = emptyList(),
        replyToId: String? = null,
    ): NetMessage {
        val payload = JSONObject()
            .put("type", if (mediaIds.isEmpty()) "text" else "media")
            .put("body", body)
            .put("reply_to_id", replyToId ?: JSONObject.NULL)
        if (mediaIds.isNotEmpty()) payload.put("media_ids", JSONArray(mediaIds))
        return (postData("/api/v1/conversations/$conversationId/messages", payload) as JSONObject).toMessage()
    }

    /**
     * "Delete for everyone" — sender only, marks the message is_deleted and (per the
     * backend note) broadcasts so the peer updates live. Path is `/messages/{id}`.
     */
    suspend fun deleteMessage(messageId: String) = delete("/api/v1/messages/$messageId")

    /** `PUT /messages/{id}/reaction`. Blank [emoji] removes my reaction. Broadcasts. */
    suspend fun reactToMessage(messageId: String, emoji: String) {
        putData("/api/v1/messages/$messageId/reaction", JSONObject().put("emoji", emoji))
    }

    /**
     * Reactions for a batch of messages in one call (`GET .../reactions`), returned
     * as `messageId -> (userId -> emoji)` so the UI can aggregate counts itself.
     */
    suspend fun getReactions(
        conversationId: String,
        messageIds: List<String>,
    ): Map<String, Map<String, String>> {
        if (messageIds.isEmpty()) return emptyMap()
        val ids = messageIds.joinToString(",")
        val arr = getData("/api/v1/conversations/$conversationId/reactions?message_ids=$ids") as JSONArray
        val out = HashMap<String, HashMap<String, String>>()
        (0 until arr.length()).forEach { i ->
            val o = arr.getJSONObject(i)
            val mid = o.optString("message_id")
            val uid = o.optString("user_id")
            val emoji = o.optString("emoji")
            if (mid.isNotBlank() && uid.isNotBlank() && emoji.isNotBlank()) {
                out.getOrPut(mid) { HashMap() }[uid] = emoji
            }
        }
        return out
    }

    suspend fun createDirect(userId: String): String {
        val payload = JSONObject().put("type", "direct").put("user_id", userId)
        return (postData("/api/v1/conversations", payload) as JSONObject).getString("id")
    }

    suspend fun createGroup(title: String, memberIds: List<String>): String {
        val payload = JSONObject().put("type", "group").put("title", title)
            .put("member_ids", JSONArray(memberIds))
        return (postData("/api/v1/conversations", payload) as JSONObject).getString("id")
    }

    suspend fun getStories(): List<NetStoryGroup> =
        (getData("/api/v1/stories") as JSONArray).mapObjects { it.toStoryGroup() }

    suspend fun viewStory(storyId: String) {
        postData("/api/v1/stories/$storyId/view", JSONObject())
    }

    suspend fun createStory(mediaId: String, visibility: String = "followers") {
        postData("/api/v1/stories", JSONObject().put("media_id", mediaId).put("visibility", visibility))
    }

    /**
     * Removes one of my own stories before the 24h expiry does it automatically.
     * Verified against the server: returns 204 and the story leaves `GET /stories`.
     */
    suspend fun deleteStory(storyId: String) = delete("/api/v1/stories/$storyId")

    /**
     * Deletes a media asset from storage.
     *
     * Not in the route table yet (`DELETE /media/{id}` → 404). Used to clean up the
     * previous profile photo when the avatar changes; wired now so it starts working
     * the instant the backend adds it.
     */
    suspend fun deleteMedia(mediaId: String) = delete("/api/v1/media/$mediaId")

    /** My own stories with their view counts (`GET /stories/me`). */
    suspend fun getMyStories(): List<NetMyStory> =
        (getData("/api/v1/stories/me") as JSONArray).mapObjects {
            NetMyStory(
                id = it.getString("id"),
                mediaUrl = it.optString("media_url", ""),
                mediaKind = it.optString("media_kind", "image"),
                viewCount = it.optInt("view_count", 0),
                isExpired = it.optBoolean("is_expired", false),
                createdAt = it.optString("created_at", ""),
                expiresAt = it.optString("expires_at", ""),
            )
        }

    /**
     * Who watched a given story. The route is registered but currently answers
     * "story tidak ditemukan" even for one's own live story, so callers should be
     * ready to fall back to the plain count from [getMyStories].
     */
    suspend fun getStoryViewers(storyId: String): List<NetStoryViewer> =
        (getData("/api/v1/stories/$storyId/viewers") as JSONArray).mapObjects {
            NetStoryViewer(
                userId = it.optString("user_id", ""),
                username = it.optString("username", ""),
                displayName = it.optString("display_name", ""),
                viewedAt = it.optString("viewed_at", ""),
            )
        }

    /** Search/discover users by username or display name. Blank query = suggestions. */
    suspend fun searchUsers(query: String): List<NetUser> {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        return (getData("/api/v1/users/search?q=$q") as JSONArray).mapObjects { it.toUser() }
    }

    suspend fun getUser(username: String): NetUser =
        (getData("/api/v1/users/$username") as JSONObject).toUser()

    // Assumed fixed per catatan-untuk-backend.md §3 (endpoint path may differ).
    suspend fun follow(username: String) {
        postData("/api/v1/users/$username/follow", JSONObject())
    }

    suspend fun unfollow(username: String) = delete("/api/v1/users/$username/follow")

    /**
     * Updates my own profile. `PATCH /users/me` accepts `display_name` and/or
     * `avatar_media_id` and returns the fresh profile, including the public
     * `avatar_url`. Verified against the server.
     */
    suspend fun updateProfile(
        displayName: String? = null,
        avatarMediaId: String? = null,
        username: String? = null,
    ): NetUser = withContext(Dispatchers.IO) {
        val payload = JSONObject()
        if (displayName != null) payload.put("display_name", displayName)
        if (avatarMediaId != null) payload.put("avatar_media_id", avatarMediaId)
        if (username != null) payload.put("username", username)
        val data = patchData("/api/v1/users/me", payload) as JSONObject
        NetUser(
            id = data.optString("id", ""),
            username = data.optString("username", ""),
            displayName = data.optString("display_name", ""),
            // The response carries a ready URL under avatar_url.
            avatarMediaId = data.optString("avatar_url", "").ifBlank { data.optString("avatar_media_id", "") },
        )
    }

    /**
     * My own profile from `GET /users/me`. The server resolves the photo to a ready
     * `avatar_url` (stored here in [NetUser.avatarMediaId]); syncing this on startup
     * is how a photo changed on another device shows up on this one.
     */
    suspend fun getMyProfile(): NetUser = withContext(Dispatchers.IO) {
        val data = getData("/api/v1/users/me") as JSONObject
        NetUser(
            id = data.optString("id", ""),
            username = data.optString("username", ""),
            displayName = data.optString("display_name", ""),
            avatarMediaId = data.optString("avatar_url", "").ifBlank { data.optString("avatar_media_id", "") },
            followerCount = data.optInt("follower_count", 0),
            followingCount = data.optInt("following_count", 0),
            isSelf = true,
        )
    }

    // -----------------------------------------------------------------------
    // Reels / Shorts (Fase 2)
    // -----------------------------------------------------------------------

    /**
     * Author data is flat (`author_id`/`author_username`/`author_name`) and the
     * viewer's own state is `liked`/`saved`. The nested-`creator` spellings are kept
     * as fallbacks so an older server shape still parses.
     */
    private fun JSONObject.toReel(): NetReel {
        val creator = optJSONObject("creator")
        return NetReel(
            id = getString("id"),
            mediaUrl = optString("media_url", ""),
            caption = optString("caption", ""),
            authorId = optString("author_id", "").ifBlank { creator?.optString("id").orEmpty() },
            creatorUsername = optString("author_username", "")
                .ifBlank { creator?.optString("username").orEmpty() },
            creatorName = optString("author_name", "")
                .ifBlank { creator?.optString("display_name").orEmpty() },
            creatorAvatarUrl = optString("author_avatar_url", "")
                .ifBlank { creator?.optString("avatar_url").orEmpty() }
                .ifBlank { null },
            likeCount = optInt("like_count", 0),
            commentCount = optInt("comment_count", 0),
            viewCount = optInt("view_count", 0),
            isLiked = optBoolean("liked", optBoolean("is_liked", false)),
            isSaved = optBoolean("saved", optBoolean("is_saved", false)),
            isFollowing = optBoolean("is_following", false),
        )
    }

    /** Deletes my own reel (soft delete). Owner only — others get 403. */
    suspend fun deleteReel(reelId: String) = delete("/api/v1/reels/$reelId")

    suspend fun getReels(): List<NetReel> =
        (getData("/api/v1/reels") as JSONArray).mapObjects { it.toReel() }

    /** My own uploaded shorts — for the profile grid. */
    suspend fun getMyReels(): List<NetReel> =
        (getData("/api/v1/reels/me") as JSONArray).mapObjects { it.toReel() }

    /** Shorts I've saved — for the profile "saved" grid. */
    suspend fun getSavedReels(): List<NetReel> =
        (getData("/api/v1/reels/saved") as JSONArray).mapObjects { it.toReel() }

    /** Another user's public shorts — for their profile grid. */
    suspend fun getUserReels(username: String): List<NetReel> =
        (getData("/api/v1/users/$username/reels") as JSONArray).mapObjects { it.toReel() }

    suspend fun postReel(
        mediaId: String,
        caption: String,
        visibility: String = "public",
        commentsEnabled: Boolean = true,
    ): String {
        val payload = JSONObject()
            .put("media_id", mediaId)
            .put("caption", caption)
            .put("visibility", visibility)
            .put("comments_enabled", commentsEnabled)
        val data = postData("/api/v1/reels", payload) as JSONObject
        return data.optString("id", "")
    }

    /** Both like and save are toggles: PUT to add, DELETE to remove. */
    suspend fun likeReel(reelId: String, like: Boolean) {
        if (like) putData("/api/v1/reels/$reelId/like", JSONObject())
        else delete("/api/v1/reels/$reelId/like")
    }

    suspend fun saveReel(reelId: String, save: Boolean) {
        if (save) putData("/api/v1/reels/$reelId/save", JSONObject())
        else delete("/api/v1/reels/$reelId/save")
    }

    /** Safe to call each time a reel is shown; the server dedupes per viewer. */
    suspend fun viewReel(reelId: String) {
        runCatching { postData("/api/v1/reels/$reelId/view", JSONObject()) }
    }

    suspend fun getReelComments(reelId: String): List<NetReelComment> =
        (getData("/api/v1/reels/$reelId/comments") as JSONArray).mapObjects {
            val creator = it.optJSONObject("creator")
            NetReelComment(
                id = it.getString("id"),
                authorId = it.optString("author_id", "")
                    .ifBlank { creator?.optString("id").orEmpty() },
                username = it.optString("author_username", "")
                    .ifBlank { creator?.optString("username").orEmpty() },
                displayName = it.optString("author_name", "")
                    .ifBlank { creator?.optString("display_name").orEmpty() },
                avatarUrl = it.optString("author_avatar_url", "")
                    .ifBlank { creator?.optString("avatar_url").orEmpty() }
                    .ifBlank { null },
                body = it.optString("body", ""),
                createdAt = it.optString("created_at", ""),
                parentId = it.optString("parent_comment_id", "").ifBlank { null },
            )
        }

    suspend fun postReelComment(reelId: String, body: String, parentId: String? = null) {
        val payload = JSONObject().put("body", body)
        if (!parentId.isNullOrBlank()) payload.put("parent_id", parentId)
        postData("/api/v1/reels/$reelId/comments", payload)
    }

    /** Deletes a comment (author only; the backend enforces ownership). */
    suspend fun deleteReelComment(reelId: String, commentId: String) {
        delete("/api/v1/reels/$reelId/comments/$commentId")
    }

    // -----------------------------------------------------------------------
    // Calls (audio & video) — docs/api.md §Calls
    //
    // The backend only carries call *state* and mints the LiveKit token; the
    // audio/video streams flow through the SFU (sfu_url/sfu_token), handled by
    // CallEngine. Incoming/answered/ended arrive as WebSocket events.
    // -----------------------------------------------------------------------

    private fun JSONObject.toCall(fallbackId: String = "") = NetCall(
        callId = optString("call_id", fallbackId),
        sfuRoomId = optString("sfu_room_id", ""),
        sfuToken = optString("sfu_token", ""),
        sfuUrl = optString("sfu_url", ""),
        isNew = optBoolean("is_new", false),
    )

    /** Starts a call (or joins the conversation's ongoing one). [kind] = "audio"|"video". */
    suspend fun startCall(conversationId: String, kind: String): NetCall {
        val payload = JSONObject().put("conversation_id", conversationId).put("kind", kind)
        return (postData("/api/v1/calls", payload) as JSONObject).toCall()
    }

    /** Answers an incoming call and returns the credentials to connect. */
    suspend fun answerCall(callId: String, conversationId: String): NetCall =
        (postData("/api/v1/calls/$callId/answer?conversation_id=$conversationId", JSONObject()) as JSONObject)
            .toCall(callId)

    /** Rejects an incoming call (direct chats only). */
    suspend fun declineCall(callId: String, conversationId: String) {
        runCatching { postData("/api/v1/calls/$callId/decline?conversation_id=$conversationId", JSONObject()) }
    }

    /** Leaves an active call. */
    suspend fun leaveCall(callId: String, conversationId: String) {
        runCatching { postData("/api/v1/calls/$callId/leave?conversation_id=$conversationId", JSONObject()) }
    }

    /** Active call in a conversation, or null when none is ongoing. */
    suspend fun getActiveCall(conversationId: String): NetActiveCall? {
        val data = runCatching { getData("/api/v1/conversations/$conversationId/call") }.getOrNull()
        val obj = data as? JSONObject ?: return null
        return NetActiveCall(
            id = obj.optString("id", ""),
            kind = obj.optString("kind", "audio"),
            status = obj.optString("status", ""),
            initiatorId = obj.optString("initiator_id", ""),
            startedAt = obj.optString("started_at", ""),
        )
    }

    /** People I follow — the pool to pick group members from. */
    suspend fun getFollowing(): List<NetUser> =
        (getData("/api/v1/users/me/following") as JSONArray).mapObjects { it.toUser() }

    /** Reports a user. `reason` is free text; verified `POST /reports` → 201. */
    suspend fun reportUser(targetId: String, reason: String) {
        postData(
            "/api/v1/reports",
            JSONObject().put("reason", reason).put("target_type", "user").put("target_id", targetId),
        )
    }

    /** Mute a conversation for [minutes], or `null` to unmute. `PUT .../mute`. */
    suspend fun setConversationMute(conversationId: String, minutes: Int?) {
        val body = JSONObject().put("duration_minutes", minutes ?: JSONObject.NULL)
        putData("/api/v1/conversations/$conversationId/mute", body)
    }

    /** Blocks a user (by username — the UUID form 404s). Verified → 204. */
    suspend fun blockUser(username: String) {
        postData("/api/v1/users/$username/block", JSONObject())
    }

    suspend fun unblockUser(username: String) = delete("/api/v1/users/$username/block")

    /** Users I've blocked (each has `user_id`, `username`, `display_name`). */
    suspend fun getBlocked(): List<NetUser> =
        (getData("/api/v1/users/me/blocked") as JSONArray).mapObjects {
            NetUser(
                id = it.optString("user_id", ""),
                username = it.optString("username", ""),
                displayName = it.optString("display_name", ""),
            )
        }

    /** Revokes the refresh token server-side, then clears the local session. */
    suspend fun logoutRemote() {
        runCatching { postData("/api/v1/auth/logout", JSONObject()) }
        logout()
    }

    // -----------------------------------------------------------------------
    // Voice rooms — docs/voice-rooms.md
    //
    // The backend is the authority for room *state*; the audio itself flows
    // through LiveKit using the sfu_url/sfu_token handed back by join().
    // Note: the server does NOT broadcast participant events, so the participant
    // list has to be re-fetched (see RoomDetailScreen's polling).
    // -----------------------------------------------------------------------

    /** Active rooms plus whether the media server is configured at all. */
    suspend fun getRooms(): NetRoomList = withContext(Dispatchers.IO) {
        val text = http.newCall(Request.Builder().url(rest("/api/v1/rooms")).auth().get().build())
            .execute().use { it.body?.string().orEmpty() }
        val root = JSONObject(text)
        if (root.has("error")) {
            val err = root.getJSONObject("error")
            throw ApiException(err.optString("code", "internal"), err.optString("message", "gagal memuat room"))
        }
        NetRoomList(
            rooms = (root.optJSONArray("data") ?: JSONArray()).mapObjects { it.toRoom() },
            sfuReady = root.optJSONObject("meta")?.optBoolean("sfu_ready", false) ?: false,
        )
    }

    /**
     * Creates a room. The response already carries a `join` block with the SFU
     * credentials, so the host does not need a second call to start talking.
     */
    suspend fun createRoom(
        title: String,
        topic: String,
        visibility: String = "public",
    ): Pair<String, NetRoomJoin?> {
        val payload = JSONObject().put("title", title).put("topic", topic).put("visibility", visibility)
        val data = postData("/api/v1/rooms", payload) as JSONObject
        val id = data.optString("id", data.optString("room_id", ""))
        val join = data.optJSONObject("join")?.let { j ->
            NetRoomJoin(
                roomId = j.optString("room_id", id),
                role = j.optString("role", "host"),
                canPublish = j.optBoolean("can_publish", true),
                sfuRoomId = j.optString("sfu_room_id", ""),
                sfuToken = j.optString("sfu_token", ""),
                sfuUrl = j.optString("sfu_url", ""),
            )
        }
        return id to join
    }

    /**
     * Joins the room and returns the LiveKit credentials. Call this again after a
     * role change — the old token was minted with the old `can_publish` and will
     * never allow the microphone on.
     */
    suspend fun joinRoom(roomId: String): NetRoomJoin {
        val d = postData("/api/v1/rooms/$roomId/join", JSONObject()) as JSONObject
        return NetRoomJoin(
            roomId = d.optString("room_id", roomId),
            role = d.optString("role", "listener"),
            canPublish = d.optBoolean("can_publish", false),
            sfuRoomId = d.optString("sfu_room_id", ""),
            sfuToken = d.optString("sfu_token", ""),
            sfuUrl = d.optString("sfu_url", ""),
        )
    }

    suspend fun leaveRoom(roomId: String) {
        postData("/api/v1/rooms/$roomId/leave", JSONObject())
    }

    suspend fun getRoomParticipants(roomId: String): List<NetRoomParticipant> =
        (getData("/api/v1/rooms/$roomId/participants") as JSONArray).mapObjects { it.toParticipant() }

    /** Host/moderator only: promote or demote someone. */
    suspend fun setRoomRole(roomId: String, userId: String, role: String) {
        patchData(
            "/api/v1/rooms/$roomId/participants",
            JSONObject().put("user_id", userId).put("role", role),
        )
    }

    suspend fun setRoomMuted(roomId: String, muted: Boolean) {
        patchData("/api/v1/rooms/$roomId/mute", JSONObject().put("muted", muted))
    }

    /** Ask the host for permission to speak. No body. */
    suspend fun raiseHand(roomId: String) {
        postData("/api/v1/rooms/$roomId/raise-hand", JSONObject())
    }

    fun roomJoinTopic(roomId: String) = subscribe(listOf("room:$roomId"))

    fun roomLeaveTopic(roomId: String) {
        subscribed.remove("room:$roomId")
        sendFrame("unsubscribe", JSONObject().put("topics", JSONArray(listOf("room:$roomId"))))
    }

    /** Ephemeral room chat — max 500 chars, never stored. */
    fun roomChat(roomId: String, body: String) =
        sendFrame("room.chat", JSONObject().put("room_id", roomId).put("body", body.take(500)))

    // -----------------------------------------------------------------------
    // Media — three-step upload (api.md §8)
    // -----------------------------------------------------------------------

    /** Uploads raw bytes and returns the confirmed media id plus its public URL. */
    suspend fun uploadMediaFull(
        kind: String,
        extension: String,
        mimeType: String,
        bytes: ByteArray,
        width: Int = 0,
        height: Int = 0,
        durationMs: Long = 0,
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val req = JSONObject().put("kind", kind).put("extension", extension)
        val step1 = postData("/api/v1/media/upload-url", req) as JSONObject
        val mediaId = step1.getString("media_id")
        val storageKey = step1.getString("storage_key")
        val uploadUrl = step1.getString("upload_url")

        http.newCall(
            Request.Builder().url(uploadUrl).put(bytes.toRequestBody(mimeType.toMediaType())).build(),
        ).execute().use { require(it.isSuccessful) { "media upload failed: ${it.code}" } }

        val confirm = JSONObject()
            .put("kind", kind).put("storage_key", storageKey).put("mime_type", mimeType)
            .put("size_bytes", bytes.size).put("width", width).put("height", height)
            .put("duration_ms", durationMs)
        val done = postData("/api/v1/media/$mediaId/confirm", confirm) as JSONObject
        done.getString("id") to done.optString("url", "")
    }

    /** Uploads raw bytes and returns the confirmed media id. */
    suspend fun uploadMedia(
        kind: String,
        extension: String,
        mimeType: String,
        bytes: ByteArray,
        width: Int = 0,
        height: Int = 0,
        durationMs: Long = 0,
    ): String = withContext(Dispatchers.IO) {
        val req = JSONObject().put("kind", kind).put("extension", extension)
        val step1 = postData("/api/v1/media/upload-url", req) as JSONObject
        val mediaId = step1.getString("media_id")
        val storageKey = step1.getString("storage_key")
        val uploadUrl = step1.getString("upload_url")

        // Step 2: PUT raw bytes straight to storage.
        http.newCall(
            Request.Builder().url(uploadUrl).put(bytes.toRequestBody(mimeType.toMediaType())).build(),
        ).execute().use { require(it.isSuccessful) { "media upload failed: ${it.code}" } }

        // Step 3: confirm.
        val confirm = JSONObject()
            .put("kind", kind).put("storage_key", storageKey).put("mime_type", mimeType)
            .put("size_bytes", bytes.size).put("width", width).put("height", height)
            .put("duration_ms", durationMs)
        (postData("/api/v1/media/$mediaId/confirm", confirm) as JSONObject).getString("id")
    }

    // -----------------------------------------------------------------------
    // WebSocket (api.md §9)
    // -----------------------------------------------------------------------

    private val listeners = CopyOnWriteArrayList<SocketListener>()
    private val subscribed = CopyOnWriteArrayList<String>()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var wantConnection = false

    fun addListener(l: SocketListener) { listeners.add(l) }
    fun removeListener(l: SocketListener) { listeners.remove(l) }

    private inline fun dispatch(crossinline block: (SocketListener) -> Unit) =
        main.post { listeners.forEach { block(it) } }

    fun connect() {
        wantConnection = true
        if (socket != null) return
        val url = buildString {
            append(ApiConfig.WS_URL).append("/api/v1/ws")
            if (!ApiConfig.useDebugUser) jwt?.let { append("?token=").append(it) }
        }
        val builder = Request.Builder().url(url)
        if (ApiConfig.useDebugUser) builder.header("X-Debug-User", ApiConfig.DEBUG_USER_ID)
        socket = http.newWebSocket(builder.build(), wsListener)
    }

    fun disconnect() {
        wantConnection = false
        socket?.close(1000, "bye")
        socket = null
    }

    private fun sendFrame(type: String, data: JSONObject? = null, ref: String? = null) {
        val frame = JSONObject().put("type", type)
        if (ref != null) frame.put("ref", ref)
        if (data != null) frame.put("data", data)
        socket?.send(frame.toString())
    }

    fun subscribe(topics: List<String>) {
        topics.forEach { if (!subscribed.contains(it)) subscribed.add(it) }
        sendFrame("subscribe", JSONObject().put("topics", JSONArray(topics)))
    }

    fun presenceQuery(userIds: List<String>, ref: String = "presence") =
        sendFrame("presence.query", JSONObject().put("user_ids", JSONArray(userIds)), ref)

    fun typingStart(conversationId: String) =
        sendFrame("typing.start", JSONObject().put("conversation_id", conversationId))

    fun typingStop(conversationId: String) =
        sendFrame("typing.stop", JSONObject().put("conversation_id", conversationId))

    fun messageSend(conversationId: String, body: String, ref: String) =
        sendFrame(
            "message.send",
            JSONObject().put("conversation_id", conversationId).put("type", "text").put("body", body),
            ref,
        )

    fun messageRead(conversationId: String, messageId: String) =
        sendFrame("message.read", JSONObject().put("conversation_id", conversationId).put("message_id", messageId))

    /** Tell the sender their message reached this device (✓✓ grey, not yet read). */
    fun messageDelivered(conversationId: String, messageId: String) =
        sendFrame("message.delivered", JSONObject().put("conversation_id", conversationId).put("message_id", messageId))

    private val wsListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (subscribed.isNotEmpty()) {
                webSocket.send(
                    JSONObject().put("type", "subscribe")
                        .put("data", JSONObject().put("topics", JSONArray(subscribed))).toString(),
                )
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val frame = runCatching { JSONObject(text) }.getOrNull() ?: return
            val type = frame.optString("type")
            val ref = if (frame.isNull("ref")) null else frame.optString("ref")
            val data = if (frame.isNull("data")) null else frame.get("data")
            when (type) {
                "ready" -> (data as? JSONObject)?.optString("user_id")?.let { id ->
                    myUserId = id
                    // Subscribe to our own user channel so story.new and
                    // notification.new actually arrive live (the backend fans those
                    // out to user:<id>). Without this they never reached the app and
                    // stories only showed up after a manual refresh.
                    if (id.isNotBlank()) subscribe(listOf("user:$id"))
                    dispatch { it.onReady(id) }
                }
                "message.new" -> (data as? JSONObject)?.toMessage()?.let { m ->
                    // Confirm delivery back to the sender the moment the message lands
                    // on this device — even if the user is only on the chat list and
                    // never opens the chat. This is what turns the sender's ✓ into ✓✓,
                    // and it does not depend on presence being visible.
                    if (m.senderId.isNotBlank() && m.senderId != myUserId) {
                        messageDelivered(m.conversationId, m.id)
                    }
                    dispatch { it.onMessageNew(m) }
                }
                "presence.update" -> (data as? JSONObject)?.let { d ->
                    val p = NetPresence(d.getString("user_id"), d.optBoolean("online"), d.strOrNull("last_seen"))
                    dispatch { it.onPresence(p) }
                }
                "typing" -> (data as? JSONObject)?.let { d ->
                    // The event carries whether the peer STARTED or STOPPED typing;
                    // hardcoding true left "typing…" stuck on forever.
                    dispatch { it.onTyping(d.getString("conversation_id"), d.optString("user_id"), d.optBoolean("typing", true)) }
                }
                "message.read" -> (data as? JSONObject)?.let { d ->
                    dispatch { it.onReadReceipt(d.getString("conversation_id"), d.optString("user_id"), d.optString("message_id")) }
                }
                "message.delivered" -> (data as? JSONObject)?.let { d ->
                    dispatch { it.onDeliveredReceipt(d.getString("conversation_id"), d.optString("user_id"), d.optString("message_id")) }
                }
                "message.deleted" -> (data as? JSONObject)?.let { d ->
                    dispatch { it.onMessageDeleted(d.optString("conversation_id"), d.optString("message_id")) }
                }
                "message.reaction" -> (data as? JSONObject)?.let { d ->
                    dispatch {
                        it.onMessageReaction(
                            d.optString("conversation_id"),
                            d.optString("message_id"),
                            d.optString("user_id"),
                            d.optString("emoji"),
                        )
                    }
                }
                "user.updated" -> (data as? JSONObject)?.let { d ->
                    dispatch {
                        it.onUserUpdated(
                            d.optString("user_id"),
                            d.optString("display_name"),
                            d.strOrNull("avatar_url"),
                        )
                    }
                }
                "reel.like" -> (data as? JSONObject)?.let { d ->
                    dispatch { it.onReelLike(d.optString("reel_id"), d.optString("user_id"), d.optBoolean("liked")) }
                }
                "reel.comment" -> (data as? JSONObject)?.let { d ->
                    dispatch { it.onReelComment(d.optString("reel_id"), d.optString("user_id"), d.optString("body")) }
                }
                "reel.new" -> (data as? JSONObject)?.let { d ->
                    dispatch { it.onReelNew(d.optString("reel_id"), d.optString("author_id")) }
                }
                "reel.deleted" -> (data as? JSONObject)?.let { d ->
                    dispatch { it.onReelDeleted(d.optString("reel_id")) }
                }
                "story.new" -> (data as? JSONObject)?.let { d ->
                    dispatch { it.onStoryNew(d.optString("story_id"), d.optString("author_id")) }
                }
                "room.participants" -> (data as? JSONObject)?.let { d ->
                    val roomId = d.optString("room_id")
                    val list = d.optJSONArray("participants")
                        ?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it).toParticipant() } }
                        ?: emptyList()
                    dispatch { it.onRoomParticipants(roomId, list) }
                }
                "room.speak_request" -> (data as? JSONObject)?.let { d ->
                    val name = d.optString("display_name").ifBlank { d.optString("username") }
                    dispatch {
                        it.onRoomSpeakRequest(d.optString("room_id"), d.optString("user_id"), name)
                    }
                }
                "room.role_changed" -> (data as? JSONObject)?.let { d ->
                    dispatch {
                        it.onRoomRoleChanged(
                            d.optString("room_id"),
                            d.optString("user_id"),
                            d.optString("role", "listener"),
                            d.optBoolean("needs_rejoin", false),
                        )
                    }
                }
                "room.ended" -> (data as? JSONObject)?.let { d ->
                    dispatch { it.onRoomEnded(d.optString("room_id")) }
                }
                "room.created" -> (data as? JSONObject)?.let { d ->
                    dispatch { it.onRoomCreated(d.optString("room_id")) }
                }
                "conversation.updated" -> (data as? JSONObject)?.let { d ->
                    dispatch { it.onConversationUpdated(d.optString("conversation_id", d.optString("id"))) }
                }
                "room.join_decided" -> (data as? JSONObject)?.let { d ->
                    val approved = d.optBoolean("approved", d.optString("status") == "approved")
                    dispatch { it.onRoomJoinDecided(d.optString("room_id"), approved) }
                }
                "notification.new" -> (data as? JSONObject)?.let { d ->
                    dispatch {
                        it.onNotification(
                            d.optString("type"),
                            d.optString("actor_name"),
                            d.optString("actor_username"),
                            d.optString("actor_avatar_url", "").ifBlank { null },
                        )
                    }
                }
                "call.incoming" -> (data as? JSONObject)?.let { d ->
                    dispatch {
                        it.onCallIncoming(
                            d.optString("conversation_id"),
                            d.optString("call_id"),
                            d.optString("kind"),
                            d.optString("initiator_id"),
                        )
                    }
                }
                "call.answered" -> (data as? JSONObject)?.let { d ->
                    dispatch { it.onCallAnswered(d.optString("call_id"), d.optString("conversation_id")) }
                }
                "call.ended" -> (data as? JSONObject)?.let { d ->
                    dispatch {
                        it.onCallEnded(
                            d.optString("call_id"),
                            d.optString("conversation_id"),
                            d.optString("reason"),
                        )
                    }
                }
                "room.message" -> (data as? JSONObject)?.let { d ->
                    val m = NetRoomMessage(
                        roomId = d.optString("room_id"),
                        senderId = d.optString("sender_id"),
                        body = d.optString("body"),
                        createdAt = d.optString("created_at"),
                    )
                    dispatch { it.onRoomMessage(m) }
                }
                "ack" -> {
                    // The presence.query reply comes back as an ack whose data is an
                    // array of {user_id, online, last_seen}. Fan it out as presence
                    // updates so the peer's LIVE status is seeded the moment you open
                    // a chat / the list — without this, presenceQuery did nothing and
                    // you only ever learned someone was online if they (re)connected
                    // while you happened to be listening.
                    if (ref == "presence") {
                        (data as? JSONArray)?.let { arr ->
                            for (i in 0 until arr.length()) {
                                val d = arr.optJSONObject(i) ?: continue
                                val p = NetPresence(d.optString("user_id"), d.optBoolean("online"), d.strOrNull("last_seen"))
                                dispatch { it.onPresence(p) }
                            }
                        }
                    }
                    dispatch { it.onAck(ref, data) }
                }
                "error" -> dispatch { it.onAck(ref, data) }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = scheduleReconnect()
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = scheduleReconnect()
    }

    private fun scheduleReconnect() {
        socket = null
        if (!wantConnection) return
        main.postDelayed({
            if (wantConnection && socket == null) {
                connect()
                dispatch { it.onReconnect() }
            }
        }, 2000)
    }
}

// ---------------------------------------------------------------------------
// JSON helpers
// ---------------------------------------------------------------------------

private fun JSONObject.strOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else getString(key)

private inline fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> =
    (0 until length()).map { block(getJSONObject(it)) }

private fun JSONObject.toConversation() = NetConversation(
    id = getString("id"),
    type = optString("type", "direct"),
    title = optString("title", ""),
    avatarMediaId = strOrNull("avatar_url") ?: strOrNull("avatar_media_id"),
    counterpartId = strOrNull("counterpart_id"),
    counterpartUsername = strOrNull("counterpart_username"),
    unreadCount = optInt("unread_count", 0),
    lastPreview = optString("last_message_preview", ""),
    lastType = optString("last_message_type", "text"),
    lastSenderId = strOrNull("last_message_sender_id"),
    lastMessageId = strOrNull("last_message_id"),
    lastAt = strOrNull("last_message_at"),
    createdAt = strOrNull("created_at"),
    counterpartLastReadId = strOrNull("counterpart_last_read_id"),
    counterpartLastDeliveredId = strOrNull("counterpart_last_delivered_id"),
)

private fun JSONObject.toMessage() = NetMessage(
    id = getString("id"),
    conversationId = optString("conversation_id", ""),
    senderId = optString("sender_id", ""),
    type = optString("type", "text"),
    body = optString("body", ""),
    replyToId = strOrNull("reply_to_id"),
    createdAt = optString("created_at", ""),
    editedAt = strOrNull("edited_at"),
    isDeleted = optBoolean("is_deleted", false),
    // `attachments` is a list of ready URLs; older shapes nested them in objects.
    attachments = optJSONArray("attachments")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            arr.optString(i, "").ifBlank {
                arr.optJSONObject(i)?.optString("url", "").orEmpty()
            }.takeIf { it.isNotBlank() }
        }
    } ?: emptyList(),
)

private fun JSONObject.toRoom() = NetRoom(
    id = getString("id"),
    hostId = optString("host_id", ""),
    hostUsername = optString("host_username", ""),
    hostName = optString("host_name", ""),
    hostAvatarMediaId = strOrNull("host_avatar_url") ?: strOrNull("host_avatar_media_id"),
    title = optString("title", ""),
    topic = optString("topic", ""),
    visibility = optString("visibility", "public"),
    participantCount = optInt("participant_count", 0),
    speakerCount = optInt("speaker_count", 0),
    maxParticipants = optInt("max_participants", 50),
    startedAt = optString("started_at", ""),
)

private fun JSONObject.toParticipant() = NetRoomParticipant(
    userId = getString("user_id"),
    username = optString("username", ""),
    displayName = optString("display_name", ""),
    avatarMediaId = strOrNull("avatar_url") ?: strOrNull("avatar_media_id"),
    role = optString("role", "listener"),
    isMuted = optBoolean("is_muted", true),
    hasRaisedHand = optBoolean("has_raised_hand", false),
    joinedAt = optString("joined_at", ""),
)

private fun JSONObject.toUser() = NetUser(
    id = getString("id"),
    username = optString("username", ""),
    displayName = optString("display_name", ""),
    // Backend now sends a ready-to-use URL; the old id is kept as a fallback.
    avatarMediaId = strOrNull("avatar_url") ?: strOrNull("avatar_media_id"),
    followerCount = optInt("follower_count", 0),
    followingCount = optInt("following_count", 0),
    followStatus = optString("follow_status", ""),
    isSelf = optBoolean("is_self", false),
)

private fun JSONObject.toStory() = NetStory(
    id = getString("id"),
    mediaId = optString("media_id", ""),
    mediaKind = optString("media_kind", "image"),
    mediaUrl = optString("media_url", ""),
    durationMs = optLong("duration_ms", 0),
    viewed = optBoolean("viewed", false),
    createdAt = optString("created_at", ""),
    expiresAt = optString("expires_at", ""),
)

private fun JSONObject.toStoryGroup() = NetStoryGroup(
    authorId = getString("author_id"),
    username = optString("username", ""),
    displayName = optString("display_name", ""),
    avatarMediaId = strOrNull("avatar_url") ?: strOrNull("avatar_media_id"),
    isCurrentUser = optBoolean("is_current_user", false),
    allViewed = optBoolean("all_viewed", false),
    unviewedCount = optInt("unviewed_count", 0),
    latestStoryAt = optString("latest_story_at", ""),
    stories = optJSONArray("stories")?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it).toStory() } }
        ?: emptyList(),
)
