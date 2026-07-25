package com.example.syntra.net

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import io.livekit.android.LiveKit
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Thin wrapper around the LiveKit room used for voice chat.
 *
 * The Syntra backend never carries audio — it only mints the `sfu_token` that says
 * who may listen and who may speak (docs/voice-rooms.md). This class turns that
 * token into an actual audio session.
 *
 * Note on tokens: a token minted while you were a listener has `canPublish: false`
 * baked in. After a promotion you must call `POST /rooms/{id}/join` again and
 * reconnect with the new token, otherwise the mic silently stays off.
 */
object VoiceEngine {

    @Volatile private var room: Room? = null
    @Volatile private var audio: AudioManager? = null
    @Volatile private var scope: CoroutineScope? = null

    val isConnected: Boolean get() = room != null

    /**
     * Live microphone loudness (0..1) per participant, keyed by backend `user_id`
     * (the LiveKit identity = token `sub`). Includes the local user, so the room
     * UI can animate whoever is talking in proportion to how loud they are.
     */
    private val _audioLevels = MutableStateFlow<Map<String, Float>>(emptyMap())
    val audioLevels: StateFlow<Map<String, Float>> = _audioLevels

    /** Connects to the SFU. Safe to call again; the previous session is dropped first. */
    suspend fun connect(context: Context, url: String, token: String) {
        disconnect()
        val r = LiveKit.create(appContext = context.applicationContext)

        // Route audio BEFORE connecting so the very first remote frames play out of
        // the loudspeaker instead of the (near-silent) earpiece.
        audio = (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.apply {
            mode = AudioManager.MODE_IN_COMMUNICATION
        }
        setLoudspeaker(true)

        r.connect(url, token)
        room = r

        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s

        // Collect room events. Remote audio is auto-subscribed and auto-played, but
        // collecting events keeps the session healthy and lets us react to speakers
        // joining/leaving immediately instead of only on the next poll tick.
        s.launch {
            runCatching { r.events.collect { /* keep the event stream flowing */ } }
        }

        // Sample every participant's audio level ~12×/sec into a smooth UI signal.
        // Identity == user's UUID (token `sub`).
        s.launch {
            while (isActive) {
                val rm = room
                if (rm != null) {
                    val levels = HashMap<String, Float>()
                    rm.localParticipant.let { lp ->
                        lp.identity?.value?.let { levels[it] = lp.audioLevel.coerceIn(0f, 1f) }
                    }
                    rm.remoteParticipants.values.forEach { rp ->
                        rp.identity?.value?.let { levels[it] = rp.audioLevel.coerceIn(0f, 1f) }
                    }
                    _audioLevels.value = levels
                }
                delay(80)
            }
        }
    }

    /**
     * Routes audio to the loudspeaker or the earpiece.
     *
     * On Android 12+ `isSpeakerphoneOn` is deprecated and frequently a no-op — the
     * audio then plays through the (near-silent) earpiece and the room sounds
     * "broken / no sound". Use the modern communication-device API there, and fall
     * back to the old flag only on older releases.
     */
    fun setLoudspeaker(on: Boolean) {
        val am = audio ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val target = if (on) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                val device = am.availableCommunicationDevices.firstOrNull { it.type == target }
                if (device != null) am.setCommunicationDevice(device)
                else @Suppress("DEPRECATION") { am.isSpeakerphoneOn = on }
            }
        } else {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = on
        }
    }

    /** [level] 0..1 mapped onto the voice-call stream. */
    fun setVolume(level: Float) {
        val am = audio ?: return
        val stream = AudioManager.STREAM_VOICE_CALL
        val max = am.getStreamMaxVolume(stream)
        val target = (level.coerceIn(0f, 1f) * max).toInt().coerceIn(0, max)
        runCatching { am.setStreamVolume(stream, target, 0) }
    }

    /** Turns the microphone on/off. No-op when not connected or not allowed to publish. */
    suspend fun setMicrophoneEnabled(enabled: Boolean) {
        runCatching { room?.localParticipant?.setMicrophoneEnabled(enabled) }
    }

    fun disconnect() {
        runCatching { scope?.cancel() }
        scope = null
        _audioLevels.value = emptyMap()
        runCatching { room?.disconnect() }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio?.clearCommunicationDevice()
        }
        runCatching { audio?.mode = AudioManager.MODE_NORMAL }
        room = null
        audio = null
    }
}
