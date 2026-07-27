package com.example.syntra

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.SyntraClient
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusRing
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import com.example.syntra.ui.theme.SyntraTheme
import kotlinx.coroutines.launch

/** Which pane of the auth flow is showing. */
private enum class AuthMode { WELCOME, LOGIN, REGISTER }

private val ErrorRed = Color(0xFFFF6B6B)
private val OkGreen = Color(0xFF23C55E)

// Shared palette for the auth surface — kept here so every pane matches.
private val AuthTopColor = Color(0xFF17132A)
private val AuthMidColor = Color(0xFF121019)
private val AuthBottomColor = Color(0xFF0B0B11)
private val FieldFill = Color(0xFF15151E)

/**
 * Gate shown when nobody is signed in. Calls [onAuthenticated] once a session
 * exists, which lets [SyntraApp] swap in the real app.
 */
@Composable
fun AuthScreen(onAuthenticated: () -> Unit, notice: String? = null) {
    // A deletion/expiry notice drops the user straight onto the login pane so the
    // reason is visible immediately, rather than the welcome splash.
    var mode by remember { mutableStateOf(if (notice != null) AuthMode.LOGIN else AuthMode.WELCOME) }

    Box(modifier = Modifier.fillMaxSize().authBackground()) {
        Crossfade(targetState = mode, animationSpec = tween(280), label = "auth-mode") { m ->
            when (m) {
                AuthMode.WELCOME -> WelcomePane(
                    onLogin = { mode = AuthMode.LOGIN },
                    onRegister = { mode = AuthMode.REGISTER },
                )
                AuthMode.LOGIN -> LoginPane(
                    onBack = { mode = AuthMode.WELCOME },
                    onSwap = { mode = AuthMode.REGISTER },
                    onAuthenticated = onAuthenticated,
                    notice = if (mode == AuthMode.LOGIN) notice else null,
                )
                AuthMode.REGISTER -> RegisterWizard(
                    onBack = { mode = AuthMode.WELCOME },
                    onSwap = { mode = AuthMode.LOGIN },
                    onAuthenticated = onAuthenticated,
                )
            }
        }
    }
}

/** The dark gradient + soft accent glow behind every auth pane. */
private fun Modifier.authBackground(): Modifier = this
    .background(Brush.verticalGradient(listOf(AuthTopColor, AuthMidColor, AuthBottomColor)))

// ---------------------------------------------------------------------------
// Welcome
// ---------------------------------------------------------------------------

@Composable
private fun WelcomePane(onLogin: () -> Unit, onRegister: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(20.dp))
        GlowingLogo(size = 108.dp)
        Spacer(Modifier.height(30.dp))
        Text(
            text = "Syntra",
            style = TextStyle(brush = Brush.horizontalGradient(listOf(Color(0xFF7FB4FF), Color(0xFF6E8BFF)))),
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Chat, status, voice room, dan panggilan —\nsemua dalam satu aplikasi realtime.",
            color = NexusTextSecondary,
            fontSize = 15.sp,
            lineHeight = 23.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureChip("Realtime")
            FeatureChip("Aman")
            FeatureChip("Gratis")
        }
        Spacer(Modifier.height(44.dp))
        PrimaryButton("Masuk", onClick = onLogin)
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Buat akun baru", onClick = onRegister)
        Spacer(Modifier.height(26.dp))
        Text(
            text = "Dengan melanjutkan, kamu menyetujui Ketentuan Layanan\ndan Kebijakan Privasi Syntra.",
            color = NexusTextSecondary.copy(alpha = 0.65f),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FeatureChip(text: String) {
    Text(
        text = text,
        color = NexusTextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.05f), CircleShape)
            .border(1.dp, NexusStroke, CircleShape)
            .padding(horizontal = 15.dp, vertical = 7.dp),
    )
}

// ---------------------------------------------------------------------------
// Login
// ---------------------------------------------------------------------------

