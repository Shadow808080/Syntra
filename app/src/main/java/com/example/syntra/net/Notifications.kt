package com.example.syntra.net

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
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
    const val SOCIAL_CHANNEL = "syntra_social"
    const val SERVICE_NOTIFICATION_ID = 1001

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
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Balasan komentar, suka, pengikut baru"
                    enableVibration(true)
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
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    /**
     * A social-activity notification (comment reply, like, follow…). Generic by
     * design: the realtime event carries only a type, which is enough to tell the
     * user something happened and pull them back into the app.
     */
    fun showSocial(context: Context, type: String) {
        ensureChannels(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val (title, body) = when (type) {
            "comment" -> "Balasan komentar" to "Seseorang membalas komentar kamu"
            "like" -> "Suka baru" to "Seseorang menyukai konten kamu"
            "follow" -> "Pengikut baru" to "Seseorang mulai mengikuti kamu"
            "mention" -> "Kamu disebut" to "Seseorang menyebut kamu di komentar"
            "story_reply" -> "Balasan story" to "Seseorang membalas story kamu"
            "room_live" -> "Room dimulai" to "Sebuah room yang kamu ikuti sedang live"
            else -> "Aktivitas baru" to "Ada aktivitas baru di Syntra"
        }

        val notification = NotificationCompat.Builder(context, SOCIAL_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, null))
            .build()

        // A distinct id per type so a reply and a like don't overwrite each other,
        // but repeats of the same type collapse instead of stacking endlessly.
        val id = 3000 + (type.hashCode() and 0x0FFF)
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
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

    /** The conversation the user currently has open, or null. Set by the chat screen. */
    @Volatile
    var openConversationId: String? = null
}
