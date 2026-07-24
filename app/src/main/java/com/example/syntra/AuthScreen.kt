package com.example.syntra

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
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.SyntraClient
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import com.example.syntra.ui.theme.SyntraTheme
import kotlinx.coroutines.launch

/** Which pane of the auth flow is showing. */
private enum class AuthMode { WELCOME, LOGIN, REGISTER }

/** How the user identifies themselves when logging in. */
private enum class LoginMethod { EMAIL, PHONE }

private val ErrorRed = Color(0xFFFF6B6B)
private val OkGreen = Color(0xFF23C55E)

/**
 * Gate shown when nobody is signed in. Calls [onAuthenticated] once a session exists,
 * which lets [SyntraApp] swap in the real app.
 */
@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    var mode by remember { mutableStateOf(AuthMode.WELCOME) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1B1430), Color(0xFF141021), Color(0xFF0E0E14)),
                ),
            ),
    ) {
        when (mode) {
            AuthMode.WELCOME -> WelcomePane(
                onLogin = { mode = AuthMode.LOGIN },
                onRegister = { mode = AuthMode.REGISTER },
            )
            AuthMode.LOGIN -> CredentialsPane(
                register = false,
                onBack = { mode = AuthMode.WELCOME },
                onSwap = { mode = AuthMode.REGISTER },
                onAuthenticated = onAuthenticated,
            )
            AuthMode.REGISTER -> CredentialsPane(
                register = true,
                onBack = { mode = AuthMode.WELCOME },
                onSwap = { mode = AuthMode.LOGIN },
                onAuthenticated = onAuthenticated,
            )
        }
    }
}

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
        Spacer(Modifier.height(24.dp))
        LogoMark(size = 104.dp)
        Spacer(Modifier.height(28.dp))
        Text(
            text = "Syntra",
            style = TextStyle(brush = Brush.horizontalGradient(listOf(Color(0xFFB79CFF), Color(0xFF6E8BFF)))),
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Chat, status, voice room, dan panggilan\ndalam satu aplikasi.",
            color = NexusTextSecondary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureChip("Terenkripsi")
            FeatureChip("Realtime")
            FeatureChip("Gratis")
        }
        Spacer(Modifier.height(40.dp))
        PrimaryButton("Masuk", onClick = onLogin)
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Daftar akun baru", onClick = onRegister)
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Dengan melanjutkan kamu menyetujui Ketentuan Layanan dan Kebijakan Privasi.",
            color = NexusTextSecondary.copy(alpha = 0.7f),
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
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

// ---------------------------------------------------------------------------
// Login / Register form
// ---------------------------------------------------------------------------

