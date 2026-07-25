package com.example.syntra.net

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Drives a 1:1 (or small) audio/video call over LiveKit.
 *
 * Like [VoiceEngine] this only turns the backend's `sfu_token` into a real media
 * session — the Syntra backend never carries audio or video (docs/api.md §Calls).
 * State is exposed as Compose [androidx.compose.runtime.State] so the call screen
 * recomposes as the far side connects, publishes video, or hangs up.
 */
object CallEngine {

    @Volatile private var room: Room? = null
    @Volatile private var audio: AudioManager? = null
    @Volatile private var scope: CoroutineScope? = null

    // Long-lived scope for fire-and-forget toggles from non-suspend click handlers.
    private val ops = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Connected to the SFU (my own media session is live). */
    var connected by mutableStateOf(false)
        private set

    /** The other participant is present in the room. */
    var remoteJoined by mutableStateOf(false)
        private set

    var micEnabled by mutableStateOf(true)
        private set
    var cameraEnabled by mutableStateOf(false)
        private set
    var speakerOn by mutableStateOf(true)
        private set

    /** Far-side camera track to render full-screen (null until they publish video). */
    var remoteVideo by mutableStateOf<VideoTrack?>(null)
        private set

    /** My own camera track for the self-preview. */
    var localVideo by mutableStateOf<VideoTrack?>(null)
        private set

    val isActive: Boolean get() = room != null

    /**
     * Connects to the SFU and turns on the microphone (and camera when [video]).
     * Safe to call again; any previous session is dropped first.
     */
    suspend fun connect(context: Context, url: String, token: String, video: Boolean) {
        disconnect()
        val r = LiveKit.create(appContext = context.applicationContext)
        room = r

        val cs = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope = cs
        cs.launch { r.events.collect { onEvent(it) } }

        r.connect(url, token)
        connected = true

        audio = (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.apply {
            mode = AudioManager.MODE_IN_COMMUNICATION
        }
        // Video defaults to loudspeaker; a voice call starts on the earpiece like a
        // normal phone call. Uses the modern routing API (Android 12+ safe).
        setSpeaker(video)

        val me = r.localParticipant
        runCatching { me.setMicrophoneEnabled(true) }
        micEnabled = true
        if (video) {
            runCatching { me.setCameraEnabled(true) }
            cameraEnabled = true
        }
        // Pick up anyone already in the room and my freshly-published tracks.
        refreshLocalVideo()
        refreshRemote()
    }

    private fun onEvent(event: RoomEvent) {
        when (event) {
            is RoomEvent.TrackSubscribed -> {
                remoteJoined = true
                (event.track as? VideoTrack)?.let { remoteVideo = it }
            }
            is RoomEvent.TrackUnsubscribed -> {
                if (event.track === remoteVideo) remoteVideo = null
            }
            is RoomEvent.ParticipantConnected -> remoteJoined = true
            is RoomEvent.ParticipantDisconnected -> {
                remoteJoined = false
                remoteVideo = null
            }
            is RoomEvent.TrackPublished -> {
                if (event.participant == event.room.localParticipant) refreshLocalVideo()
            }
            is RoomEvent.TrackUnpublished -> {
                if (event.participant == event.room.localParticipant) refreshLocalVideo()
            }
            is RoomEvent.Disconnected -> connected = false
            else -> Unit
        }
    }

    private fun refreshRemote() {
        val r = room ?: return
        val peer = r.remoteParticipants.values.firstOrNull()
        remoteJoined = peer != null
        remoteVideo = peer?.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
    }

    private fun refreshLocalVideo() {
        val me = room?.localParticipant ?: return
        localVideo = me.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
    }

    fun setSpeaker(on: Boolean) {
        val am = audio
        if (am != null) {
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
        speakerOn = on
    }

    suspend fun setMicrophone(enabled: Boolean) {
        runCatching { room?.localParticipant?.setMicrophoneEnabled(enabled) }
        micEnabled = enabled
    }

    suspend fun setCamera(enabled: Boolean) {
        runCatching { room?.localParticipant?.setCameraEnabled(enabled) }
        cameraEnabled = enabled
        refreshLocalVideo()
    }

    /** Toggle the mic from a plain (non-suspend) click handler. */
    fun fireMic() {
        ops.launch { setMicrophone(!micEnabled) }
    }

    /** Toggle the camera from a plain (non-suspend) click handler. */
    fun fireCamera(enabled: Boolean) {
        ops.launch { setCamera(enabled) }
    }

    /** Flips between front and back cameras. */
    fun switchCamera() {
        runCatching { (localVideo as? LocalVideoTrack)?.switchCamera() }
    }

    /** Grabs the shared EGL context so a renderer view can display a track. */
    fun initRenderer(view: io.livekit.android.renderer.TextureViewRenderer) {
        runCatching { room?.initVideoRenderer(view) }
    }

    fun disconnect() {
        runCatching { scope?.cancel() }
        scope = null
        runCatching { room?.disconnect() }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio?.clearCommunicationDevice()
        }
        runCatching { audio?.mode = AudioManager.MODE_NORMAL }
        room = null
        audio = null
        connected = false
        remoteJoined = false
        remoteVideo = null
        localVideo = null
        micEnabled = true
        cameraEnabled = false
        speakerOn = true
    }
}
