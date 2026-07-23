package com.example.syntra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.ApiException
import com.example.syntra.net.SyntraClient
import kotlinx.coroutines.delay
import com.example.syntra.ui.theme.AppTheme
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.SyntraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Paint the saved theme before the first frame so there is no flash.
        AppTheme.load(this)
        enableEdgeToEdge()
        setContent {
            SyntraTheme {
                NexusApp()
            }
        }
    }

    override fun onDestroy() {
        // Close the realtime socket with the app; ChatScreen reopens it on start.
        if (ApiConfig.ENABLED) SyntraClient.disconnect()
        super.onDestroy()
    }
}

/**
 * Auth gate: nobody gets to the app without a session. [SessionStore] persists the
 * flag, so a signed-in user goes straight to the chat list on the next launch.
 */
@Composable
private fun NexusApp() {
    val context = LocalContext.current
    // null = still deciding, so we don't flash the login screen at a signed-in user.
    var signedIn by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        val remembered = SessionStore.isSignedIn(context)
        signedIn = when {
            !remembered -> false
            !ApiConfig.ENABLED -> true
            // The access token is gone after a restart; trade the refresh token
            // for a new one, otherwise every request would come back 401.
            else -> {
                val token = SessionStore.refreshToken(context)
                if (token.isNullOrBlank()) {
                    SessionStore.signOut(context)
                    false
                } else {
                    var restored = false
                    var rejected = false
                    // A flaky first second of connectivity must not log anyone out, so
                    // only an explicit rejection from the server clears the session.
                    repeat(3) { attempt ->
                        if (restored || rejected) return@repeat
                        runCatching { SyntraClient.restoreSession(token) }
                            .onSuccess { restored = true }
                            .onFailure { e ->
                                if ((e as? ApiException)?.code == "unauthorized") rejected = true
                                else if (attempt < 2) delay(1200)
                            }
                    }
                    when {
                        restored -> SessionStore.markSignedIn(
                            context,
                            SessionStore.signedInEmail(context).orEmpty(),
                            SyntraClient.currentRefreshToken,
                        )
                        rejected -> SessionStore.signOut(context)
                        else -> Unit // offline: keep the session, try again next launch
                    }
                    restored
                }
            }
        }
    }

    when (signedIn) {
        null -> Box(modifier = Modifier.fillMaxSize().background(NexusBackground))
        false -> AuthScreen(onAuthenticated = { signedIn = true })
        true -> MainTabs(onSignOut = { signedIn = false })
    }
}

@Composable
private fun MainTabs(onSignOut: () -> Unit) {
    var tab by remember { mutableStateOf(NexusTab.CHAT) }
    when (tab) {
        NexusTab.SHORTS -> ShortsScreen(
            modifier = Modifier.fillMaxSize(),
            selectedTab = tab,
            onTabSelected = { tab = it },
        )
        NexusTab.CALLS -> CallsScreen(
            modifier = Modifier.fillMaxSize(),
            selectedTab = tab,
            onTabSelected = { tab = it },
        )
        NexusTab.ROOMS -> RoomsScreen(
            modifier = Modifier.fillMaxSize(),
            selectedTab = tab,
            onTabSelected = { tab = it },
        )
        else -> ChatScreen(
            modifier = Modifier.fillMaxSize(),
            selectedTab = tab,
            onTabSelected = { tab = it },
            onSignOut = onSignOut,
        )
    }
}
