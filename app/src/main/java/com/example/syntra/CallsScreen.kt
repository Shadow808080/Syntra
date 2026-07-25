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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
            }
                // Guard the LazyColumn key: two calls logged in the same millisecond
                // (or a double teardown) once produced entries with an identical id,
                // which crashed the list with a duplicate-key error. De-dupe on load.
                .distinctBy { it.id }
                .sortedByDescending { it.at }
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showNewCall by remember { mutableStateOf(false) }

    // A call placed or received anywhere in the app appends to the log; re-read it
    // whenever this tab comes back — or the moment a call finishes — so the history
    // is always current without a manual refresh.
    LaunchedEffect(visible, CallController.isBusy) {
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
        if (CallController.isBusy) {
            Toast.makeText(context, "Masih ada panggilan berlangsung.", Toast.LENGTH_SHORT).show()
            return
        }
        // Resolve (or create) the direct conversation, then hand the call to the
        // app-root CallHost (which floats over every tab).
        scope.launch {
            val convId = runCatching { SyntraClient.createDirect(peerId) }.getOrNull()
            if (convId == null) {
                Toast.makeText(context, "Gagal memulai panggilan.", Toast.LENGTH_SHORT).show()
            } else {
                CallController.startOutgoing(convId, name, peerId, video)
            }
        }
    }

    val byFilter = if (filter == 1) history.filter { it.direction == CallDirection.MISSED } else history
    val shown = if (query.isBlank()) byFilter
    else byFilter.filter { it.peerName.contains(query, ignoreCase = true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusBackground),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // App bar — plain page title, no wordmark. Search collapses in place.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 20.dp, end = 14.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (searching) {
                    CallIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Tutup cari") {
                        searching = false; query = ""
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Cari nama…", color = NexusTextSecondary, fontSize = 16.sp)
                        }
                        val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = NexusTextPrimary, fontSize = 16.sp),
                            cursorBrush = SolidColor(NexusAccentSoft),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    if (query.isNotEmpty()) {
                        CallIconButton(Icons.Filled.Close, "Bersihkan") { query = "" }
                    }
                } else {
                    Text(
                        "Panggilan",
                        color = NexusTextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.weight(1f))
                    CallIconButton(Icons.Filled.Search, "Cari") { searching = true }
                }
            }

            // Segmented filter — quiet pill, not a gradient.
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Semua", "Tak terjawab").forEachIndexed { i, label ->
                    val active = filter == i
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (active) NexusAccent.copy(alpha = 0.16f) else NexusSurface)
                            .border(1.dp, if (active) NexusAccent.copy(alpha = 0.45f) else NexusStroke, RoundedCornerShape(50))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { filter = i }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = label,
                            color = if (active) NexusTextPrimary else NexusTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp),
            ) {
                if (shown.isEmpty()) {
                    item { EmptyCalls(missedOnly = filter == 1 && query.isBlank()) }
                } else {
                    items(shown, key = { it.id }) { entry ->
                        CallRow(
                            entry = entry,
                            onCall = { placeCall(entry.peerName, entry.peerId, false) },
                            onVideo = { placeCall(entry.peerName, entry.peerId, true) },
                        )
                    }
                    if (query.isBlank()) {
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
                                    .padding(vertical = 20.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        // FAB: start a new call — opens a contact picker sheet (the "big app" flow),
        // instead of a long inline contact list under the history.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 20.dp, bottom = 20.dp)
                .size(56.dp)
                .background(
                    Brush.verticalGradient(listOf(NexusAccentSoft, NexusAccent)),
                    CircleShape,
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { showNewCall = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, "Panggilan baru", tint = Color.White, modifier = Modifier.size(28.dp))
        }

        if (showNewCall) {
            NewCallSheet(
                contacts = contacts,
                onDismiss = { showNewCall = false },
                onCall = { name, id -> showNewCall = false; placeCall(name, id, false) },
                onVideo = { name, id -> showNewCall = false; placeCall(name, id, true) },
            )
        }

        // The call itself is rendered by CallHost at the app root (floats over all
        // tabs, survives navigation, minimizes to a draggable window).
    }
}

/**
 * Contact picker for starting a new call — a bottom sheet with search + your direct
 * contacts, each offering a voice and a video button. This is the "new call" entry
 * point, keeping the main screen focused on history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewCallSheet(
    contacts: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onCall: (String, String) -> Unit,
    onVideo: (String, String) -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val filtered by remember {
        derivedStateOf {
            if (q.isBlank()) contacts else contacts.filter { it.first.contains(q, ignoreCase = true) }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NexusSurface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                "Panggilan baru",
                color = NexusTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            )
            // Search field.
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NexusBackground)
                    .border(1.dp, NexusStroke, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = NexusTextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("Cari kontak…", color = NexusTextSecondary, fontSize = 14.sp)
                    BasicTextField(
                        value = q,
                        onValueChange = { q = it },
                        singleLine = true,
                        textStyle = TextStyle(color = NexusTextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(NexusAccentSoft),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                Text(
                    if (contacts.isEmpty()) "Belum ada kontak. Mulai obrolan dulu untuk bisa menelepon."
                    else "Tidak ada kontak cocok.",
                    color = NexusTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(filtered, key = { it.second }) { (name, id) ->
                        ContactRow(
                            name = name,
                            onCall = { onCall(name, id) },
                            onVideo = { onVideo(name, id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallIconButton(icon: ImageVector, cd: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, cd, tint = NexusTextPrimary, modifier = Modifier.size(23.dp))
    }
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------


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
            text = "Riwayat panggilan suara & video kamu akan muncul di sini. Ketuk " +
                "tombol + untuk memulai panggilan baru.",
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
