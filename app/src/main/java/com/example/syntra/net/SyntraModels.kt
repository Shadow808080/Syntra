package com.example.syntra.net

// Plain domain models mirroring the JSON in api.md. Parsing lives in SyntraClient.

data class NetUser(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarMediaId: String? = null,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    /** "" = not followed · "pending" · "accepted" */
    val followStatus: String = "",
    val isSelf: Boolean = false,
)

data class NetConversation(
    val id: String,
    val type: String,               // "direct" | "group"
    val title: String,
    val avatarMediaId: String? = null,
    val counterpartId: String? = null,
    /** Username of the other participant in a direct chat — needed to block/report. */
    val counterpartUsername: String? = null,
    val unreadCount: Int = 0,
    val lastPreview: String = "",
    val lastType: String = "text",
    val lastSenderId: String? = null,
    val lastMessageId: String? = null,
    val lastAt: String? = null,
    val createdAt: String? = null,
    /**
     * Newest message the other side has read. Ids are UUIDv7 (time-ordered), so
     * `message.id <= this` means "already read" — no receipt table needed.
     */
    val counterpartLastReadId: String? = null,
    /**
     * Newest message that reached the other side's device (✓✓ grey), whether or
     * not they've read it. Compared the same way as [counterpartLastReadId].
     */
    val counterpartLastDeliveredId: String? = null,
)

data class NetMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val type: String = "text",
    val body: String = "",
    val replyToId: String? = null,
    val createdAt: String = "",
    val editedAt: String? = null,
    val isDeleted: Boolean = false,
    /** Ready-to-use media URLs resolved by the server (`attachments`). */
    val attachments: List<String> = emptyList(),
)

data class NetPresence(
    val userId: String,
    val online: Boolean,
    val lastSeen: String? = null,
)

data class NetStory(
    val id: String,
    val mediaId: String,
    val mediaKind: String,          // "image" | "video"
    val mediaUrl: String,
    val durationMs: Long = 0,
    val viewed: Boolean = false,
    val createdAt: String = "",
    val expiresAt: String = "",
    /** Music attached to this story (from overlays.music), or null. */
    val music: StoryMusic? = null,
)

/** A song stuck to a story: 30-second preview + display info. */
data class StoryMusic(
    val title: String,
    val artist: String,
    val previewUrl: String,
    val artworkUrl: String? = null,
)

// Voice rooms — shapes taken from docs/voice-rooms.md + rest/handler/room.go.

data class NetRoom(
    val id: String,
    val hostId: String = "",
    val hostUsername: String = "",
    val hostName: String = "",
    val hostAvatarMediaId: String? = null,
    val title: String,
    val topic: String = "",
    val visibility: String = "public",
    val participantCount: Int = 0,
    val speakerCount: Int = 0,
    val maxParticipants: Int = 50,
    val startedAt: String = "",
)

/** Room list plus `meta.sfu_ready` — Join must be hidden when the SFU is unconfigured. */
data class NetRoomList(
    val rooms: List<NetRoom>,
    val sfuReady: Boolean,
)

/** Role: "host" | "moderator" | "speaker" | "listener". */
data class NetRoomParticipant(
    val userId: String,
    val username: String = "",
    val displayName: String = "",
    val avatarMediaId: String? = null,
    val role: String = "listener",
    val isMuted: Boolean = true,
    val hasRaisedHand: Boolean = false,
    val joinedAt: String = "",
)

/** Result of POST /rooms/{id}/join — carries the LiveKit credentials. */
data class NetRoomJoin(
    val roomId: String,
    val role: String,
    val canPublish: Boolean,
    val sfuRoomId: String,
    val sfuToken: String,
    val sfuUrl: String,
)

/** Ephemeral room chat message (never stored server-side). */
data class NetRoomMessage(
    val roomId: String,
    val senderId: String,
    val body: String,
    val createdAt: String,
)

/** One of my own stories, from `GET /stories/me` — carries the view count. */
data class NetMyStory(
    val id: String,
    val mediaUrl: String = "",
    val mediaKind: String = "image",
    val viewCount: Int = 0,
    val isExpired: Boolean = false,
    val createdAt: String = "",
    val expiresAt: String = "",
)

/** Someone who watched my story. */
data class NetStoryViewer(
    val userId: String,
    val username: String = "",
    val displayName: String = "",
    val viewedAt: String = "",
)

data class NetReel(
    val id: String,
    val mediaUrl: String,
    val caption: String = "",
    /** Author fields are flat in the response: `author_id` / `author_username` / `author_name`. */
    val authorId: String = "",
    val creatorUsername: String = "",
    val creatorName: String = "",
    val creatorAvatarUrl: String? = null,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val viewCount: Int = 0,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val isFollowing: Boolean = false,
)

data class NetReelComment(
    val id: String,
    val authorId: String = "",
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val body: String = "",
    val createdAt: String = "",
    /** Non-null when this comment is a reply to another comment. */
    val parentId: String? = null,
)

// Calls (audio & video) — docs/api.md §Calls. Audio/video ride LiveKit; the
// backend only mints the sfu_token, exactly like voice rooms.

/** Credentials handed back by POST /calls and .../answer. */
data class NetCall(
    val callId: String,
    val sfuRoomId: String,
    val sfuToken: String,
    val sfuUrl: String,
    /** true when this call was just created (I'm the initiator). */
    val isNew: Boolean = false,
)

/** Active-call status of a conversation, from GET /conversations/{id}/call. */
data class NetActiveCall(
    val id: String,
    val kind: String,          // "audio" | "video"
    val status: String,        // "ongoing" | ...
    val initiatorId: String,
    val startedAt: String = "",
)

data class NetStoryGroup(
    val authorId: String,
    val username: String,
    val displayName: String,
    val avatarMediaId: String? = null,
    val isCurrentUser: Boolean = false,
    val allViewed: Boolean = false,
    val unviewedCount: Int = 0,
    val latestStoryAt: String = "",
    val stories: List<NetStory> = emptyList(),
)
