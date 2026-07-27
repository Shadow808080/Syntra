package com.example.syntra.net

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.syntra.MainActivity
import com.example.syntra.R

/**
 * Local chat notifications — no Firebase, no Google Play Services. The realtime
 * WebSocket (kept alive by [ChatConnectionService]) delivers `message.new`, and
 * this shows a plain system notification for it. Everything runs on the app's own
 * backend, matching the "no external services / no API keys" rule.
 */
object Notifications {

    const val MESSAGES_CHANNEL = "syntra_messages"
    const val SERVICE_CHANNEL = "syntra_service"
    // v2: IMPORTANCE_HIGH so activity notifications pop up as a heads-up banner.
    // A new id is required because Android freezes a channel's importance after it
    // is first created — the old "syntra_social" would stay DEFAULT forever.
    const val SOCIAL_CHANNEL = "syntra_social_v2"
    const val CALL_CHANNEL = "syntra_call"
    /**
     * Ringing channel — separate from [CALL_CHANNEL] on purpose.
     *
     * CALL_CHANNEL is IMPORTANCE_LOW because it carries the silent "call in progress"
     * foreground-service notice. An incoming call is the opposite: it must be loud, and
     * Android freezes a channel's importance after first creation, so this cannot
     * simply be a louder use of the same id.
     */
    const val RINGING_CHANNEL = "syntra_ringing"
    const val SERVICE_NOTIFICATION_ID = 1001
    const val CALL_NOTIFICATION_ID = 1002
    const val RINGING_NOTIFICATION_ID = 1003

    /** Creates the notification channels once; safe to call repeatedly. */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return

