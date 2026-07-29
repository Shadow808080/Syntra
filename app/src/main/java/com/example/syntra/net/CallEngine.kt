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
    @Volatile private var appContext: Context? = null

    /** True while the active call is a video call (drives the sleep camera logic). */
    @Volatile private var videoCall = false
    /** True when we auto-disabled the camera because the screen turned off, so we know
     *  to switch it back on when the screen returns (but not if the user turned it off). */
    @Volatile private var cameraSuspendedBySleep = false

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

    /**
     * EVERY remote participant, not just the first.
     *
     * The engine was written for 1:1 and read `remoteParticipants.values.firstOrNull()`
     * everywhere, so a third person in the room was invisible: their audio played (the
     * SFU sends it regardless) but nothing on screen ever acknowledged them. Group
     * calls need the whole set.
     */
    var remotePeers by mutableStateOf<List<RemotePeer>>(emptyList())
        private set

    /**
     * True while MY microphone is picking up speech.
     *
     * Without this the local tile was hardcoded to "not speaking", so the minimized
     * pill could never show you — it followed everyone except the one person it always
     * had the data for.
     */
    var localSpeaking by mutableStateOf(false)
        private set

    /** One other person in the call. */
    data class RemotePeer(
        val identity: String,
        val name: String,
        val video: VideoTrack?,
        val speaking: Boolean,
    )

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
        val app = context.applicationContext
        appContext = app
        videoCall = video
        cameraSuspendedBySleep = false
        // Video-call room options. Every one of these was at its default, and the
        // defaults are tuned for a laptop on wifi, not an RMX2180 on mobile data.
        val r = LiveKit.create(
            appContext = app,
            options = io.livekit.android.RoomOptions(
                // Pause / downgrade a remote track whose tile is small or off-screen.
                // Defaults to FALSE, so a five-person bento grid decoded five
                // full-size streams to paint five thumbnails — and kept decoding the
                // ones scrolled out of view. This is the single biggest lever here.
                adaptiveStream = true,
                // Stop sending layers nobody is subscribed to. Also default FALSE, so
                // the phone encoded and uploaded quality nobody had asked for.
                dynacast = true,
                videoTrackCaptureDefaults = io.livekit.android.room.track.LocalVideoTrackOptions(
                    // LiveKit captures 720p by default. On a phone tile that detail is
                    // invisible, but the capture, encode and upload are all paid in
                    // full — and on this class of device the encoder is the thing that
                    // runs out first. 360p at 24fps is about a quarter of the work.
                    captureParams = io.livekit.android.room.track.VideoCaptureParameter(
                        width = 640,
                        height = 360,
                        maxFps = 24,
                    ),
                ),
                videoTrackPublishDefaults = io.livekit.android.room.participant.VideoTrackPublishDefaults(
                    videoEncoding = io.livekit.android.room.track.VideoEncoding(
                        maxBitrate = 500_000,
                        maxFps = 24,
                    ),
                    // Under pressure, shed pixels rather than frames. A call is faces
                    // and lips; a sharp slideshow reads as broken where a soft but
                    // smooth picture reads as a weak signal.
                    degradationPreference =
                    livekit.org.webrtc.RtpParameters.DegradationPreference.MAINTAIN_FRAMERATE,
                ),
            ),
        )
        room = r

        val cs = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope = cs
        cs.launch { r.events.collect { onEvent(it) } }

        // Keep the call alive when the phone sleeps: a microphone (+camera) foreground
        // service holds the mic/network open in the background and takes a wake lock.
        runCatching { CallService.start(app, video) }

        MusicPlayer.pauseForExternalAudio() // a call takes over audio
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
            is RoomEvent.TrackSubscribed -> refreshRemote()
            is RoomEvent.TrackUnsubscribed -> refreshRemote()
            // Recomputed from the room rather than toggled by hand: with more than two
            // people, one person leaving does NOT mean the call is empty.
            is RoomEvent.ParticipantConnected -> refreshRemote()
            is RoomEvent.ParticipantDisconnected -> refreshRemote()
            is RoomEvent.ActiveSpeakersChanged -> refreshRemote()
            is RoomEvent.TrackPublished -> {
                if (event.participant == event.room.localParticipant) refreshLocalVideo()
            }
            is RoomEvent.TrackUnpublished -> {
                if (event.participant == event.room.localParticipant) refreshLocalVideo()
            }
            is RoomEvent.Disconnected -> {
                // Logged with its reason: a media session that drops right after the
                // handshake is indistinguishable on screen from a call the far side
                // hung up, and that ambiguity is why this bug survived two fixes.
                android.util.Log.w("SyntraCall", "livekit Disconnected: ${event.error?.message ?: "no error"}")
                connected = false
            }
            else -> Unit
        }
    }

    private fun refreshRemote() {
        val r = room ?: return
        localSpeaking = r.localParticipant.isSpeaking && micEnabled
        val peers = r.remoteParticipants.values.toList()
        remoteJoined = peers.isNotEmpty()
        remotePeers = peers.map { p ->
            RemotePeer(
                identity = p.identity?.value.orEmpty(),
                name = p.name?.takeIf { it.isNotBlank() } ?: p.identity?.value.orEmpty(),
                video = p.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack,
                speaking = p.isSpeaking,
            )
        }
        // The full-screen stage still shows ONE video — whoever is speaking, else the
        // first with a camera on. A five-way grid is a different screen; this keeps the
        // existing 1:1 layout correct while the participant strip carries the rest.
        remoteVideo = (peers.firstOrNull { it.isSpeaking }
            ?: peers.firstOrNull { it.getTrackPublication(Track.Source.CAMERA)?.track != null })
            ?.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
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

    /**
     * The screen turned off (phone sleeping). On a video call, turn the camera off —
     * a backgrounded camera would only publish a frozen/black frame and drains the
     * battery. Audio keeps flowing, so the call continues. Remembered so [onDeviceWake]
     * can restore it, but only if the user hadn't already switched the camera off.
     */
    fun onDeviceSleep() {
        if (room == null || !videoCall) return
        if (cameraEnabled) {
            cameraSuspendedBySleep = true
            ops.launch { setCamera(false) }
        }
    }

    /** The screen came back on: restore the camera if we were the ones who cut it. */
    fun onDeviceWake() {
        if (room == null || !videoCall) return
        if (cameraSuspendedBySleep) {
            cameraSuspendedBySleep = false
            ops.launch { setCamera(true) }
        }
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
        // Tear down the call foreground service + wake lock.
        appContext?.let { ctx -> runCatching { CallService.stop(ctx) } }
        appContext = null
        videoCall = false
        cameraSuspendedBySleep = false
        room = null
        audio = null
        connected = false
        remoteJoined = false
        remoteVideo = null
        remotePeers = emptyList()
        localSpeaking = false
        localVideo = null
        micEnabled = true
        cameraEnabled = false
        speakerOn = true
    }
}