@Composable
private fun LoginPane(
    onBack: () -> Unit,
    onSwap: () -> Unit,
    onAuthenticated: () -> Unit,
    notice: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        focus.clearFocus()
        error = when {
            email.isBlank() -> "Isi alamat email."
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Format email tidak valid."
            password.isBlank() -> "Isi kata sandi."
            else -> null
        }
        if (error != null) return
        busy = true
        scope.launch {
            val result = runCatching {
                if (!ApiConfig.ENABLED) {
                    SessionStore.markSignedIn(context, email.trim())
                } else {
                    SyntraClient.loginWith(email.trim(), password)
                    SessionStore.markSignedIn(context, email.trim(), SyntraClient.currentRefreshToken)
                }
            }
            busy = false
            result.onSuccess { onAuthenticated() }
                .onFailure { error = it.message ?: "Gagal masuk, coba lagi." }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 28.dp, vertical = 36.dp),
    ) {
        AuthHeader(onBack = onBack)

        Spacer(Modifier.height(30.dp))
        Text("Selamat datang kembali", color = NexusTextPrimary, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Masuk untuk melanjutkan percakapanmu di Syntra.",
            color = NexusTextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

        if (notice != null) {
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NexusAccent.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                    .border(1.dp, NexusAccent.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(notice, color = NexusTextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }

        Spacer(Modifier.height(30.dp))
        AuthField(
            label = "Email",
            value = email,
            onValueChange = { email = it; error = null },
            placeholder = "nama@email.com",
            icon = Icons.Filled.Mail,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(16.dp))
        AuthField(
            label = "Kata sandi",
            value = password,
            onValueChange = { password = it; error = null },
            placeholder = "Masukkan kata sandi",
            icon = Icons.Filled.Lock,
            keyboardType = KeyboardType.Password,
            isPassword = true,
            imeAction = ImeAction.Done,
            onImeAction = { submit() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Lupa kata sandi?",
            color = NexusAccentSoft,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )

        ErrorBanner(error)

        Spacer(Modifier.height(26.dp))
        PrimaryButton(text = "Masuk", busy = busy, onClick = { submit() })

        Spacer(Modifier.height(22.dp))
        SwapRow(prompt = "Belum punya akun?", action = "Daftar", onClick = onSwap)
    }
}

// ---------------------------------------------------------------------------
// Register — step-by-step wizard
// ---------------------------------------------------------------------------

private enum class RegStep { IDENTITY, EMAIL, SECURITY }

@Composable
private fun RegisterWizard(onBack: () -> Unit, onSwap: () -> Unit, onAuthenticated: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var step by remember { mutableStateOf(RegStep.IDENTITY) }
    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun validate(target: RegStep): String? = when (target) {
        RegStep.IDENTITY -> {
            val u = username.trim()
            when {
                fullName.isBlank() -> "Isi nama lengkap kamu."
                u.isBlank() -> "Isi nama pengguna."
                u.length < 3 -> "Nama pengguna minimal 3 karakter."
                !u.first().isLetter() -> "Nama pengguna harus diawali huruf."
                else -> null
            }
        }
        RegStep.EMAIL -> when {
            email.isBlank() -> "Isi alamat email."
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Format email tidak valid."
            else -> null
        }
        RegStep.SECURITY -> when {
            password.isBlank() -> "Isi kata sandi."
            password.length < 6 -> "Kata sandi minimal 6 karakter."
            confirm != password -> "Konfirmasi kata sandi tidak cocok."
            else -> null
        }
    }

    fun goBack() {
        error = null
        focus.clearFocus()
        when (step) {
            RegStep.IDENTITY -> onBack()
            RegStep.EMAIL -> step = RegStep.IDENTITY
            RegStep.SECURITY -> step = RegStep.EMAIL
        }
    }

    fun submit() {
        focus.clearFocus()
        error = validate(RegStep.SECURITY)
        if (error != null) return
        busy = true
        scope.launch {
            val result = runCatching {
                if (!ApiConfig.ENABLED) {
                    SessionStore.markSignedIn(context, email.trim())
                } else {
                    SyntraClient.register(email.trim(), password, username.trim(), fullName.trim())
                    // If email confirmation is required, register returns no session.
                    // Try logging straight in — this succeeds when confirmation is
                    // disabled or the account auto-confirms.
                    if (!SyntraClient.hasSession) {
                        runCatching { SyntraClient.loginWith(email.trim(), password) }
                    }
                    if (SyntraClient.hasSession) {
                        SessionStore.markSignedIn(context, email.trim(), SyntraClient.currentRefreshToken)
                    } else {
                        throw IllegalStateException(
                            "Akun berhasil dibuat. Konfirmasi lewat email yang kami kirim, lalu masuk.",
                        )
                    }
                }
            }
            busy = false
            result.onSuccess { onAuthenticated() }
                .onFailure { error = it.message ?: "Pendaftaran gagal, coba lagi." }
        }
    }

    fun advance() {
        error = validate(step)
        if (error != null) return
        focus.clearFocus()
        when (step) {
            RegStep.IDENTITY -> step = RegStep.EMAIL
            RegStep.EMAIL -> step = RegStep.SECURITY
            RegStep.SECURITY -> submit()
        }
    }

    val subtitle = when (step) {
        RegStep.IDENTITY -> "Kenalkan dirimu. Nama pengguna dipakai orang lain untuk menemukanmu."
        RegStep.EMAIL -> "Email dipakai untuk masuk dan mengamankan akunmu."
        RegStep.SECURITY -> "Buat kata sandi yang kuat untuk mengunci akunmu."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 28.dp, vertical = 36.dp),
    ) {
        AuthHeader(onBack = { goBack() })

        Spacer(Modifier.height(26.dp))
        StepIndicator(current = step.ordinal, total = RegStep.entries.size)

        Spacer(Modifier.height(24.dp))
        Text("Buat akun Syntra", color = NexusTextPrimary, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Langkah ${step.ordinal + 1} dari ${RegStep.entries.size}", color = NexusAccentSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = NexusTextSecondary, fontSize = 14.sp, lineHeight = 20.sp)

        Spacer(Modifier.height(26.dp))
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(320)) { w -> dir * w } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(320)) { w -> -dir * w } + fadeOut(tween(220)))
            },
            label = "reg-step",
        ) { current ->
            Column {
                when (current) {
                    RegStep.IDENTITY -> {
                        AuthField(
                            label = "Nama lengkap",
                            value = fullName,
                            onValueChange = { fullName = it; error = null },
                            placeholder = "mis. Budi Santoso",
                            icon = Icons.Filled.Badge,
                            imeAction = ImeAction.Next,
                        )
                        Spacer(Modifier.height(16.dp))
                        AuthField(
                            label = "Nama pengguna",
                            value = username,
                            onValueChange = { v -> username = v.filterNot { it.isWhitespace() }.lowercase(); error = null },
                            placeholder = "mis. budi",
                            icon = Icons.Filled.AlternateEmail,
                            helper = "Huruf kecil, angka, titik, garis bawah. Diawali huruf.",
                            imeAction = ImeAction.Done,
                            onImeAction = { advance() },
                        )
                    }
                    RegStep.EMAIL -> {
                        AuthField(
                            label = "Email",
                            value = email,
                            onValueChange = { email = it; error = null },
                            placeholder = "nama@email.com",
                            icon = Icons.Filled.Mail,
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done,
                            onImeAction = { advance() },
                        )
                    }
                    RegStep.SECURITY -> {
                        AuthField(
                            label = "Kata sandi",
                            value = password,
                            onValueChange = { password = it; error = null },
                            placeholder = "Minimal 6 karakter",
                            icon = Icons.Filled.Lock,
                            keyboardType = KeyboardType.Password,
                            isPassword = true,
                            imeAction = ImeAction.Next,
                        )
                        Spacer(Modifier.height(16.dp))
                        AuthField(
                            label = "Konfirmasi kata sandi",
                            value = confirm,
                            onValueChange = { confirm = it; error = null },
                            placeholder = "Ulangi kata sandi",
                            icon = Icons.Filled.CheckCircle,
                            keyboardType = KeyboardType.Password,
                            isPassword = true,
                            imeAction = ImeAction.Done,
                            onImeAction = { advance() },
                            trailingOk = confirm.isNotEmpty() && confirm == password,
                        )
                    }
                }
            }
        }

        ErrorBanner(error)

        Spacer(Modifier.height(26.dp))
        PrimaryButton(
            text = if (step == RegStep.SECURITY) "Daftar" else "Lanjut",
            busy = busy,
            trailingArrow = step != RegStep.SECURITY,
            onClick = { advance() },
        )

        Spacer(Modifier.height(22.dp))
        SwapRow(prompt = "Sudah punya akun?", action = "Masuk", onClick = onSwap)
    }
}

