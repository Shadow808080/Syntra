package com.example.syntra.net

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import io.livekit.android.LiveKit
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Thin wrapper around the LiveKit room used for voice AND video rooms.
 *
 * The Syntra backend never carries media — it only mints the `sfu_token` that says
 * who may publish (speaker) and who may only listen (docs/voice-rooms.md). A speaker
 * token grants BOTH audio and video publish, so a room can be a plain voice room or,
 * the moment someone turns their camera on, a video room — no backend change needed.
 *
 * Note on tokens: a token minted while you were a listener has `canPublish: false`
 * baked in. After a promotion you must call `POST /rooms/{id}/join` again and
 * reconnect with the new token, otherwise the mic/camera silently stay off.
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

    /**
     * Camera video tracks per participant, keyed by `user_id`. Includes the local
     * camera (so the grid can render your own preview the same way as everyone
     * else). A user absent from this map simply has their camera off.
     */
    private val _videoTracks = MutableStateFlow<Map<String, VideoTrack>>(emptyMap())
    val videoTracks: StateFlow<Map<String, VideoTrack>> = _videoTracks

    /** Whether MY camera is currently publishing. */
    private val _cameraOn = MutableStateFlow(false)
    val cameraOn: StateFlow<Boolean> = _cameraOn

    /** Whether MY VTuber avatar is currently publishing (in place of the camera). */
    private val _avatarOn = MutableStateFlow(false)
    val avatarOn: StateFlow<Boolean> = _avatarOn

    // The avatar's published track, kept so it can be unpublished cleanly.
    @Volatile private var avatarTrack: io.livekit.android.room.track.LocalVideoTrack? = null

    /** Connects to the SFU. Safe to call again; the previous session is dropped first. */
    suspend fun connect(context: Context, url: String, token: String) {
        disconnect()
        // Inject the real-time voice changer as a mic capture post-processor. It bypasses
        // itself when the mode is Normal, so an untouched room pays nothing.
        val overrides = io.livekit.android.LiveKitOverrides(
            audioOptions = io.livekit.android.AudioOptions(
                audioProcessorOptions = io.livekit.android.audio.AudioProcessorOptions(
                    RoomVoicePitchProcessor(), false, null, false,
                ),
            ),
        )
        // Same tuning as a video call — a room can hold far more people, so the cost of
        // the defaults is worse here, not better. See CallEngine.connect for why each
        // one matters. The avatar capturer publishes through this too, so its frames
        // ride the same 360p/24fps budget instead of a 720p one nobody can see.
        val r = LiveKit.create(
            appContext = context.applicationContext,
            options = io.livekit.android.RoomOptions(
                adaptiveStream = true,
                dynacast = true,
                videoTrackCaptureDefaults = io.livekit.android.room.track.LocalVideoTrackOptions(
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
                    degradationPreference =
                    livekit.org.webrtc.RtpParameters.DegradationPreference.MAINTAIN_FRAMERATE,
                ),
            ),
            overrides = overrides,
        )

        // Route audio BEFORE connecting so the very first remote frames play out of
        // the loudspeaker instead of the (near-silent) earpiece.
        audio = (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.apply {
            mode = AudioManager.MODE_IN_COMMUNICATION
        }
        setLoudspeaker(true)

        MusicPlayer.pauseForExternalAudio() // a room takes over audio
        r.connect(url, token)
        room = r

        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s

        // Collect room events. Remote tracks are auto-subscribed; collecting events
        // keeps the session healthy and lets us react to cameras/speakers changing.
        s.launch {
            runCatching { r.events.collect { /* keep the event stream flowing */ } }
        }

        // Sample audio levels ~12x/sec AND reconcile the video-track map. The map is
        // only re-emitted when it actually changes, so idle recompositions stay cheap.
        s.launch {
            while (isActive) {
                val rm = room
                if (rm != null) {
                    val levels = HashMap<String, Float>()
                    val videos = HashMap<String, VideoTrack>()
                    rm.localParticipant.let { lp ->
                        val id = lp.identity?.value
                        if (id != null) {
                            val lvl = lp.audioLevel.coerceIn(0f, 1f)
                            levels[id] = lvl
                            // Drive the VTuber mouth from my own loudness.
                            if (_avatarOn.value) VtuberFx.feedLevel(lvl)
                            // Gate on INTENT, not on what LiveKit still reports.
                            //
                            // This is what kept the tile black. After unpublishing the
                            // avatar (or disabling the camera) LiveKit keeps returning
                            // the CAMERA publication for a short window, and this loop
                            // runs every 80 ms — so it re-added the dead track faster
                            // than setAvatarEnabled/setCameraEnabled could remove it,
                            // and the grid went on rendering a track with no frames.
                            // NOTE: no `muted` check here, only intent.
                            //
                            // Adding one broke the VTuber outright. The avatar is
                            // published by hand (publishVideoTrack) rather than through
                            // the high-level camera API, so its publication reports
                            // muted = true even while it is happily producing frames —
                            // the flag tracks the high-level mute that was never set.
                            // Filtering on it dropped the avatar every poll, so the tile
                            // fell back to the profile picture and flickered as the
                            // explicit set and the poll fought over the same slot.
                            if (_cameraOn.value || _avatarOn.value) {
                                (lp.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack)
                                    ?.let { videos[id] = it }
                            }
                        }
                    }
                    rm.remoteParticipants.values.forEach { rp ->
                        val id = rp.identity?.value ?: return@forEach
                        levels[id] = rp.audioLevel.coerceIn(0f, 1f)
                        // Same for peers: a muted publication is someone who turned
                        // their camera off, and rendering it leaves them as a black
                        // rectangle instead of falling back to their avatar cover.
                        rp.getTrackPublication(Track.Source.CAMERA)
                            ?.takeIf { !it.muted }
                            ?.let { pub -> (pub.track as? VideoTrack)?.let { videos[id] = it } }
                    }
                    _audioLevels.value = levels
                    // Emit video only on change (key set or track refs), to avoid
                    // re-rendering every surface at 12 fps for nothing.
                    if (!sameTracks(_videoTracks.value, videos)) {
                        _videoTracks.value = videos
                    }
                }
                delay(80)
            }
        }
    }

    /** True when both maps hold the same user ids pointing at the same track objects. */
    private fun sameTracks(a: Map<String, VideoTrack>, b: Map<String, VideoTrack>): Boolean {
        if (a.size != b.size) return false
        for ((k, v) in a) if (b[k] !== v) return false
        return true
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

    /**
     * Turns MY camera on/off (turning a voice room into a video room). No-op when not
     * connected or the token is listener-only. Updates [cameraOn] and the track map.
     */
    suspend fun setCameraEnabled(enabled: Boolean) {
        val lp = room?.localParticipant ?: return
        // Camera and avatar both own the CAMERA source — only one at a time.
        if (enabled && _avatarOn.value) {
            setAvatarEnabled(enabled = false)
            // Let LiveKit finish releasing the source before claiming it again. The
            // avatar path already waits here for the mirror-image case; without the
            // same wait, turning the camera ON straight after the avatar published a
            // camera track that never produced a frame — a permanently black tile.
            delay(150)
        }
        runCatching { lp.setCameraEnabled(enabled) }
        _cameraOn.value = enabled
        val id = lp.identity?.value
        if (id != null) {
            val cur = HashMap(_videoTracks.value)
            val track = lp.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
            if (enabled && track != null) cur[id] = track else cur.remove(id)
            _videoTracks.value = cur
        }
    }

    /**
     * Turns MY "Mode VTuber" avatar on/off. The avatar is a locally-rendered video
     * ([AvatarVideoCapturer]) published under the CAMERA source, so everyone in the room
     * sees it exactly like a camera — no backend change, no camera permission. Mutually
     * exclusive with the real camera.
     */
    suspend fun setAvatarEnabled(enabled: Boolean) {
        val lp = room?.localParticipant ?: return
        if (enabled) {
            if (_avatarOn.value) return
            val id = lp.identity?.value
            // Fully free the CAMERA source BEFORE republishing the avatar under it.
            //
            // The camera (high-level lp.setCameraEnabled) and the avatar (manual
            // publishVideoTrack) share Track.Source.CAMERA. From a cold start that is
            // clean, but turning the camera ON and THEN switching to the avatar RACED:
            // the camera's teardown wasn't finished when the avatar publish landed, the
            // publish was refused, and the now-dead camera track kept being rendered —
            // a black tile that stayed black even after toggling the camera off.
            //
            // So: check for a real camera publication (not just the _cameraOn flag,
            // which can drift), disable it, drop it from the grid at once, and let
            // LiveKit settle before publishing under the freed source.
            val hadCamera = _cameraOn.value ||
                lp.getTrackPublication(Track.Source.CAMERA)?.track is LocalVideoTrack
            if (hadCamera) {
                runCatching { lp.setCameraEnabled(false) }
                _cameraOn.value = false
                if (id != null) {
                    val cur = HashMap(_videoTracks.value); cur.remove(id); _videoTracks.value = cur
                }
                delay(150)
            }
            val ok = runCatching {
                val capturer = AvatarVideoCapturer()
                val track = lp.createVideoTrack(name = "avatar", capturer = capturer)
                track.startCapture()
                lp.publishVideoTrack(
                    track,
                    io.livekit.android.room.participant.VideoTrackPublishOptions(
                        source = Track.Source.CAMERA,
                    ),
                )
                avatarTrack = track
                _avatarOn.value = true
                VtuberFx.enabled = true
                // Show my own avatar in the grid immediately (the poll also picks it up).
                if (id != null) {
                    val cur = HashMap(_videoTracks.value)
                    cur[id] = track
                    _videoTracks.value = cur
                }
            }
            // If the publish failed, make sure no stale/dead track is left rendering as
            // a black tile — clear my slot so it falls back to the avatar-off cover.
            if (ok.isFailure && id != null) {
                val cur = HashMap(_videoTracks.value); cur.remove(id); _videoTracks.value = cur
            }
        } else {
            if (!_avatarOn.value && avatarTrack == null) return
            val track = avatarTrack
            avatarTrack = null
            _avatarOn.value = false
            VtuberFx.enabled = false
            val id = lp.identity?.value
            if (id != null) {
                val cur = HashMap(_videoTracks.value)
                if (cur[id] === track) { cur.remove(id); _videoTracks.value = cur }
            }
            // Unpublish + dispose off the main thread: the video-pipeline teardown can
            // block, and this runs on the UI scope from the room sheet.
            if (track != null) {
                withContext(Dispatchers.Default) {
                    runCatching { lp.unpublishTrack(track) }
                    runCatching { track.stopCapture() }
                    runCatching { track.dispose() }
                }
            }
        }
    }

    /** Flips between the front and back cameras. */
    fun switchCamera() {
        val lp = room?.localParticipant ?: return
        runCatching {
            (lp.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack)?.switchCamera()
        }
    }

    /** Grabs the shared EGL context so a renderer view can display a room track. */
    fun initRenderer(view: io.livekit.android.renderer.TextureViewRenderer) {
        runCatching { room?.initVideoRenderer(view) }
    }

    fun disconnect() {
        runCatching { scope?.cancel() }
        scope = null
        // A voice disguise is per-session — the next room starts as your real voice.
        RoomVoiceFx.reset()
        // Tear down the avatar capturer/track OFF the main thread. Doing it inline here
        // (disconnect runs on the caller — leaving the room does it on the UI thread)
        // could block on the video pipeline and freeze the app on exit.
        val at = avatarTrack
        avatarTrack = null
        if (at != null) {
            Thread {
                runCatching { at.stopCapture() }
                runCatching { at.dispose() }
            }.start()
        }
        VtuberFx.reset()
        _avatarOn.value = false
        _audioLevels.value = emptyMap()
        _videoTracks.value = emptyMap()
        _cameraOn.value = false
        runCatching { room?.disconnect() }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio?.clearCommunicationDevice()
        }
        runCatching { audio?.mode = AudioManager.MODE_NORMAL }
        room = null
        audio = null
    }
}
