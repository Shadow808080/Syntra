package com.example.syntra

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Logo mark
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(
                    Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFF3B68F5))),
                    RoundedCornerShape(28.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("S", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(26.dp))
        Text(
            text = "Syntra",
            style = TextStyle(brush = Brush.horizontalGradient(listOf(Color(0xFFB79CFF), Color(0xFF6E8BFF)))),
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Chat, status, dan voice room dalam satu tempat.",
            color = NexusTextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(44.dp))
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

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        error = null
        if (email.isBlank() || password.isBlank() || (register && username.isBlank())) {
            error = "Lengkapi semua kolom dulu."
            return
        }
        if (password.length < 6) {
            error = "Kata sandi minimal 6 karakter."
            return
        }
        busy = true
        scope.launch {
            val result = runCatching {
                if (!ApiConfig.ENABLED) {
                    // No backend configured: accept the input and continue offline.
                    SessionStore.markSignedIn(context, email)
                } else {
                    if (register) SyntraClient.register(email, password, username)
                    else SyntraClient.loginWith(email, password)
                    SessionStore.markSignedIn(context, email, SyntraClient.currentRefreshToken)
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
            .padding(horizontal = 28.dp, vertical = 60.dp),
    ) {
        Text(
            text = if (register) "Buat akun" else "Masuk",
            color = NexusTextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (register) "Isi data untuk mulai memakai Syntra."
            else "Selamat datang kembali.",
            color = NexusTextSecondary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(30.dp))

        if (register) {
            AuthField(
                value = username,
                onValueChange = { username = it },
                placeholder = "Nama pengguna",
                icon = Icons.Filled.Person,
            )
            Spacer(Modifier.height(12.dp))
        }
        AuthField(
            value = email,
            onValueChange = { email = it },
            placeholder = "Email",
            icon = Icons.Filled.Mail,
            keyboardType = KeyboardType.Email,
        )
        Spacer(Modifier.height(12.dp))
        AuthField(
            value = password,
            onValueChange = { password = it },
            placeholder = "Kata sandi",
            icon = Icons.Filled.Lock,
            keyboardType = KeyboardType.Password,
            isPassword = true,
        )

        error?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp)
        }

        Spacer(Modifier.height(26.dp))
        PrimaryButton(
            text = if (register) "Daftar" else "Masuk",
            busy = busy,
            onClick = { submit() },
        )
        Spacer(Modifier.height(16.dp))
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
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Kembali",
            color = NexusTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onBack,
                ),
        )
    }
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .border(1.dp, NexusStroke, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = NexusTextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, color = NexusTextSecondary, fontSize = 15.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = NexusTextPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(NexusAccentSoft),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Next,
                ),
                visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PrimaryButton(text: String, busy: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(
                Brush.horizontalGradient(listOf(NexusAccentSoft, NexusAccent)),
                RoundedCornerShape(26.dp),
            )
            .clickable(
                enabled = !busy,
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
            Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(26.dp))
            .border(1.dp, NexusStroke, RoundedCornerShape(26.dp))
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