/** Segmented progress bar for the register wizard. */
@Composable
private fun StepIndicator(current: Int, total: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { i ->
            val active = i <= current
            val color by animateColorAsState(
                targetValue = if (active) NexusAccentSoft else NexusStroke,
                animationSpec = tween(300),
                label = "seg-$i",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Splash (shown while the session is being restored)
// ---------------------------------------------------------------------------

/**
 * Minimal startup screen: four brand tiles start scattered and snap together
 * one by one — a quiet "assemble" — then the wordmark fades in. No spinner, no
 * copy; just the mark forming. Simple, modern, professional.
 */
@Composable
fun AuthSplash() {
    // 2×2 tiles. Each starts offset + faded, then eases into place in sequence.
    val tileColors = listOf(
        Color(0xFF7FB4FF), Color(0xFF5C9BFF),
        Color(0xFF6E8BFF), Color(0xFF3B68F5),
    )
    // Final positions relative to centre (in dp), and the scattered start offsets.
    val finals = listOf(
        (-18).dp to (-18).dp, 18.dp to (-18).dp,
        (-18).dp to 18.dp, 18.dp to 18.dp,
    )
    val starts = listOf(
        (-160).dp to (-120).dp, 150.dp to (-140).dp,
        (-140).dp to 150.dp, 160.dp to 130.dp,
    )

    val progress = remember { List(4) { androidx.compose.animation.core.Animatable(0f) } }
    var showWord by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        progress.forEachIndexed { i, anim ->
            launch {
                kotlinx.coroutines.delay(i * 130L)
                anim.animateTo(1f, tween(480, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            }
        }
        kotlinx.coroutines.delay(4 * 130L + 400L)
        showWord = true
    }

    // Once assembled, a gentle breathing pulse keeps the mark alive for however
    // long the session restore actually takes — so the splash stays useful during
    // loading instead of freezing.
    val pulseTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "splash-pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        modifier = Modifier.fillMaxSize().authBackground(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                progress.forEachIndexed { i, anim ->
                    val t = anim.value
                    val (fx, fy) = finals[i]
                    val (sx, sy) = starts[i]
                    val x = androidx.compose.ui.unit.lerp(sx, fx, t)
                    val y = androidx.compose.ui.unit.lerp(sy, fy, t)
                    // Breathing pulse applies only once assembled (t == 1).
                    val p = if (t >= 1f) pulse else (0.6f + 0.4f * t)
                    Box(
                        modifier = Modifier
                            .offset(x = x, y = y)
                            .size(34.dp)
                            .graphicsLayer {
                                alpha = t
                                rotationZ = (1f - t) * 45f
                                scaleX = p
                                scaleY = p
                            }
                            .clip(RoundedCornerShape(10.dp))
                            .background(tileColors[i]),
                    )
                }
            }
            Spacer(Modifier.height(30.dp))
            androidx.compose.animation.AnimatedVisibility(
                visible = showWord,
                enter = fadeIn(tween(360)),
            ) {
                Text(
                    text = "Syntra",
                    style = TextStyle(brush = Brush.horizontalGradient(listOf(Color(0xFF7FB4FF), Color(0xFF6E8BFF)))),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

/** Back button + compact brand mark, shared by login & register. */
@Composable
private fun AuthHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(Color.White.copy(alpha = 0.05f), CircleShape)
                .border(1.dp, NexusStroke, CircleShape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onBack,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali",
                tint = NexusTextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        LogoMark(size = 42.dp, corner = 13.dp)
    }
}

/** The Syntra brand tile. */
@Composable
private fun LogoMark(size: Dp, corner: Dp = 28.dp) {
    Image(
        painter = painterResource(R.drawable.ic_syntra_logo),
        contentDescription = "Logo Syntra",
        modifier = Modifier.size(size).clip(RoundedCornerShape(corner)),
    )
}

/** Logo with a soft accent halo behind it, for hero moments. */
@Composable
private fun GlowingLogo(size: Dp) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size + 56.dp)
                .background(Brush.radialGradient(listOf(NexusRing.copy(alpha = 0.28f), Color.Transparent)), CircleShape),
        )
        LogoMark(size = size, corner = size / 3.6f)
    }
}

