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
    val unreadCount: Int = 0,
    val lastPreview: String = "",
    val lastType: String = "text",
    val lastSenderId: String? = null,
    val lastAt: String? = null,
    val createdAt: String? = null,
    /**
     * Newest message the other side has read. Ids are UUIDv7 (time-ordered), so
     * `message.id <= this` means "already read" — no receipt table needed.
     */
    val counterpartLastReadId: String? = null,
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
