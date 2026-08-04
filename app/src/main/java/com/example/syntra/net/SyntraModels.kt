package com.example.syntra.net

// Plain domain models mirroring the JSON in api.md. Parsing lives in SyntraClient.

data class NetUser(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarMediaId: String? = null,
    /** Ready-to-use URL of the profile background/cover, or null for the gradient. */
    val coverUrl: String? = null,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    /** "" = not followed · "pending" · "accepted" */
    val followStatus: String = "",
    val isSelf: Boolean = false,
)

/** A member of a group conversation, with their [role] (owner|admin|member). */
data class NetMember(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val role: String = "member",
)

/** One person who visited your profile. [avatarUrl] is a ready-to-use image URL. */
data class NetVisitor(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val visitedAt: String = "",
)

/** The "who viewed me" payload: recent visitors plus the grand total. */
data class NetVisitors(
    val total: Int = 0,
    val visitors: List<NetVisitor> = emptyList(),
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

/**
 * What `GET /conversations/{id}` knows about a group that the list does not: a
 * resolved [avatarUrl], the description, the member count, and my own role.
 */
data class GroupInfo(
    val description: String = "",
    val avatarUrl: String? = null,
    val memberCount: Int = 0,
    val myRole: String = "member",
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

/** A song stuck to a story: 30-second preview + display info + how it's shown. */
data class StoryMusic(
    val title: String,
    val artist: String,
    val previewUrl: String,
    val artworkUrl: String? = null,
    /** How the song appears on the story: "card" | "text" | "none" (audio only). */
    val mode: String = "card",
    /** Widget centre as a fraction of the frame (0..1), and its scale. */
    val posX: Float = 0.5f,
    val posY: Float = 0.5f,
    val scale: Float = 1f,
)

// Voice rooms — shapes taken from docs/voice-rooms.md + rest/handler/room.go.

data class NetRoom(
    val id: String,
    val hostId: String = "",
    val hostUsername: String = "",
    val hostName: String = "",
    val hostAvatarMediaId: String? = null,
    /** Host's profile background/cover URL — used as the room card background. */
    val hostCoverUrl: String? = null,
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
    /** Profile background/cover URL — the tile background when the camera is off. */
    val coverUrl: String? = null,
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

// Live streaming — shapes from rest/handler/live.go (migration 66).

/** One ongoing live broadcast (GET /lives, GET /lives/{id}). */
data class NetLive(
    val id: String,
    val hostId: String = "",
    val hostUsername: String = "",
    val hostName: String = "",
    val hostAvatarUrl: String? = null,
    val title: String = "",
    val category: String = "",
    val viewerCount: Int = 0,
    val startedAt: String = "",
)

/** Live list plus `meta.sfu_ready` — the Go Live button must be hidden when the SFU is off. */
data class NetLiveList(
    val lives: List<NetLive>,
    val sfuReady: Boolean,
)

/** Result of POST /lives or /lives/{id}/join — carries the LiveKit credentials. */
data class NetLiveJoin(
    val liveId: String,
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

/**
 * Satu baris di kotak notifikasi.
 *
 * `subjectType` + `subjectId` menentukan ke mana ketukan membawa: sebuah `reel`
 * membuka postingannya, `user` membuka profil orangnya. Tanpa keduanya notifikasi
 * hanya jadi pengumuman yang tak bisa ditindaklanjuti.
 */
data class NetNotification(
    val id: String,
    /** follow · like · comment · mention · story_reply · room_live · system */
    val type: String = "",
    val actorId: String = "",
    val actorUsername: String = "",
    val actorName: String = "",
    val actorAvatarUrl: String? = null,
    val subjectType: String = "",
    val subjectId: String = "",
    val isRead: Boolean = false,
    val createdAt: String = "",
)

/** Satu pesan berbintang, lintas percakapan. */
data class NetStarredMessage(
    val id: String,
    val conversationId: String = "",
    val senderId: String = "",
    val type: String = "text",
    val body: String = "",
    val createdAt: String = "",
    val starredAt: String = "",
)

data class NetReelComment(
    val id: String,
    val authorId: String = "",
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val body: String = "",
    val createdAt: String = "",
    /** Non-null when this comment is a reply to another comment (top-level thread). */
    val parentId: String? = null,
    /** The exact comment this one answers (may be a reply inside the thread) — drives
     *  the quoted preview shown inside the reply. Username/body are for display. */
    val replyToId: String? = null,
    val replyToUsername: String = "",
    val replyToBody: String = "",
    /** Jumlah suka pada komentar ini. */
    val likeCount: Int = 0,
    /** True bila PEMAKAI saat ini sudah menyukai komentar ini. */
    val likedByMe: Boolean = false,
    /** URL lampiran GIF opsional pada komentar (null bila tak ada). Untuk komentar
     *  yang masih dikirim, ini berisi uri/URL lokal supaya GIF-nya sudah terlihat
     *  sebelum server menjawab. */
    val mediaUrl: String? = null,
    val mediaKind: String = "",
    /** Terisi kalau komentar ini pernah diubah setelah dikirim — app menandainya
     *  "diedit" di bawah badan komentar. */
    val editedAt: String? = null,
    /** True selama komentar ini baru ada di layar dan BELUM dikonfirmasi server.
     *  Baris seperti ini digambar redup dengan progres sekali-jalan, lalu tergantikan
     *  oleh salinan asli dari server begitu terkirim. */
    val pending: Boolean = false,
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