@Composable
private fun CredentialsPane(
    register: Boolean,
    onBack: () -> Unit,
    onSwap: () -> Unit,
    onAuthenticated: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loginMethod by remember { mutableStateOf(LoginMethod.EMAIL) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Phone auth isn't wired on the backend yet, so that path stays inert.
    val emailFlow = register || loginMethod == LoginMethod.EMAIL

    fun validate(): String? {
        if (register && fullName.isBlank()) return "Isi nama lengkap kamu."
        if (register) {
            val u = username.trim()
            if (u.isBlank()) return "Isi nama pengguna."
            if (u.length < 3) return "Nama pengguna minimal 3 karakter."
            if (u.any { it.isWhitespace() }) return "Nama pengguna tidak boleh mengandung spasi."
        }
        if (email.isBlank()) return "Isi alamat email."
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            return "Format email tidak valid."
        }
        if (password.isBlank()) return "Isi kata sandi."
        if (password.length < 6) return "Kata sandi minimal 6 karakter."
        if (register && confirm != password) return "Konfirmasi kata sandi tidak cocok."
        return null
    }

    fun submit() {
        if (!emailFlow) {
            error = "Login dengan nomor HP segera hadir. Untuk sekarang, gunakan email."
            return
        }
        error = validate()
        if (error != null) return
        busy = true
        scope.launch {
            val result = runCatching {
                if (!ApiConfig.ENABLED) {
                    // No backend configured: accept the input and continue offline.
                    SessionStore.markSignedIn(context, email.trim())
                } else {
                    if (register) {
                        // Backend takes display_name straight in register (api.md §1),
                        // so no follow-up PATCH is needed. Phone is collected but not
                        // sent — the server has no field for it yet.
                        SyntraClient.register(email.trim(), password, username.trim(), fullName.trim())
                    } else {
                        SyntraClient.loginWith(email.trim(), password)
                    }
                    SessionStore.markSignedIn(context, email.trim(), SyntraClient.currentRefreshToken)
                }
            }
            busy = false
            result
                .onSuccess { onAuthenticated() }
                .onFailure { error = it.message ?: "Gagal, coba lagi." }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 28.dp, vertical = 40.dp),
    ) {
        // Back + compact logo header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
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
            LogoMark(size = 40.dp, corner = 12.dp)
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = if (register) "Buat akun baru" else "Masuk ke Syntra",
            color = NexusTextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (register) "Lengkapi data di bawah untuk mulai memakai Syntra."
            else "Selamat datang kembali, senang melihatmu lagi.",
            color = NexusTextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(24.dp))

        // Login can be by email or phone; register always uses email.
        if (!register) {
            LoginMethodToggle(
                selected = loginMethod,
                onSelect = { loginMethod = it; error = null },
            )
            Spacer(Modifier.height(22.dp))
        }

        if (register) {
            AuthField(
                label = "Nama lengkap",
                value = fullName,
                onValueChange = { fullName = it; error = null },
                placeholder = "mis. Budi Santoso",
                icon = Icons.Filled.Badge,
                keyboardType = KeyboardType.Text,
            )
            Spacer(Modifier.height(16.dp))
            AuthField(
                label = "Nama pengguna",
                value = username,
                onValueChange = { username = it.filterNot { c -> c.isWhitespace() }; error = null },
                placeholder = "mis. budi",
                icon = Icons.Filled.AlternateEmail,
                keyboardType = KeyboardType.Text,
                helper = "Unik, tanpa spasi. Dipakai orang lain untuk menemukanmu.",
            )
            Spacer(Modifier.height(16.dp))
        }

        if (emailFlow) {
            AuthField(
                label = "Email",
                value = email,
                onValueChange = { email = it; error = null },
                placeholder = "nama@email.com",
                icon = Icons.Filled.Mail,
                keyboardType = KeyboardType.Email,
            )
        } else {
            // Phone login: the field is shown for readiness but stays inert.
            AuthField(
                label = "Nomor HP",
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' } },
                placeholder = "mis. 0812xxxxxxx",
                icon = Icons.Filled.Phone,
                keyboardType = KeyboardType.Phone,
            )
            Spacer(Modifier.height(12.dp))
            InfoBanner("Login dengan nomor HP segera hadir. Untuk sekarang, silakan masuk memakai email.")
        }

        if (register) {
            Spacer(Modifier.height(16.dp))
            AuthField(
                label = "Nomor HP (opsional)",
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' }; error = null },
                placeholder = "mis. 0812xxxxxxx",
                icon = Icons.Filled.Phone,
                keyboardType = KeyboardType.Phone,
                helper = "Segera hadir — belum disimpan untuk saat ini.",
            )
        }

        if (emailFlow) {
            Spacer(Modifier.height(16.dp))
            AuthField(
                label = "Kata sandi",
                value = password,
                onValueChange = { password = it; error = null },
                placeholder = "Minimal 6 karakter",
                icon = Icons.Filled.Lock,
                keyboardType = KeyboardType.Password,
                isPassword = true,
                imeAction = if (register) ImeAction.Next else ImeAction.Done,
                onImeAction = { if (!register) submit() },
            )
        }
        if (register) {
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
                onImeAction = { submit() },
                trailingOk = confirm.isNotEmpty() && confirm == password,
            )
        }

        error?.let {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ErrorRed.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(it, color = ErrorRed, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }

        Spacer(Modifier.height(28.dp))
        PrimaryButton(
            text = if (register) "Daftar" else "Masuk",
            busy = busy,
            enabled = emailFlow,
            onClick = { submit() },
        )

        if (register) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Dengan mendaftar kamu menyetujui Ketentuan Layanan dan Kebijakan Privasi Syntra.",
                color = NexusTextSecondary.copy(alpha = 0.7f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (register) "Sudah punya akun?" else "Belum punya akun?",
                color = NexusTextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (register) "Masuk" else "Daftar",
                color = NexusAccentSoft,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onSwap,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Splash (shown while the session is being restored)
// ---------------------------------------------------------------------------

/** Branded loading screen so app start never shows a blank dark rectangle. */
@Composable
fun AuthSplash() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1B1430), Color(0xFF141021), Color(0xFF0E0E14)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LogoMark(size = 96.dp)
            Spacer(Modifier.height(22.dp))
            Text(
                text = "Syntra",
                style = TextStyle(brush = Brush.horizontalGradient(listOf(Color(0xFFB79CFF), Color(0xFF6E8BFF)))),
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(
                color = NexusAccentSoft,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text("Menyiapkan Syntra…", color = NexusTextSecondary, fontSize = 13.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

/** The Syntra brand tile, used at various sizes across the auth flow. */
@Composable
private fun LogoMark(size: androidx.compose.ui.unit.Dp, corner: androidx.compose.ui.unit.Dp = 28.dp) {
    Image(
        painter = painterResource(R.drawable.ic_syntra_logo),
        contentDescription = "Logo Syntra",
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner)),
    )
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
    Column {
        Text(
            text = label,
            color = NexusTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                .border(1.dp, NexusStroke, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = NexusTextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(placeholder, color = NexusTextSecondary.copy(alpha = 0.7f), fontSize = 15.sp)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(color = NexusTextPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(NexusAccentSoft),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    keyboardActions = KeyboardActions(
                        onDone = { onImeAction() },
                        onGo = { onImeAction() },
                    ),
                    visualTransformation = if (isPassword && !revealed) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    modifier = Modifier.fillMaxWidth(),
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
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = OkGreen,
                    modifier = Modifier.size(20.dp),
                )
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
private fun PrimaryButton(
    text: String,
    busy: Boolean = false,
    enabled: Boolean = true,
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
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
            )
        } else {
            Text(
                text,
                color = if (enabled) Color.White else NexusTextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Email / phone segmented switch on the login pane. */
@Composable
private fun LoginMethodToggle(selected: LoginMethod, onSelect: (LoginMethod) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .border(1.dp, NexusStroke, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ToggleSegment("Email", selected == LoginMethod.EMAIL, Modifier.weight(1f)) {
            onSelect(LoginMethod.EMAIL)
        }
        ToggleSegment("Nomor HP", selected == LoginMethod.PHONE, Modifier.weight(1f)) {
            onSelect(LoginMethod.PHONE)
        }
    }
}

@Composable
private fun ToggleSegment(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .then(
                if (selected) {
                    Modifier.background(
                        Brush.horizontalGradient(listOf(NexusAccentSoft, NexusAccent)),
                        RoundedCornerShape(12.dp),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Color.White else NexusTextSecondary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

/** Small accent-tinted note used for "coming soon" and similar hints. */
@Composable
private fun InfoBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NexusAccent.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .border(1.dp, NexusAccent.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Info, null, tint = NexusAccentSoft, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = NexusTextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
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

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 360, heightDp = 800)
@Composable
private fun AuthPreview() {
    SyntraTheme { AuthScreen(onAuthenticated = {}) }
}