        if (mgr.getNotificationChannel(MESSAGES_CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    MESSAGES_CHANNEL,
                    "Pesan",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Notifikasi pesan chat masuk"
                    enableVibration(true)
                },
            )
        }
        if (mgr.getNotificationChannel(SOCIAL_CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    SOCIAL_CHANNEL,
                    "Aktivitas",
                    // HIGH = pop on screen (heads-up) with sound, like a chat message.
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Balasan komentar, suka, pengikut baru"
                    enableVibration(true)
                    enableLights(true)
                },
            )
        }
        if (mgr.getNotificationChannel(RINGING_CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    RINGING_CHANNEL,
                    "Panggilan masuk",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Dering panggilan masuk"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 700, 600, 700, 600)
                    enableLights(true)
                    setBypassDnd(true)
                    // The system rings this one, using the device's own ringtone, so a
                    // call is audible even when the app was never opened.
                    setSound(
                        android.media.RingtoneManager
                            .getActualDefaultRingtoneUri(context, android.media.RingtoneManager.TYPE_RINGTONE),
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                },
            )
        }
        if (mgr.getNotificationChannel(CALL_CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CALL_CHANNEL,
                    "Panggilan",
                    // Low = quiet + no sound (the call UI already handles ringing); it's
                    // the mandatory "call in progress" notice for the foreground service.
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Menjaga panggilan tetap berjalan saat layar mati"
                    setShowBadge(false)
                },
            )
        }
        if (mgr.getNotificationChannel(SERVICE_CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    SERVICE_CHANNEL,
                    "Koneksi latar belakang",
                    // Low importance = silent, no sound, minimal — it's the mandatory
                    // "app is running" notice for the foreground service, not an alert.
                    NotificationManager.IMPORTANCE_MIN,
                ).apply {
                    description = "Menjaga chat tetap tersambung agar notifikasi masuk"
                    setShowBadge(false)
                },
            )
        }
    }

    /** The persistent, quiet notification the foreground service must display. */
    fun serviceNotification(context: Context): android.app.Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, SERVICE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Syntra aktif")
            .setContentText("Menerima pesan secara real-time")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent(context, null))
            .build()
    }

    /** The ongoing "call in progress" notice the call foreground service must show. */
    fun callNotification(context: Context, video: Boolean): android.app.Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CALL_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (video) "Panggilan video berlangsung" else "Panggilan berlangsung")
            .setContentText("Ketuk untuk kembali ke panggilan")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent(context, null))
            .build()
    }

    /**
     * Shows an incoming-message notification, WhatsApp-style: the sender's profile
     * photo, their name, and the message text. Built with [NotificationCompat.MessagingStyle]
     * so the launcher renders the avatar as the person icon and the name as the title.
     * Notifications are keyed by conversation, so a new message replaces the previous
     * one for the same chat instead of stacking; tapping deep-links into that chat.
     *
     * [avatar] is the sender's photo (already downloaded + circle-cropped by the
     * caller); null falls back to a plain notification with no photo.
     */
    fun showMessage(
        context: Context,
        conversationId: String,
        title: String,
        body: String,
        avatar: Bitmap? = null,
    ) {
        ensureChannels(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val senderName = title.ifBlank { "Pesan baru" }
        val text = body.ifBlank { "Mengirim pesan" }

        // "Me" (the reading user) vs the sender — MessagingStyle needs both. The
        // sender carries the name + avatar, so the system shows profile photo + name.
        val me = Person.Builder().setName("Saya").build()
        val sender = Person.Builder()
            .setName(senderName)
            .apply { if (avatar != null) setIcon(IconCompat.createWithBitmap(avatar)) }
            .build()
        val style = NotificationCompat.MessagingStyle(me)
            .addMessage(text, System.currentTimeMillis(), sender)

        val notification = NotificationCompat.Builder(context, MESSAGES_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            // Some launchers show the large icon instead of the person icon.
            .apply { if (avatar != null) setLargeIcon(avatar) }
            // Keep title/text too, for launchers that ignore MessagingStyle.
            .setContentTitle(senderName)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, conversationId))
            .build()

        // Stable id per conversation so repeated messages update in place.
        val id = 2000 + (conversationId.hashCode() and 0x7FFF)
        // Explicit POST_NOTIFICATIONS check (Android 13+) before notifying — the
        // areNotificationsEnabled() gate above already covers this, but the direct
        // permission check keeps the call unambiguously safe (and satisfies lint).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
        }
    }

    /**
     * A social-activity notification (comment reply, like, follow…). Generic by
     * design: the realtime event carries only a type, which is enough to tell the
     * user something happened and pull them back into the app.
     */
    fun showSocial(
        context: Context,
        type: String,
        actorName: String = "",
        actorUsername: String = "",
        avatar: Bitmap? = null,
        /** The reel this activity relates to; tapping the notification opens it. */
        reelId: String? = null,
    ) {
        ensureChannels(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        // Prefer a real name; fall back to @username, then a neutral "Seseorang".
        val who = actorName.ifBlank { actorUsername.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "Seseorang" }
        val (title, action) = when (type) {
            "comment" -> "Balasan komentar" to "$who membalas komentar kamu"
            "like" -> "Suka baru" to "$who menyukai konten kamu"
            "follow" -> "Pengikut baru" to "$who mulai mengikuti kamu"
            "mention" -> "Kamu disebut" to "$who menyebut kamu di komentar"
            "story_reply" -> "Balasan story" to "$who membalas story kamu"
            "room_live" -> "Room dimulai" to "$who memulai sebuah room"
            else -> "Aktivitas baru" to "$who berinteraksi dengan kamu"
        }

        // MessagingStyle so the launcher renders the actor's photo as the person icon
        // and the name as the title — the same "someone messaged you" look, which
        // reads as a chat/comment reply. Falls back to plain text without a photo.
        val me = Person.Builder().setName("Kamu").build()
        val actor = Person.Builder()
            .setName(who)
            .apply { if (avatar != null) setIcon(IconCompat.createWithBitmap(avatar)) }
            .build()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(title)
            .addMessage(action, System.currentTimeMillis(), actor)

        val notification = NotificationCompat.Builder(context, SOCIAL_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .apply { if (avatar != null) setLargeIcon(avatar) }
            .setContentTitle(who)
            .setContentText(action)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // sound + vibrate → heads-up
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(openReelIntent(context, reelId))
            .build()

        // A distinct id per type so a reply and a like don't overwrite each other,
        // but repeats of the same type collapse instead of stacking endlessly.
        val id = 3000 + (type.hashCode() and 0x0FFF)
        // Explicit POST_NOTIFICATIONS check (Android 13+) before notifying — the
        // areNotificationsEnabled() gate above already covers this, but the direct
        // permission check keeps the call unambiguously safe (and satisfies lint).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
        }
    }

    /** Opens the app straight into a specific reel (deep-link from a notification). */
    private fun openReelIntent(context: Context, reelId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!reelId.isNullOrBlank()) putExtra("open_reel", reelId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        // Unique request code per reel so distinct reels get distinct intents.
        return PendingIntent.getActivity(context, ("reel" + (reelId ?: "")).hashCode(), intent, flags)
    }

    private fun openAppIntent(context: Context, conversationId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (conversationId != null) putExtra("open_conversation", conversationId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getActivity(context, conversationId?.hashCode() ?: 0, intent, flags)
    }

    /**
     * Rings for an incoming call, with Answer and Decline right on the notification.
     *
     * Uses a CallStyle full-screen intent. On a locked or asleep device Android turns
     * that into the full-screen incoming-call UI; where it cannot (Android 14+ without
     * the permission, or a device that declines it), the same notification still shows
     * as a heads-up banner with both buttons — so the call is never silently missed,
     * which is what happened before: with the app closed, nothing appeared at all.
     */
    fun showIncomingCall(
        context: Context,
        callId: String,
        conversationId: String,
        callerName: String,
        video: Boolean,
    ) {
        ensureChannels(context)
        // Same explicit POST_NOTIFICATIONS gate the other notifications use.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fun intentFor(action: String, requestCode: Int): PendingIntent {
            val i = Intent(context, MainActivity::class.java).apply {
                this.action = action
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("call_id", callId)
                putExtra("call_conversation_id", conversationId)
                putExtra("call_caller", callerName)
                putExtra("call_video", video)
            }
            return PendingIntent.getActivity(
                context, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val answer = intentFor(ACTION_ANSWER_CALL, 2001)
        val decline = intentFor(ACTION_DECLINE_CALL, 2002)
        val full = intentFor(ACTION_SHOW_CALL, 2003)
        // Swiping it away must DECLINE, not silently dismiss. Otherwise the caller is
        // left ringing against a device that has already thrown the call away.
        val swipedAway = intentFor(ACTION_DECLINE_CALL, 2004)

        val caller = Person.Builder()
            .setName(callerName.ifBlank { "Panggilan masuk" })
            .setImportant(true)
            .build()

        val n = NotificationCompat.Builder(context, RINGING_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(callerName.ifBlank { "Panggilan masuk" })
            .setContentText(if (video) "Panggilan video masuk" else "Panggilan suara masuk")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            // Keeps it on screen until answered or declined rather than fading away.
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(full, true)
            .setDeleteIntent(swipedAway)
            // Sits in the shade like a system call notification rather than a banner
            // that scrolls away: PUBLIC so it is fully readable on the lock screen,
            // colourised so Android gives it the call treatment, and pinned to the top
            // of the shade by the CALL category + MAX priority above.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColorized(true)
            .setColor(0xFF2E6BF0.toInt())
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            // Clears itself if nobody ever answers, matching the 35s in-app ring —
            // an ongoing notification with no timeout would otherwise sit there forever.
            .setTimeoutAfter(38_000L)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, decline, answer))
            .build()

        // FLAG_INSISTENT makes the ringtone repeat until it is dealt with, which is what
        // separates a call from a message. Set on the built notification because
        // NotificationCompat.Builder has no wrapper for it.
        n.flags = n.flags or android.app.Notification.FLAG_INSISTENT

        runCatching { NotificationManagerCompat.from(context).notify(RINGING_NOTIFICATION_ID, n) }
    }

    /** Stops the ringing notification — answered, declined, or the caller gave up. */
    fun cancelIncomingCall(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(RINGING_NOTIFICATION_ID) }
    }

    const val ACTION_ANSWER_CALL = "com.example.syntra.ANSWER_CALL"
    const val ACTION_DECLINE_CALL = "com.example.syntra.DECLINE_CALL"
    const val ACTION_SHOW_CALL = "com.example.syntra.SHOW_CALL"
}

