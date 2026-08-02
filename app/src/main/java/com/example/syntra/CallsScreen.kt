package com.example.syntra

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.SyntraClient
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusOnline
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusSurfaceElevated
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

    /** Removes a single call from the log (the long-press "Hapus" action). */
    fun remove(context: Context, id: String) {
        val kept = all(context).filterNot { it.id == id }
        val arr = JSONArray()
        kept.forEach { e ->
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
    /** Reported true while a full-screen overlay (open chat) is up, so the host hides the bottom bar. */
    onOverlayChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history = remember { mutableStateListOf<CallEntry>() }
    // People you could call, taken from the real conversation list.
    val contacts = remember { mutableStateListOf<Pair<String, String>>() }
    // peerId -> profile photo url / username, so the call log shows real avatars and
    // "Kirim pesan" can open the right chat — like a phone app's recents.
    val avatars = remember { mutableStateMapOf<String, String>() }
    val usernames = remember { mutableStateMapOf<String, String>() }
    var filter by remember { mutableStateOf(0) } // 0 = semua, 1 = tak terjawab
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showNewCall by remember { mutableStateOf(false) }
    // Long-press "Info" opens a call-detail sheet; "Kirim pesan" opens a chat.
    var detailEntry by remember { mutableStateOf<CallEntry?>(null) }
    // Ids of the history rows currently selected — same model as the chat screen.
    val selectedCalls = remember { mutableStateListOf<String>() }
    androidx.activity.compose.BackHandler(enabled = selectedCalls.isNotEmpty()) { selectedCalls.clear() }
    var openChat by remember { mutableStateOf<Conversation?>(null) }

    // Opening a chat covers the whole screen — tell the host to hide the bottom bar.
    LaunchedEffect(openChat) { onOverlayChange(openChat != null) }

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

    // Contacts and avatars only. The history has exactly ONE owner — the effect above.
    // This used to seed it too, with a bare addAll; on a composition that starts with
    // the Calls tab already visible both effects ran, the second appended the same
    // entries the first had just loaded, and every id appeared twice — which is a
    // duplicate-key crash in the list below, not a cosmetic double.
    LaunchedEffect(Unit) {
        if (ApiConfig.ENABLED) {
            runCatching { SyntraClient.getConversations() }
                .onSuccess { list ->
                    list.filter { it.type == "direct" }.forEach { c ->
                        val pid = c.counterpartId ?: return@forEach
                        contacts.add(c.title to pid)
                        // Only real http URLs are loadable. A bare media id stored here would
                        // win the first branch in CallAvatar and BLOCK the AvatarCache fallback,
                        // leaving the row on the placeholder — the "foto tidak muncul" bug. Store
                        // usable URLs both in the row map and the shared cache (keyed by id,
                        // username, and name) so the log shows photos the chat home already knows.
                        val photo = c.avatarMediaId?.takeIf { it.startsWith("http") }
                        if (photo != null) {
                            avatars[pid] = photo
                            com.example.syntra.net.AvatarCache.put(context, pid, photo)
                            c.counterpartUsername?.takeIf { it.isNotBlank() }
                                ?.let { com.example.syntra.net.AvatarCache.put(context, it, photo) }
                            c.title.takeIf { it.isNotBlank() }
                                ?.let { com.example.syntra.net.AvatarCache.put(context, it, photo) }
                        }
                        c.counterpartUsername?.takeIf { it.isNotBlank() }?.let { usernames[pid] = it }
                    }
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

    fun messagePeer(entry: CallEntry) {
        if (!ApiConfig.ENABLED || entry.peerId.isBlank()) return
        scope.launch {
            val convId = runCatching { SyntraClient.createDirect(entry.peerId) }.getOrNull() ?: return@launch
            openChat = Conversation(
                id = convId,
                name = entry.peerName.ifBlank { "Tanpa nama" },
                message = "",
                time = "",
                counterpartId = entry.peerId,
                counterpartUsername = usernames[entry.peerId],
            )
        }
    }

    fun deleteEntry(entry: CallEntry) {
        CallLog.remove(context, entry.id)
        history.removeAll { it.id == entry.id }
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
            // While rows are selected it is replaced by the same contextual bar the
            // chat screen uses, so "select several, then act" means the same thing in
            // both places instead of being a per-row menu here and a header there.
            if (selectedCalls.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NexusSurfaceElevated)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(start = 6.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CallIconButton(Icons.Filled.Close, "Batal pilih") { selectedCalls.clear() }
                    Text(
                        "${selectedCalls.size}",
                        color = NexusTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    // Select-all / deselect-all over exactly the rows currently on screen
                    // (respects the Missed filter and any search), so "pilih semua" never
                    // silently grabs entries the list is hiding. Toggles: once everything
                    // shown is picked, the same button clears the selection.
                    val shownIds = shown.map { it.id }
                    val allSelected = shownIds.isNotEmpty() && shownIds.all { it in selectedCalls }
                    CallIconButton(
                        if (allSelected) Icons.Filled.Deselect else Icons.Filled.SelectAll,
                        if (allSelected) "Batal pilih semua" else "Pilih semua",
                    ) {
                        if (allSelected) {
                            selectedCalls.clear()
                        } else {
                            selectedCalls.clear()
                            selectedCalls.addAll(shownIds)
                        }
                    }
                    // Only offered for a single pick: calling back, messaging and the
                    // detail sheet are all about ONE conversation, and picking an
                    // arbitrary one of several would be worse than not offering it.
                    if (selectedCalls.size == 1) {
                        val one = history.firstOrNull { it.id == selectedCalls.first() }
                        if (one != null) {
                            CallIconButton(Icons.Filled.Call, "Panggil") {
                                selectedCalls.clear(); placeCall(one.peerName, one.peerId, one.video)
                            }
                            CallIconButton(Icons.AutoMirrored.Filled.Message, "Kirim pesan") {
                                selectedCalls.clear(); messagePeer(one)
                            }
                            CallIconButton(Icons.Filled.Info, "Info") {
                                selectedCalls.clear(); detailEntry = one
                            }
                        }
                    }
                    CallIconButton(Icons.Filled.Delete, "Hapus") {
                        val picked = selectedCalls.toList()
                        selectedCalls.clear()
                        picked.forEach { id -> history.firstOrNull { it.id == id }?.let { deleteEntry(it) } }
                    }
                }
            } else {
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
                            avatarUrl = avatars[entry.peerId],
                            // Tap the row → call back with the same kind as the entry
                            // (a video call → video; a voice call → voice), the way a
                            // phone app's recents behaves.
                            onCallBack = { placeCall(entry.peerName, entry.peerId, entry.video) },
                            onVoice = { placeCall(entry.peerName, entry.peerId, false) },
                            onVideo = { placeCall(entry.peerName, entry.peerId, true) },
                            onMessage = { messagePeer(entry) },
                            onInfo = { detailEntry = entry },
                            onDelete = { deleteEntry(entry) },
                            isSelected = entry.id in selectedCalls,
                            onLongPress = {
                                if (entry.id in selectedCalls) selectedCalls.remove(entry.id)
                                else selectedCalls.add(entry.id)
                            },
                            onTapWhileSelecting = if (selectedCalls.isEmpty()) {
                                null
                            } else {
                                {
                                    if (entry.id in selectedCalls) selectedCalls.remove(entry.id)
                                    else selectedCalls.add(entry.id)
                                }
                            },
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

        // Call-detail sheet (opened from the row's "Info panggilan").
        detailEntry?.let { entry ->
            CallDetailSheet(
                entry = entry,
                avatarUrl = avatars[entry.peerId],
                onDismiss = { detailEntry = null },
                onVoice = { detailEntry = null; placeCall(entry.peerName, entry.peerId, false) },
                onVideo = { detailEntry = null; placeCall(entry.peerName, entry.peerId, true) },
                onMessage = { detailEntry = null; messagePeer(entry) },
                onDelete = { detailEntry = null; deleteEntry(entry) },
            )
        }

        // "Kirim pesan" opens the chat full-screen over the calls list.
        openChat?.let { convo ->
            ChatDetailScreen(conversation = convo, onBack = { openChat = null })
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

/** iOS/Google-Phone-style call detail: who, the exact call, and quick actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallDetailSheet(
    entry: CallEntry,
    avatarUrl: String?,
    onDismiss: () -> Unit,
    onVoice: () -> Unit,
    onVideo: () -> Unit,
    onMessage: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NexusSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CallAvatar(avatarUrl = avatarUrl, name = entry.peerName, peerId = entry.peerId, size = 84.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                entry.peerName.ifBlank { "Tanpa nama" },
                color = NexusTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            val meta = buildString {
                append(if (entry.video) "Video · " else "Suara · ")
                append(directionLabel(entry.direction))
                if (entry.durationSec > 0) append(" · " + durationText(entry.durationSec))
            }
            Text(meta, color = NexusTextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                java.text.SimpleDateFormat("EEEE, d MMM yyyy • HH.mm", java.util.Locale("id")).format(entry.at),
                color = NexusTextSecondary, fontSize = 12.sp,
            )
            Spacer(Modifier.height(22.dp))
            // Action row: Suara · Video · Pesan.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                DetailAction(Icons.Filled.Call, "Suara", onVoice)
                DetailAction(Icons.Filled.Videocam, "Video", onVideo)
                DetailAction(Icons.AutoMirrored.Filled.Message, "Pesan", onMessage)
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDelete,
                    )
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF5D5D), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Hapus dari riwayat", color = Color(0xFFFF5D5D), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DetailAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onClick,
        ),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(Brush.verticalGradient(listOf(NexusAccentSoft, NexusAccent)), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(label, color = NexusTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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

// Formatters are built ONCE and reused. They used to be `new SimpleDateFormat(...)`
// created inside phoneCallTime — i.e. on every composition of every visible row — and
// building a SimpleDateFormat (pattern parse + locale data load) on each frame while
// scrolling is what made the call list stutter. DateTimeFormatter is immutable and
// thread-safe, so a single shared instance is safe to reuse.
private val callTimeLocale = java.util.Locale.forLanguageTag("id-ID")
private val callClockFmt = java.time.format.DateTimeFormatter.ofPattern("HH.mm", callTimeLocale)
private val callDayFmt = java.time.format.DateTimeFormatter.ofPattern("EEEE", callTimeLocale)
private val callDateFmt = java.time.format.DateTimeFormatter.ofPattern("d MMM", callTimeLocale)

/** Phone-app style timestamp: clock today, "Kemarin", day name this week, else date. */
private fun phoneCallTime(at: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val then = java.time.Instant.ofEpochMilli(at).atZone(zone)
    val today = java.time.LocalDate.now(zone)
    val thenDate = then.toLocalDate()
    return when {
        thenDate == today -> then.format(callClockFmt)
        thenDate == today.minusDays(1) -> "Kemarin"
        thenDate.isAfter(today.minusDays(7)) -> then.format(callDayFmt)
        else -> then.format(callDateFmt)
    }
}

private fun directionLabel(d: CallDirection): String = when (d) {
    CallDirection.OUTGOING -> "Keluar"
    CallDirection.INCOMING -> "Masuk"
    CallDirection.MISSED -> "Tak terjawab"
}

private fun durationText(seconds: Int): String = when {
    seconds <= 0 -> ""
    seconds < 60 -> "${seconds}d"
    else -> "${seconds / 60}m ${seconds % 60}d"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallRow(
    entry: CallEntry,
    avatarUrl: String?,
    onCallBack: () -> Unit,
    onVoice: () -> Unit,
    onVideo: () -> Unit,
    onMessage: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    /** True while this row is part of the current selection. */
    isSelected: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    /** Non-null only while a selection is active: a plain tap toggles this row. */
    onTapWhileSelecting: (() -> Unit)? = null,
) {
    val (icon, tint) = when (entry.direction) {
        CallDirection.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to NexusOnline
        CallDirection.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to NexusAccentSoft
        CallDirection.MISSED -> Icons.AutoMirrored.Filled.CallMissed to Color(0xFFFF5D5D)
    }
    var menuOpen by remember { mutableStateOf(false) }
    val selectTint by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(160),
        label = "call-select",
    )

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Same treatment as a selected chat message: the row lifts out of the
                // list while it is picked, so a bulk delete can never be about a row
                // you did not mean.
                .then(
                    if (selectTint > 0.01f) {
                        Modifier.background(NexusAccent.copy(alpha = 0.16f * selectTint))
                    } else {
                        Modifier
                    },
                )
                // Tap → call back (or toggle, while selecting); long-press → start a
                // selection, falling back to the old per-row menu when there is none.
                .combinedClickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { if (onTapWhileSelecting != null) onTapWhileSelecting() else onCallBack() },
                    onLongClick = { if (onLongPress != null) onLongPress() else menuOpen = true },
                )
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CallAvatar(avatarUrl = avatarUrl, name = entry.peerName, peerId = entry.peerId, size = 50.dp)
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
                    Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    if (entry.video) {
                        Icon(Icons.Filled.Videocam, null, tint = NexusTextSecondary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    val dur = durationText(entry.durationSec)
                    Text(
                        text = directionLabel(entry.direction) +
                            (if (dur.isNotBlank()) " · $dur" else "") +
                            " · " + phoneCallTime(entry.at),
                        color = NexusTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            CallActionIcon(if (entry.video) Icons.Filled.Videocam else Icons.Filled.Call, onClick = onCallBack)
        }

        // Long-press context menu.
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Panggil suara") },
                leadingIcon = { Icon(Icons.Filled.Call, null, modifier = Modifier.size(20.dp)) },
                onClick = { menuOpen = false; onVoice() },
            )
            DropdownMenuItem(
                text = { Text("Panggil video") },
                leadingIcon = { Icon(Icons.Filled.Videocam, null, modifier = Modifier.size(20.dp)) },
                onClick = { menuOpen = false; onVideo() },
            )
            DropdownMenuItem(
                text = { Text("Kirim pesan") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Message, null, modifier = Modifier.size(20.dp)) },
                onClick = { menuOpen = false; onMessage() },
            )
            DropdownMenuItem(
                text = { Text("Info panggilan") },
                leadingIcon = { Icon(Icons.Filled.Info, null, modifier = Modifier.size(20.dp)) },
                onClick = { menuOpen = false; onInfo() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Hapus dari riwayat", color = Color(0xFFFF5D5D)) },
                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF5D5D), modifier = Modifier.size(20.dp)) },
                onClick = { menuOpen = false; onDelete() },
            )
        }
    }
}

/**
 * The contact's photo, or Syntra's empty-profile mark — never a letter.
 *
 * The call log is built from a local history file that stores no avatar, so almost
 * every row fell to the initial branch. It now looks the photo up in the shared
 * AvatarCache by id and username (the same store chat and rooms write to), and hands
 * whatever it finds to GradientAvatar — which draws Syntra's own placeholder when there
 * is genuinely no photo, exactly like every other list in the app.
 */
@Composable
private fun CallAvatar(avatarUrl: String?, name: String, peerId: String, size: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    // Accept only a real URL here: a non-http value (a bare media id) is not loadable
    // and, worse, would short-circuit this chain and hide the cached photo below it.
    val resolved = avatarUrl?.takeIf { it.startsWith("http") }
        ?: peerId.takeIf { it.isNotBlank() }?.let { com.example.syntra.net.AvatarCache.get(ctx, it) }
        ?: name.takeIf { it.isNotBlank() }?.let { com.example.syntra.net.AvatarCache.get(ctx, it) }
    GradientAvatar(
        gradient = callGradient(peerId.ifBlank { name }),
        initial = "",
        size = size,
        photoUrl = resolved,
    )
}

@Composable
private fun ContactRow(name: String, onCall: () -> Unit, onVideo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CallAvatar(avatarUrl = null, name = name, peerId = "", size = 44.dp)
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
