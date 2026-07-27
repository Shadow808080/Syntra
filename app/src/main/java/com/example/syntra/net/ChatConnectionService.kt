package com.example.syntra.net

import android.app.Service
import android.content.Context
import android.app.AlarmManager
import android.app.PendingIntent
import android.os.SystemClock
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps the realtime chat WebSocket connected while the app is in the background,
 * and raises a local notification for each incoming message — all without Firebase
 * or Google Play Services. This is the "notif tanpa google-services.json" path: the
 * app's own backend pushes over the socket the service holds open.
 *
 * Limits (Android, not a bug): if the user force-stops the app or an aggressive OEM
 * battery manager kills it, the socket dies and nothing can wake it — that last mile
 * is the only thing FCM would add. A battery-optimisation exemption (offered from
 * the app) keeps this alive far longer.
 */
class ChatConnectionService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Names + avatars for incoming-message notifications, resolved from the
    // conversation list. Kept small and refreshed lazily so a notification shows the
    // sender's name + profile photo, not a raw id. conversationId -> title / avatar url.
    @Volatile private var names: Map<String, String> = emptyMap()
    @Volatile private var avatars: Map<String, String> = emptyMap()

    private val listener = object : SocketListener {
        override fun onReady(userId: String) {
            // Pull conversation titles so notifications can name the sender.
            scope.launch { refreshNames() }
        }

        /**
         * Ring for an incoming call even with the app closed.
         *
         * This listener lives in the SERVICE, which outlives the UI — the in-app
         * handler is a Compose effect and only exists while a screen is composed, so
         * with the app in the background or freshly killed nothing rang at all.
         *
         * When the app IS on screen the in-app call UI handles it; posting here as
         * well would ring twice, so the foreground case is skipped.
         */
        override fun onCallIncoming(conversationId: String, callId: String, kind: String, initiatorId: String) {
            if (callId.isBlank()) return
            if (initiatorId.isNotBlank() && initiatorId == SyntraClient.myUserId) return
            if (AppForeground.isForeground) return
            scope.launch {
                val name = runCatching { SyntraClient.getConversations() }
                    .getOrNull()?.firstOrNull { it.id == conversationId }?.title.orEmpty()
                Notifications.showIncomingCall(
                    context = this@ChatConnectionService,
                    callId = callId,
                    conversationId = conversationId,
                    callerName = name,
                    video = kind == "video",
                )
            }
        }

        override fun onCallEnded(endedCallId: String, endedConversationId: String, reason: String) {
            // The caller gave up, or it was answered elsewhere — stop ringing.
            Notifications.cancelIncomingCall(this@ChatConnectionService)
        }

        override fun onMessageNew(message: NetMessage) {
            // Never notify for my own messages.
            if (message.senderId.isBlank() || message.senderId == SyntraClient.myUserId) return
            // Suppress ONLY when the user is currently reading this exact chat. Being
            // in Shorts / the feed / another chat still notifies — that's the feature.
            if (AppForeground.openConversationId == message.conversationId) return

            val title = names[message.conversationId] ?: "Pesan baru"
            val preview = previewOf(message)
            val avatarUrl = avatars[message.conversationId]

            // Download + circle-crop the avatar (best effort), then post. Falls back to
            // a photo-less notification if there's no avatar or the fetch fails.
            scope.launch {
                val bmp = loadAvatar(avatarUrl)
                Notifications.showMessage(applicationContext, message.conversationId, title, preview, bmp)
            }

            // A new conversation we don't have a name for yet — fetch names so the
            // NEXT message from it is labelled.
            if (!names.containsKey(message.conversationId)) scope.launch { refreshNames() }
        }

        // Social activity (comment reply, like, follow…) — post a system
        // notification. Previously this was skipped whenever the app was in the
        // foreground, so a user on ANY other screen (chat, music, calls…) never
        // learned their comment got a reply. Now it always notifies, except while
        // the user is actually on Shorts, where the reply is already visible live.
        override fun onNotification(
            kind: String,
            actorName: String,
            actorUsername: String,
            actorAvatarUrl: String?,
            subjectType: String,
            subjectId: String,
        ) {
            if (AppForeground.inShorts) return
            // Fetch + circle-crop the actor's photo (best effort), then post a rich
            // notification — name, action, avatar, and a deep-link to the reel.
            val reelId = subjectId.takeIf { subjectType == "reel" && it.isNotBlank() }
            scope.launch {
                val bmp = loadAvatar(actorAvatarUrl)
                Notifications.showSocial(applicationContext, kind, actorName, actorUsername, bmp, reelId)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote to foreground immediately (must happen within a few seconds of start).
        val notification = Notifications.serviceNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Notifications.SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(Notifications.SERVICE_NOTIFICATION_ID, notification)
        }

        // Hold the socket open + listen for messages.
        SyntraClient.addListener(listener)
        SyntraClient.connect()
        scope.launch { refreshNames() }

        // START_STICKY: if the system kills the service (memory pressure) it tries to
        // recreate it — best-effort background survival short of FCM.
        return START_STICKY
    }

    /**
     * The user swiped the app out of Recents.
     *
     * START_STICKY does NOT cover this: a task-removal kill is treated as deliberate,
     * so Android does not restart the service — and on ColorOS/OneUI-style skins the
     * whole process goes with it. Messages and calls then stop arriving until the app
     * is opened again, which is exactly the failure people describe as "notifications
     * only work when the app is open".
     *
     * Re-scheduling through AlarmManager rather than calling startService directly:
     * the process is being torn down as this runs, so anything started inline dies with
     * it. A one-shot alarm a second later starts cleanly in a fresh process.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        runCatching {
            val restart = Intent(applicationContext, ChatConnectionService::class.java)
            val pending = PendingIntent.getService(
                applicationContext, 41, restart,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
            )
            val am = getSystemService(AlarmManager::class.java)
            am?.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000L, pending)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        SyntraClient.removeListener(listener)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun refreshNames() {
        runCatching { SyntraClient.getConversations() }.getOrNull()?.let { convs ->
            names = convs.associate { it.id to it.title.ifBlank { "Pesan baru" } }
            // For direct chats the conversation avatar IS the counterpart's photo.
            avatars = convs.mapNotNull { c ->
                c.avatarMediaId?.takeIf { it.isNotBlank() }?.let { c.id to it }
            }.toMap()
        }
    }

    /** Downloads [url] and circle-crops it for the notification's person icon. */
    private suspend fun loadAvatar(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            withContext(Dispatchers.IO) {
                val loader = coil.ImageLoader(applicationContext)
                val request = coil.request.ImageRequest.Builder(applicationContext)
                    .data(url)
                    .allowHardware(false) // need a software bitmap to draw into a circle
                    .size(128)
                    .build()
                val drawable = (loader.execute(request) as? coil.request.SuccessResult)?.drawable
                (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { circleCrop(it) }
            }
        }.getOrNull()
    }

    /** Crops a square bitmap into a circle so it reads as a profile photo. */
    private fun circleCrop(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        val x = (src.width - size) / 2
        val y = (src.height - size) / 2
        val squared = Bitmap.createBitmap(src, x, y, size, size)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(squared, 0f, 0f, paint)
        return output
    }

    private fun previewOf(m: NetMessage): String {
        val body = m.body
        if (body.isNotBlank()) {
            // Unwrap our app markers (separator 0x01) so a notification never shows a
            // raw "STICKER<0x1>..." body — see ChatDetailScreen / ChatScreen.
            return when {
                body.startsWith("STICKER") -> "Stiker"
                body.startsWith("VIEWONCE") -> "📷 Foto"
                body.startsWith("STORYREPLY") -> "Balasan story"
                else -> body
            }
        }
        val url = m.attachments.firstOrNull().orEmpty().substringBefore('?').lowercase()
        return when {
            url.endsWith(".m4a") || url.endsWith(".mp3") || url.endsWith(".aac") || url.endsWith(".ogg") -> "🎤 Pesan suara"
            url.endsWith(".mp4") || url.endsWith(".mov") || url.endsWith(".webm") -> "🎥 Video"
            url.endsWith(".gif") -> "GIF"
            url.endsWith(".jpg") || url.endsWith(".jpeg") || url.endsWith(".png") || url.endsWith(".webp") -> "📷 Foto"
            url.isNotBlank() -> "📎 Media"
            else -> "Mengirim pesan"
        }
    }

    companion object {
        /** Start the background chat connection (call when signed in). */
        fun start(context: Context) {
            val intent = Intent(context, ChatConnectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Stop it (call on sign-out). */
        fun stop(context: Context) {
            context.stopService(Intent(context, ChatConnectionService::class.java))
        }

        /**
         * True when the system is allowed to doze this app's background work.
         *
         * A foreground service survives most things, but Doze still suspends the
         * network on an idle screen, so the socket dies quietly and messages arrive in
         * a burst whenever the phone wakes. The exemption is what keeps it live.
         */
        fun batteryRestricted(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
            val pm = context.getSystemService(android.os.PowerManager::class.java) ?: return false
            return !pm.isIgnoringBatteryOptimizations(context.packageName)
        }

        /**
         * Opens the system dialog asking to exempt Syntra from battery optimisation.
         *
         * Deliberately user-initiated from Settings rather than fired on launch: this
         * is a permission that materially affects the user's battery, and an app that
         * demands it unprompted at first run deserves to be refused. The app works
         * without it — messages just arrive late once the screen has been off a while.
         */
        fun requestBatteryExemption(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
            runCatching {
                val i = Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:" + context.packageName),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            }.onFailure {
                // Some skins hide that screen — fall back to the app's settings page.
                runCatching {
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
    }
}
