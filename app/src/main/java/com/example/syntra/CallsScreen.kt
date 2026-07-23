package com.example.syntra

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.SyntraClient
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusOnline
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import com.example.syntra.ui.theme.SyntraTheme
import org.json.JSONArray
import org.json.JSONObject

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

enum class CallDirection { OUTGOING, INCOMING, MISSED }

data class CallEntry(
    val id: String,
    val peerName: String,
    val peerId: String,
    val video: Boolean,
    val direction: CallDirection,
    /** Epoch millis. */
    val at: Long,
    /** Seconds; 0 for a missed call. */
    val durationSec: Int,
)

/**
 * Call history, stored on the device.
 *
 * The backend has no call endpoints yet (`/api/v1/calls*` → "endpoint tidak
 * ditemukan"), so there is nothing to sync with. Keeping it local means the log
 * is real for this device instead of invented.
 */
object CallLog {
    private const val PREFS = "syntra_calls"
    private const val KEY = "entries"

    fun all(context: Context): List<CallEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CallEntry(
                    id = o.getString("id"),
                    peerName = o.optString("name"),
                    peerId = o.optString("peer_id"),
                    video = o.optBoolean("video"),
                    direction = CallDirection.valueOf(o.optString("dir", "OUTGOING")),
                    at = o.optLong("at"),
                    durationSec = o.optInt("dur"),
                )
            }.sortedByDescending { it.at }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, entry: CallEntry) {
        val current = all(context).take(199)
        val arr = JSONArray()
        (listOf(entry) + current).forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id).put("name", e.peerName).put("peer_id", e.peerId)
                    .put("video", e.video).put("dir", e.direction.name)
                    .put("at", e.at).put("dur", e.durationSec),
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun CallsScreen(
    modifier: Modifier = Modifier,
    selectedTab: NexusTab = NexusTab.CALLS,
    onTabSelected: (NexusTab) -> Unit = {},
    /** True while this tab is on screen; used to re-read the log live. */
    visible: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history = remember { mutableStateListOf<CallEntry>() }
    // People you could call, taken from the real conversation list.
    val contacts = remember { mutableStateListOf<Pair<String, String>>() }
    var filter by remember { mutableStateOf(0) } // 0 = semua, 1 = tak terjawab
    // Non-null while a full-screen call is up: (name, conversationId, peerId, video).
    var activeCall by remember { mutableStateOf<CallTarget?>(null) }

    // A call placed or received anywhere in the app appends to the log; re-read it
    // whenever this tab comes back so the history is always current.
    LaunchedEffect(visible) {
        if (visible) {
            val fresh = CallLog.all(context)
            if (fresh.map { it.id } != history.map { it.id }) {
                history.clear()
                history.addAll(fresh)
            }
        }
    }

    LaunchedEffect(Unit) {
        history.addAll(CallLog.all(context))
        if (ApiConfig.ENABLED) {
            runCatching { SyntraClient.getConversations() }
                .onSuccess { list ->
                    contacts.addAll(
                        list.filter { it.type == "direct" }
                            .mapNotNull { c -> c.counterpartId?.let { c.title to it } },
                    )
                }
        }
    }

    fun placeCall(name: String, peerId: String, video: Boolean) {
        if (!ApiConfig.ENABLED || peerId.isBlank()) {
            Toast.makeText(context, "Tidak bisa memulai panggilan.", Toast.LENGTH_SHORT).show()
            return
        }
        // Resolve (or create) the direct conversation, then open the call screen.
        scope.launch {
            val convId = runCatching { SyntraClient.createDirect(peerId) }.getOrNull()
            if (convId == null) {
                Toast.makeText(context, "Gagal memulai panggilan.", Toast.LENGTH_SHORT).show()
            } else {
                activeCall = CallTarget(name, convId, peerId, video)
            }
        }
    }

    val shown = if (filter == 1) history.filter { it.direction == CallDirection.MISSED } else history

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusBackground),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(SyntraHeaderPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SyntraTitle()
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.Search, "Cari",
                    tint = NexusTextPrimary, modifier = Modifier.size(22.dp),
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        Text(
                            "Panggilan",
                            color = NexusTextPrimary,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Riwayat panggilan suara dan video kamu.",
                            color = NexusAccentSoft,
                            fontSize = 14.sp,
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        listOf("Semua", "Tak terjawab").forEachIndexed { i, label ->
                            val active = filter == i
                            Box(
                                modifier = Modifier
                                    .background(
                                        brush = if (active) {
                                            Brush.horizontalGradient(listOf(NexusAccentSoft, NexusAccent))
                                        } else {
                                            Brush.horizontalGradient(listOf(NexusSurface, NexusSurface))
                                        },
                                        shape = RoundedCornerShape(50),
                                    )
                                    .border(
                                        width = if (active) 0.dp else 1.dp,
                                        color = if (active) Color.Transparent else NexusStroke,
                                        shape = RoundedCornerShape(50),
                                    )
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { filter = i }
                                    .padding(horizontal = 18.dp, vertical = 9.dp),
                            ) {
                                Text(
                                    text = label,
                                    color = if (active) Color.White else NexusTextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                }

                if (shown.isEmpty()) {
                    item { EmptyCalls(missedOnly = filter == 1) }
                } else {
                    item { SectionTitle("Terbaru") }
                    itemsIndexed(shown) { _, entry ->
                        CallRow(
                            entry = entry,
                            onCall = { placeCall(entry.peerName, entry.peerId, false) },
                            onVideo = { placeCall(entry.peerName, entry.peerId, true) },
                        )
                    }
                    item {
                        Text(
                            text = "Bersihkan riwayat",
                            color = Color(0xFFFF5D5D),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) {
                                    CallLog.clear(context)
                                    history.clear()
                                }
                                .padding(vertical = 18.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                if (contacts.isNotEmpty()) {
                    item { SectionTitle("Mulai panggilan") }
                    itemsIndexed(contacts) { _, (name, id) ->
                        ContactRow(
                            name = name,
                            onCall = { placeCall(name, id, false) },
                            onVideo = { placeCall(name, id, true) },
                        )
                    }
                }
            }
        }

        // Full-screen call overlay.
        activeCall?.let { target ->
            CallScreen(
                peerName = target.name,
                conversationId = target.conversationId,
                video = target.video,
                peerId = target.peerId,
                onClose = {
                    activeCall = null
                    // The call screen logged the attempt; refresh the list.
                    history.clear()
                    history.addAll(CallLog.all(context))
                },
            )
        }
    }
}

/** A call the user is placing from this screen. */
private data class CallTarget(
    val name: String,
    val conversationId: String,
    val peerId: String,
    val video: Boolean,
)

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = NexusTextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp),
    )
}

