package com.example.syntra

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.syntra.net.AppLock
import com.example.syntra.net.AppLockStore
import com.example.syntra.ui.theme.DangerFill
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusSurfaceElevated
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import kotlin.math.roundToInt

private const val PIN_LENGTH = 6

// ---------------------------------------------------------------------------
// Biometric helper
// ---------------------------------------------------------------------------

/** Unwraps a Compose [Context] to the hosting [FragmentActivity] (needed by BiometricPrompt). */
fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

object BiometricAuth {
    /** True when the device has an enrolled fingerprint/face we can use. */
    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        activity: FragmentActivity,
        subtitle: String,
        onSuccess: () -> Unit,
        onCancel: () -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // User pressed "Gunakan PIN" or dismissed — fall back to PIN, quietly.
                    onCancel()
                }
                // onAuthenticationFailed (a non-matching finger) leaves the prompt up.
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Buka Syntra")
            .setSubtitle(subtitle)
            .setNegativeButtonText("Gunakan PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }
}

// ---------------------------------------------------------------------------
// Root lock gate
// ---------------------------------------------------------------------------

/**
 * Full-screen unlock gate shown at app start (and when returning from the
 * background) while the app lock is on. Clears [AppLock.unlocked] on success.
 */
@Composable
fun AppLockScreen(onUnlocked: () -> Unit) {
    // The lock is a dead end for the back button — don't let it fall through to the app.
    BackHandler {}

    val context = LocalContext.current
    val activity = remember { context.findFragmentActivity() }
    val biometricOn = remember { AppLockStore.biometricEnabled(context) && BiometricAuth.canAuthenticate(context) }

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    fun triggerBiometric() {
        val act = activity ?: return
        BiometricAuth.authenticate(
            activity = act,
            subtitle = "Verifikasi sidik jari untuk membuka",
            onSuccess = { AppLock.unlocked = true; onUnlocked() },
            onCancel = { /* stay on the PIN pad */ },
        )
    }

    // Offer the fingerprint prompt straight away when it's enabled.
    LaunchedEffect(Unit) { if (biometricOn) triggerBiometric() }

    // Verify as soon as the PIN is complete.
    LaunchedEffect(pin) {
        if (pin.length == PIN_LENGTH) {
            if (AppLockStore.verifyPin(context, pin)) {
                AppLock.unlocked = true
                onUnlocked()
            } else {
                error = true
                shake.snapTo(0f)
                // A quick left-right shudder to say "wrong".
                shake.animateTo(1f, androidx.compose.animation.core.tween(360))
                pin = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground)
            .windowInsetsPadding(WindowInsets.statusBars),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(NexusAccent.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Lock, null, tint = NexusAccentSoft, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Syntra terkunci", color = NexusTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (error) "PIN salah, coba lagi" else "Masukkan PIN untuk membuka",
            color = if (error) Color(0xFFFF6B6B) else NexusTextSecondary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(28.dp))

        val shakePx = (shake.value * 2 * Math.PI * 3).let { kotlin.math.sin(it) } * 12
        PinDots(
            filled = pin.length,
            modifier = Modifier.offset { IntOffset(shakePx.roundToInt(), 0) },
        )

        Spacer(Modifier.weight(1f))

        NumericKeypad(
            onDigit = { if (pin.length < PIN_LENGTH) { error = false; pin += it } },
            onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
            showBiometric = biometricOn,
            onBiometric = { triggerBiometric() },
        )
        Spacer(Modifier.height(28.dp))
    }
}

// ---------------------------------------------------------------------------
// Settings: enable / change / disable the lock
// ---------------------------------------------------------------------------

/** Manage the app lock from Settings: set/change the PIN and toggle fingerprint. */
@Composable
fun AppLockSettingsScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current

    var enabled by remember { mutableStateOf(AppLockStore.isEnabled(context)) }
    var bioEnabled by remember { mutableStateOf(AppLockStore.biometricEnabled(context)) }
    val bioAvailable = remember { BiometricAuth.canAuthenticate(context) }
    var autoLockSeconds by remember { mutableStateOf(AppLockStore.autoLockSeconds(context)) }
    var showAutoLock by remember { mutableStateOf(false) }
    // When non-null we're inside the set/change-PIN flow.
    var settingPin by remember { mutableStateOf(false) }
    // What we're re-authenticating FOR, or null when not verifying. Turning the lock
    // off — or removing the fingerprint from it — is exactly the action someone who
    // grabbed an unlocked phone would take, so it has to cost the same proof as
    // getting in does.
    var verifyFor by remember { mutableStateOf<LockAction?>(null) }

    if (settingPin) {
        PinSetupFlow(
            onCancel = { settingPin = false },
            onDone = { newPin ->
                AppLockStore.setPin(context, newPin)
                enabled = true
                settingPin = false
            },
        )
        return
    }

    verifyFor?.let { action ->
        VerifyLockFlow(
            action = action,
            onCancel = { verifyFor = null },
            onVerified = {
                when (action) {
                    LockAction.DISABLE -> {
                        AppLockStore.disable(context)
                        enabled = false
                        bioEnabled = false
                    }
                    LockAction.REMOVE_BIOMETRIC -> {
                        bioEnabled = false
                        AppLockStore.setBiometricEnabled(context, false)
                    }
                }
                verifyFor = null
            },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClose,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = NexusTextPrimary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text("Kunci aplikasi", color = NexusTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(72.dp)
                    .background(NexusAccent.copy(alpha = 0.14f), CircleShape)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Lock, null, tint = NexusAccentSoft, modifier = Modifier.size(32.dp))
            }
            Text(
                text = "Minta PIN atau sidik jari setiap kali Syntra dibuka. Kunci ini " +
                    "hanya tersimpan di perangkat ini.",
                color = NexusTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(12.dp))

            if (!enabled) {
                PrimaryButton("Aktifkan kunci aplikasi") { settingPin = true }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NexusSurface, RoundedCornerShape(18.dp))
                        .border(1.dp, NexusStroke, RoundedCornerShape(18.dp)),
                ) {
                    LockRow(
                        title = "Ubah PIN",
                        subtitle = "Ganti PIN 6 angka",
                        onClick = { settingPin = true },
                    )
                    RowDivider()
                    ToggleLockRow(
                        title = "Buka dengan sidik jari",
                        subtitle = when {
                            !bioAvailable -> "Tidak ada sidik jari terdaftar di perangkat"
                            else -> "Gunakan sidik jari sebagai ganti PIN"
                        },
                        checked = bioEnabled && bioAvailable,
                        enabled = bioAvailable,
                        onChange = { wanted ->
                            if (wanted) {
                                // Turning it ON only ever ADDS a way in, so it needs no
                                // proof beyond already being here.
                                bioEnabled = true
                                AppLockStore.setBiometricEnabled(context, true)
                            } else {
                                // Turning it off WEAKENS the lock — verify first.
                                verifyFor = LockAction.REMOVE_BIOMETRIC
                            }
                        },
                    )
                    RowDivider()
                    LockRow(
                        title = "Kunci otomatis",
                        subtitle = autoLockLabel(autoLockSeconds),
                        onClick = { showAutoLock = true },
                    )
                }
                Spacer(Modifier.height(20.dp))
                PrimaryButton("Matikan kunci aplikasi", destructive = true) {
                    verifyFor = LockAction.DISABLE
                }
            }
        }
    }

    if (showAutoLock) {
        AutoLockDialog(
            current = autoLockSeconds,
            onSelect = {
                autoLockSeconds = it
                AppLockStore.setAutoLockSeconds(context, it)
                showAutoLock = false
            },
            onDismiss = { showAutoLock = false },
        )
    }
}