@Composable
private fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    helper: String? = null,
    trailingOk: Boolean = false,
) {
    var revealed by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) NexusAccentSoft else NexusStroke,
        animationSpec = tween(180),
        label = "field-border",
    )
    Column {
        Text(
            text = label,
            color = if (focused) NexusAccentSoft else NexusTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FieldFill, RoundedCornerShape(16.dp))
                .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = if (focused) NexusAccentSoft else NexusTextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(placeholder, color = NexusTextSecondary.copy(alpha = 0.6f), fontSize = 15.sp)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(color = NexusTextPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(NexusAccentSoft),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    keyboardActions = KeyboardActions(onDone = { onImeAction() }, onGo = { onImeAction() }),
                    visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
                )
            }
            if (isPassword) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (revealed) "Sembunyikan kata sandi" else "Tampilkan kata sandi",
                    tint = NexusTextSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { revealed = !revealed },
                        ),
                )
            } else if (trailingOk) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.CheckCircle, null, tint = OkGreen, modifier = Modifier.size(20.dp))
            }
        }
        if (helper != null) {
            Text(
                text = helper,
                color = NexusTextSecondary.copy(alpha = 0.7f),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun ErrorBanner(error: String?) {
    if (error == null) return
    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ErrorRed.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .border(1.dp, ErrorRed.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(error, color = ErrorRed, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    busy: Boolean = false,
    enabled: Boolean = true,
    trailingArrow: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(
                if (enabled) Brush.horizontalGradient(listOf(NexusAccentSoft, NexusAccent))
                else SolidColor(Color.White.copy(alpha = 0.08f)),
                RoundedCornerShape(27.dp),
            )
            .clickable(
                enabled = enabled && !busy,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text,
                    color = if (enabled) Color.White else NexusTextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (trailingArrow) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(27.dp))
            .border(1.dp, NexusStroke, RoundedCornerShape(27.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = NexusTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

/** "Sudah punya akun? Masuk" style footer row. */
@Composable
private fun SwapRow(prompt: String, action: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(prompt, color = NexusTextSecondary, fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = action,
            color = NexusAccentSoft,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 360, heightDp = 800)
@Composable
private fun AuthPreview() {
    SyntraTheme { AuthScreen(onAuthenticated = {}) }
}