private val callGradients = listOf(
    listOf(Color(0xFF6C5CE7), Color(0xFF3B68F5)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    listOf(Color(0xFFEE5A6F), Color(0xFFF29263)),
    listOf(Color(0xFFDA22FF), Color(0xFF9733EE)),
)

private fun callGradient(key: String): List<Color> =
    callGradients[(key.hashCode() and Int.MAX_VALUE) % callGradients.size]

private fun relativeCallTime(at: Long): String {
    val minutes = (System.currentTimeMillis() - at) / 60_000
    return when {
        minutes < 1 -> "Baru saja"
        minutes < 60 -> "$minutes menit lalu"
        minutes < 1440 -> "${minutes / 60} jam lalu"
        minutes < 2880 -> "Kemarin"
        else -> "${minutes / 1440} hari lalu"
    }
}

private fun durationText(seconds: Int): String = when {
    seconds <= 0 -> ""
    seconds < 60 -> " · ${seconds}d"
    else -> " · ${seconds / 60}m ${seconds % 60}d"
}

@Composable
private fun CallRow(entry: CallEntry, onCall: () -> Unit, onVideo: () -> Unit) {
    val (icon, tint) = when (entry.direction) {
        CallDirection.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to NexusOnline
        CallDirection.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to NexusAccentSoft
        CallDirection.MISSED -> Icons.AutoMirrored.Filled.CallMissed to Color(0xFFFF5D5D)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientAvatar(
            gradient = callGradient(entry.peerId.ifBlank { entry.peerName }),
            initial = entry.peerName.firstOrNull()?.toString() ?: "?",
            size = 48.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.peerName.ifBlank { "Tanpa nama" },
                color = if (entry.direction == CallDirection.MISSED) Color(0xFFFF8A8A) else NexusTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    text = relativeCallTime(entry.at) + durationText(entry.durationSec),
                    color = NexusTextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
        CallActionIcon(if (entry.video) Icons.Filled.Videocam else Icons.Filled.Call) {
            if (entry.video) onVideo() else onCall()
        }
    }
}

@Composable
private fun ContactRow(name: String, onCall: () -> Unit, onVideo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientAvatar(callGradient(name), name.firstOrNull()?.toString() ?: "?", 44.dp)
        Spacer(Modifier.width(14.dp))
        Text(
            text = name,
            color = NexusTextPrimary,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        CallActionIcon(Icons.Filled.Videocam, onClick = onVideo)
        Spacer(Modifier.width(6.dp))
        CallActionIcon(Icons.Filled.Call, onClick = onCall)
    }
}

@Composable
private fun CallActionIcon(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(NexusSurface, CircleShape)
            .border(1.dp, NexusStroke, CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = NexusAccentSoft, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun EmptyCalls(missedOnly: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 50.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(NexusSurface, CircleShape)
                .border(1.dp, NexusStroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Call, null,
                tint = NexusTextSecondary, modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (missedOnly) "Tidak ada panggilan tak terjawab" else "Belum ada panggilan",
            color = NexusTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Riwayat panggilan akan muncul di sini. Panggilan sendiri belum " +
                "bisa tersambung — server belum menyediakan fiturnya.",
            color = NexusTextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    showBackground = true, backgroundColor = 0xFF121212, widthDp = 360, heightDp = 800,
)
@Composable
private fun CallsPreview() {
    SyntraTheme { CallsScreen() }
}