/** Human label for the auto-lock delay shown in the settings row. */
private fun autoLockLabel(seconds: Int): String =
    if (seconds <= 0) "Segera setelah keluar" else "Setelah $seconds detik di latar belakang"

/** Picks how long the app may sit in the background before it re-locks. */
@Composable
private fun AutoLockDialog(current: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(0 to "Segera", 10 to "10 detik", 20 to "20 detik", 30 to "30 detik")
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusSurface, RoundedCornerShape(20.dp))
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = "Kunci otomatis",
                color = NexusTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
            )
            Text(
                text = "Minta PIN lagi setelah aplikasi ditinggalkan selama ini.",
                color = NexusTextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
            options.forEach { (seconds, label) ->
                val selected = seconds == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { onSelect(seconds) },
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        color = if (selected) NexusAccentSoft else NexusTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Dipilih",
                            tint = NexusAccentSoft,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** What a re-authentication is being demanded for. */
private enum class LockAction { DISABLE, REMOVE_BIOMETRIC }

/**
 * Proves it is still you before the lock is weakened.
 *
 * Offers the fingerprint first when it is enabled (that is what the user set up), and
 * always keeps the PIN pad underneath as the fallback — a finger can fail to read, and
 * being unable to turn your own lock off would be its own kind of lockout.
 *
 * This is the same challenge as the unlock gate, and deliberately so: without it,
 * anyone holding the phone while it was already open could simply switch the lock off
 * and keep it open forever.
 */
@Composable
private fun VerifyLockFlow(
    action: LockAction,
    onCancel: () -> Unit,
    onVerified: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    val context = LocalContext.current
    val activity = remember { context.findFragmentActivity() }
    val biometricOn = remember {
        AppLockStore.biometricEnabled(context) && BiometricAuth.canAuthenticate(context)
    }

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    fun triggerBiometric() {
        val act = activity ?: return
        BiometricAuth.authenticate(
            activity = act,
            subtitle = when (action) {
                LockAction.DISABLE -> "Verifikasi untuk mematikan kunci"
                LockAction.REMOVE_BIOMETRIC -> "Verifikasi untuk mematikan sidik jari"
            },
            onSuccess = onVerified,
            onCancel = { /* fall through to the PIN pad */ },
        )
    }

    LaunchedEffect(Unit) { if (biometricOn) triggerBiometric() }

    LaunchedEffect(pin) {
        if (pin.length == PIN_LENGTH) {
            if (AppLockStore.verifyPin(context, pin)) {
                onVerified()
            } else {
                error = true
                shake.snapTo(0f)
                shake.animateTo(1f, androidx.compose.animation.core.tween(360))
                pin = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground)
            .windowInsetsPadding(WindowInsets.statusBars),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onCancel,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "Batal",
                    tint = NexusTextPrimary, modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(NexusAccent.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (biometricOn) Icons.Filled.Fingerprint else Icons.Filled.Lock,
                null,
                tint = NexusAccentSoft,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = when (action) {
                LockAction.DISABLE -> "Matikan kunci aplikasi"
                LockAction.REMOVE_BIOMETRIC -> "Matikan sidik jari"
            },
            color = NexusTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                error -> "PIN salah, coba lagi"
                biometricOn -> "Verifikasi sidik jari, atau masukkan PIN"
                else -> "Masukkan PIN untuk melanjutkan"
            },
            color = if (error) Color(0xFFFF6B6B) else NexusTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(28.dp))

        val shakePx = (shake.value * 2 * Math.PI * 3).let { kotlin.math.sin(it) } * 12
        PinDots(
            filled = pin.length,
            modifier = Modifier.offset { IntOffset(shakePx.roundToInt(), 0) },
        )

        Spacer(Modifier.weight(1f))

        NumericKeypad(
            onDigit = { if (pin.length < PIN_LENGTH) { error = false; pin += it } },
            onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
            showBiometric = biometricOn,
            onBiometric = { triggerBiometric() },
        )
        Spacer(Modifier.height(28.dp))
    }
}

/** Two-step "enter a new PIN, then confirm it" flow used to set or change the PIN. */
@Composable
private fun PinSetupFlow(onCancel: () -> Unit, onDone: (String) -> Unit) {
    BackHandler(onBack = onCancel)
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(pin) {
        if (pin.length == PIN_LENGTH) {
            val stage1 = first
            if (stage1 == null) {
                first = pin
                pin = ""
            } else if (stage1 == pin) {
                onDone(pin)
            } else {
                error = true
                first = null
                pin = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground)
            .windowInsetsPadding(WindowInsets.statusBars),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onCancel,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Batal", tint = NexusTextPrimary, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = if (first == null) "Buat PIN" else "Konfirmasi PIN",
            color = NexusTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                error && first == null -> "PIN tidak cocok, ulangi"
                first == null -> "Masukkan 6 angka"
                else -> "Masukkan ulang PIN yang sama"
            },
            color = if (error && first == null) Color(0xFFFF6B6B) else NexusTextSecondary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(28.dp))
        PinDots(filled = pin.length)
        Spacer(Modifier.weight(1f))
        NumericKeypad(
            onDigit = { if (pin.length < PIN_LENGTH) { error = false; pin += it } },
            onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
            showBiometric = false,
            onBiometric = {},
        )
        Spacer(Modifier.height(28.dp))
    }
}

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

@Composable
private fun PinDots(filled: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(PIN_LENGTH) { i ->
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .background(
                        if (i < filled) NexusAccent else Color.Transparent,
                        CircleShape,
                    )
                    .border(
                        1.5.dp,
                        if (i < filled) NexusAccent else NexusStroke,
                        CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun NumericKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    showBiometric: Boolean,
    onBiometric: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                row.forEach { d -> KeypadKey(digit = d, onClick = { onDigit(d) }) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Bottom-left: fingerprint shortcut, or an empty slot to keep the grid aligned.
            if (showBiometric) {
                KeypadIcon(Icons.Filled.Fingerprint, "Sidik jari", onClick = onBiometric)
            } else {
                Spacer(Modifier.size(72.dp))
            }
            KeypadKey(digit = "0", onClick = { onDigit("0") })
            KeypadIcon(Icons.AutoMirrored.Filled.Backspace, "Hapus", onClick = onBackspace)
        }
    }
}

@Composable
private fun KeypadKey(digit: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(digit, color = NexusTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun KeypadIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = NexusTextSecondary, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun PrimaryButton(text: String, destructive: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (destructive) DangerFill else NexusAccent,
                RoundedCornerShape(16.dp),
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (destructive) Color(0xFFFF5D5D) else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LockRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = NexusTextPrimary, fontSize = 15.sp)
            Text(subtitle, color = NexusTextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ToggleLockRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onChange(!checked) }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (enabled) NexusTextPrimary else NexusTextSecondary, fontSize = 15.sp)
            Text(subtitle, color = NexusTextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = if (enabled) onChange else null,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NexusAccent,
                uncheckedThumbColor = NexusTextSecondary,
                uncheckedTrackColor = NexusSurfaceElevated,
                uncheckedBorderColor = NexusStroke,
            ),
        )
    }
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(NexusStroke.copy(alpha = 0.6f)),
    )
}
