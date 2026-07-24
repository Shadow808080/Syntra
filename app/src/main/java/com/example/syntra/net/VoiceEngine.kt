package com.example.syntra.net

import android.content.Context
import android.media.AudioManager
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
     * Identities (backend `user_id` UUIDs) of participants LiveKit reports as
     * currently speaking — including the local user. The room UI observes this to
     * animate whoever is actually talking, live.
     */
    private val _speakingIds = MutableStateFlow<Set<String>>(emptySet())
    val speakingIds: StateFlow<Set<String>> = _speakingIds

    /** Connects to the SFU. Safe to call again; the previous session is dropped first. */
    suspend fun connect(context: Context, url: String, token: String) {
        disconnect()
        val r = LiveKit.create(appContext = context.applicationContext)
        r.connect(url, token)
        room = r
        // Track active speakers. The identity LiveKit carries is the user's UUID
        // (the token's `sub`), which matches NetRoomParticipant.userId.
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        s.launch {
            r.events.collect { event ->
                if (event is RoomEvent.ActiveSpeakersChanged) {
                    _speakingIds.value = event.speakers.mapNotNull { it.identity?.value }.toSet()
                }
            }
        }
        audio = (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.apply {
            // A voice room routed to the earpiece sounds broken — it is barely audible
            // unless the phone is against your ear. Default to the loudspeaker.
            mode = AudioManager.MODE_IN_COMMUNICATION
            isSpeakerphoneOn = true
        }
    }

    /** Routes audio to the loudspeaker or the earpiece. */
    fun setLoudspeaker(on: Boolean) {
        audio?.isSpeakerphoneOn = on
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
        _speakingIds.value = emptySet()
        runCatching { room?.disconnect() }
        runCatching { audio?.mode = AudioManager.MODE_NORMAL }
        room = null
        audio = null
    }
}
