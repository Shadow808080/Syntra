package com.example.syntra.net

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
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
 * Drives ONE live broadcast over LiveKit.
 *
 * A live is asymmetric, unlike a call: the host connects as PUBLISHER (camera +
 * mic), viewers connect as SUBSCRIBERS (no capture) and render the single host
 * track. Backend only mints the `sfu_token` — video flows host↔SFU↔viewer and
 * never through Syntra. State is Compose [androidx.compose.runtime.State] so the
 * live screens recompose as the host's video arrives or the session drops.
 *
 * A user is only ever broadcasting OR watching, never both, so one singleton is
 * enough (same shape as [CallEngine]).
 */
object LiveEngine {

    @Volatile private var room: Room? = null
    @Volatile private var scope: CoroutineScope? = null
    @Volatile private var appContext: Context? = null

    private val ops = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Connected to the SFU (my own session is live). */
    var connected by mutableStateOf(false)
        private set

    /** Host's own camera track for the self-preview (host side). */
    var localVideo by mutableStateOf<VideoTrack?>(null)
        private set

    /** The host's camera track as seen by a viewer — null until the host publishes. */
    var remoteVideo by mutableStateOf<VideoTrack?>(null)
        private set

    /** True once the host's video is actually flowing (viewer side) — drives a spinner. */
    var hostPresent by mutableStateOf(false)
        private set

    var micEnabled by mutableStateOf(true)
        private set
    var cameraEnabled by mutableStateOf(true)
        private set

    val isActive: Boolean get() = room != null

    /**
     * Connects to the SFU. [asHost] turns on the camera + mic (publisher); a viewer
     * connects silent and only subscribes. Safe to call again; any previous session
     * is dropped first.
     */
    suspend fun connect(context: Context, url: String, token: String, asHost: Boolean) {
        disconnect()
        val app = context.applicationContext
        appContext = app

        val r = LiveKit.create(
            appContext = app,
            options = RoomOptions(adaptiveStream = true, dynacast = true),
        )
        room = r

        val cs = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope = cs
        cs.launch { r.events.collect { onEvent(it) } }

        MusicPlayer.pauseForExternalAudio() // a live takes over audio
        r.connect(url, token)
        connected = true

        val me = r.localParticipant
        if (asHost) {
            runCatching { me.setMicrophoneEnabled(true) }
            runCatching { me.setCameraEnabled(true) }
            micEnabled = true
            cameraEnabled = true
            refreshLocal()
        }
        refreshRemote()
    }

    private fun onEvent(event: RoomEvent) {
        when (event) {
            is RoomEvent.TrackSubscribed,
            is RoomEvent.TrackUnsubscribed,
            is RoomEvent.ParticipantConnected,
            is RoomEvent.ParticipantDisconnected -> refreshRemote()

            is RoomEvent.TrackPublished ->
                if (event.participant == event.room.localParticipant) refreshLocal()
            is RoomEvent.TrackUnpublished ->
                if (event.participant == event.room.localParticipant) refreshLocal()

            is RoomEvent.Disconnected -> connected = false
            else -> Unit
        }
    }

    private fun refreshRemote() {
        val r = room ?: return
        // A live has exactly one publisher — the host. Take the first remote camera track.
        val hostTrack = r.remoteParticipants.values
            .firstNotNullOfOrNull { p ->
                p.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
            }
        remoteVideo = hostTrack
        hostPresent = hostTrack != null
    }

    private fun refreshLocal() {
        val me = room?.localParticipant ?: return
        localVideo = me.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
    }

    suspend fun setMicrophone(enabled: Boolean) {
        runCatching { room?.localParticipant?.setMicrophoneEnabled(enabled) }
        micEnabled = enabled
    }

    /** Toggle the mic from a plain (non-suspend) click handler. */
    fun fireMic() {
        ops.launch { setMicrophone(!micEnabled) }
    }

    suspend fun setCamera(enabled: Boolean) {
        runCatching { room?.localParticipant?.setCameraEnabled(enabled) }
        cameraEnabled = enabled
        refreshLocal()
    }

    /** Flips between front and back cameras (host). */
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
        appContext = null
        room = null
        connected = false
        localVideo = null
        remoteVideo = null
        hostPresent = false
        micEnabled = true
        cameraEnabled = true
    }
}
