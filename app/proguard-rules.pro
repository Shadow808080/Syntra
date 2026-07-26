# ---------------------------------------------------------------------------
# Syntra R8 / ProGuard rules
#
# The app serializes NOTHING by reflection — every payload is built and parsed by
# hand with org.json (see SyntraClient / MusicClient). So the data/model classes
# are safe to shrink & obfuscate; no -keep is needed for them. These rules only
# cover the third-party libraries that use JNI / native / reflection, plus a few
# -dontwarn for optional transitive deps R8 would otherwise flag.
#
# NOTE: this build enables R8 for release. Smoke-test a real release build on a
# device (calls, video, music, images, QR) before shipping — that's the only way
# to be certain no keep-rule is missing.
# ---------------------------------------------------------------------------

# Keep line numbers for readable crash reports; hide the original file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# Compose/coroutines rely on generic signatures & annotations at runtime.
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault

# --- LiveKit + WebRTC (JNI / native handles, protobuf signalling) ----------
# WebRTC crosses the JNI boundary by class/method name; obfuscating it crashes
# audio/video calls and voice rooms. Keep it and LiveKit whole — correctness over
# a few hundred KB here.
-keep class org.webrtc.** { *; }
-keep class io.livekit.** { *; }
-keep class livekit.** { *; }
-keep class com.twilio.** { *; }
-dontwarn org.webrtc.**
-dontwarn io.livekit.**

# --- OkHttp / Okio (ship their own consumer rules; silence optional deps) ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Coil (has consumer rules; guard its optional decoders) ----------------
-dontwarn coil.**

# --- ZXing (QR generation) --------------------------------------------------
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# --- Kotlin coroutines / metadata (R8-safe defaults, kept explicit) --------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**
-keep class kotlin.Metadata { *; }

# --- Enums: keep valueOf/values so any name-based lookup keeps working ------
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