/**
 * Tracks where the user is, so notifications fire at the right time.
 *
 * The whole point of this app: someone browsing Shorts while waiting for a reply
 * should STILL get a notification when a chat arrives. So we do NOT suppress on
 * foreground — we suppress only when the user is actually reading THAT conversation
 * ([openConversationId]). Everywhere else (Shorts, feed, another chat, background)
 * a new message notifies.
 */
object AppForeground {
    @Volatile
    var isForeground: Boolean = false

    /**
     * Compose-observable foreground flag. Screens read this so playback pauses when
     * the app leaves the foreground — e.g. a Shorts video stops when you background
     * the app, not just when you switch tabs.
     */
    var isForegroundState by androidx.compose.runtime.mutableStateOf(true)

    /** The conversation the user currently has open, or null. Set by the chat screen. */
    @Volatile
    var openConversationId: String? = null

    /**
     * True only while the Shorts tab is the one on screen. A "your comment got a
     * reply" notification is redundant only there (the reply shows live in the
     * open comment sheet); anywhere else in the app the user has no idea, so it
     * must still notify. Set by the Shorts screen.
     */
    @Volatile
    var inShorts: Boolean = false

    /**
     * True while the user is inside a live voice/video room.
     *
     * A room is a real-time commitment with other people in it: seizing the whole
     * screen with an incoming call would drop them mid-sentence for something they
     * have not agreed to yet. While this is set, a call announces itself as a small
     * banner and only takes over once it is actually accepted. Set by the room screen.
     */
    var inVoiceRoom by androidx.compose.runtime.mutableStateOf(false)
}
