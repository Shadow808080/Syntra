package com.example.syntra.net

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * Keeps a 1:1 call (audio or video) running while the phone sleeps.
 *
 * Two things Android needs so a call does not die when the screen turns off:
 *  1. A foreground service of type `microphone` (plus `camera` for video). On
 *     Android 14+ an app may only use the mic/camera in the background through a
 *     foreground service of that exact type — without it the call goes silent the
 *     moment the app is backgrounded or the screen locks.
 *  2. A partial wake lock so the CPU keeps processing audio between Doze windows.
 *
 * It also listens for the screen turning off/on and tells [CallEngine] to drop the
 * camera while asleep (video only) and bring it back on wake — "when the phone
 * sleeps the camera turns off, the call keeps going".
 */
class CallService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> CallEngine.onDeviceSleep()
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> CallEngine.onDeviceWake()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "syntra:call")?.apply {
            setReferenceCounted(false)
            // Safety cap so a leaked lock can't drain the battery forever; a real call
            // is far shorter, and disconnect() releases it well before this.
            runCatching { acquire(60 * 60 * 1000L) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val video = intent?.getBooleanExtra(EXTRA_VIDEO, false) ?: false
        val notification = Notifications.callNotification(this, video)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (video) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            // If the typed start is refused (e.g. a permission race) fall back to an
            // untyped foreground start — still satisfies the "must call startForeground"
            // contract so the service can't crash the process.
            runCatching { startForeground(Notifications.CALL_NOTIFICATION_ID, notification, type) }
                .onFailure { runCatching { startForeground(Notifications.CALL_NOTIFICATION_ID, notification) } }
        } else {
            startForeground(Notifications.CALL_NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_VIDEO = "video"

        /** Start the call foreground service. Called from [CallEngine.connect]. */
        fun start(context: Context, video: Boolean) {
            val intent = Intent(context, CallService::class.java).putExtra(EXTRA_VIDEO, video)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Stop it when the call ends. Called from [CallEngine.disconnect]. */
        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, CallService::class.java)) }
        }
    }
}
