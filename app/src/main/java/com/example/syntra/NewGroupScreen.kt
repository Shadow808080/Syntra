package com.example.syntra

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.NetUser
import com.example.syntra.net.SyntraClient
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import kotlinx.coroutines.launch

/**
 * Create a group and pick its members.
 *
 * The backend has no user-search endpoint — only exact username lookup
 * (`GET /users/{username}`) and the following list. So the picker offers the
 * people you follow, plus an "add by username" box for everyone else.
 */
@Composable
fun NewGroupScreen(onClose: () -> Unit, onCreated: (String, String) -> Unit) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val following = remember { mutableStateListOf<NetUser>() }
    val selected = remember { mutableStateListOf<NetUser>() }
    var loading by remember { mutableStateOf(ApiConfig.ENABLED) }
    var looking by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!ApiConfig.ENABLED) {
            loading = false
            notice = "Backend belum dikonfigurasi."
            return@LaunchedEffect
        }
        runCatching { SyntraClient.getFollowing() }
            .onSuccess { following.addAll(it) }
            .onFailure { notice = it.message }
        loading = false
    }

    fun toggle(user: NetUser) {
        val at = selected.indexOfFirst { it.id == user.id }
        if (at >= 0) selected.removeAt(at) else selected.add(user)
    }

    /** Exact-username lookup, the only "search" the backend offers. */
    fun lookup() {
        val name = query.trim().removePrefix("@")
        if (name.isBlank() || !ApiConfig.ENABLED) return
        looking = true
        notice = null
        scope.launch {
            runCatching { SyntraClient.getUser(name) }
                .onSuccess { user ->
                    when {
                        user.isSelf -> notice = "Itu akun kamu sendiri."
                        selected.any { it.id == user.id } -> notice = "${user.username} sudah dipilih."
                        else -> {
                            selected.add(user)
                            query = ""
                        }
                    }
                }
                .onFailure { notice = "Pengguna \"$name\" tidak ditemukan." }
            looking = false
        }
    }

    fun create() {
        if (title.isBlank() || selected.isEmpty() || creating) return
        creating = true
        scope.launch {
            runCatching { SyntraClient.createGroup(title.trim(), selected.map { it.id }) }
                .onSuccess { id -> onCreated(id, title.trim()) }
                .onFailure {
                    Toast.makeText(context, "Buat grup gagal: ${it.message}", Toast.LENGTH_LONG).show()
                }
            creating = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBox(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", onClick = onClose)
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Grup baru", color = NexusTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (selected.isEmpty()) "Pilih anggota" else "${selected.size} anggota dipilih",
                    color = NexusTextSecondary,
                    fontSize = 12.sp,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            // Group name
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                Brush.linearGradient(listOf(NexusAccentSoft, NexusAccent)),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Group, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    FieldBox(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = "Nama grup",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Chips of who is going in
            if (selected.isNotEmpty()) {
                item {
                    LazyRow(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(selected) { user ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(62.dp),
                            ) {
                                Box {
                                    GradientAvatar(
                                        gradient = memberGradient(user.id),
                                        initial = displayOf(user).first().toString(),
                                        size = 52.dp,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(20.dp)
                                            .background(NexusSurface, CircleShape)
                                            .border(1.dp, NexusStroke, CircleShape)
                                            .clickable(
                                                indication = null,
                                                interactionSource = remember { MutableInteractionSource() },
                                            ) { toggle(user) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Filled.Close, "Hapus",
                                            tint = NexusTextSecondary,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    text = displayOf(user).substringBefore(' '),
                                    color = NexusTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // Add by username — the only way to reach someone you don't follow
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FieldBox(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Tambah lewat username",
                        leading = Icons.Filled.Search,
                        imeAction = ImeAction.Search,
                        onImeAction = { lookup() },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(NexusAccent, RoundedCornerShape(14.dp))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { lookup() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (looking) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Icon(
                                Icons.Filled.PersonAdd, "Tambah",
                                tint = Color.White, modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                notice?.let {
                    Text(
                        text = it,
                        color = Color(0xFFFFC46B),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
                    )
                }
            }

            item {
                Text(
                    text = "Dari yang kamu ikuti",
                    color = NexusTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 6.dp),
                )
            }

            when {
                loading -> item { Hint("Memuat daftar…") }
                following.isEmpty() -> item {
                    Hint(
                        "Kamu belum mengikuti siapa pun. Tambahkan anggota lewat " +
                            "username di kolom atas.",
                    )
                }
                else -> items(following) { user ->
                    MemberRow(
                        user = user,
                        checked = selected.any { it.id == user.id },
                        onToggle = { toggle(user) },
                    )
                }
            }
        }

        // Create
        val ready = title.isNotBlank() && selected.isNotEmpty() && !creating
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        brush = if (ready) {
                            Brush.horizontalGradient(listOf(NexusAccentSoft, NexusAccent))
                        } else {
                            Brush.horizontalGradient(listOf(Color(0xFF2A2A32), Color(0xFF2A2A32)))
                        },
                        shape = RoundedCornerShape(26.dp),
                    )
                    .clickable(
                        enabled = ready,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { create() },
                contentAlignment = Alignment.Center,
            ) {
                if (creating) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text(
                        text = "Buat grup",
                        color = if (ready) Color.White else NexusTextSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

private fun displayOf(user: NetUser): String =
    user.displayName.ifBlank { user.username }.ifBlank { "Pengguna" }

private val memberGradients = listOf(
    listOf(Color(0xFF6C5CE7), Color(0xFF3B68F5)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    listOf(Color(0xFFEE5A6F), Color(0xFFF29263)),
    listOf(Color(0xFFDA22FF), Color(0xFF9733EE)),
)

private fun memberGradient(id: String): List<Color> =
    memberGradients[(id.hashCode() and Int.MAX_VALUE) % memberGradients.size]

@Composable
private fun MemberRow(user: NetUser, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onToggle,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientAvatar(memberGradient(user.id), displayOf(user).first().toString(), 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayOf(user),
                color = NexusTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("@${user.username}", color = NexusTextSecondary, fontSize = 12.sp)
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = if (checked) NexusAccent else Color.Transparent,
                    shape = CircleShape,
                )
                .border(
                    width = 1.5.dp,
                    color = if (checked) NexusAccent else NexusStroke,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun FieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: androidx.compose.ui.graphics.vector.ImageVector? = null,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
            .border(1.dp, NexusStroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, null, tint = NexusTextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, color = NexusTextSecondary, fontSize = 14.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = NexusTextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(NexusAccentSoft),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onSearch = { onImeAction() },
                    onDone = { onImeAction() },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        color = NexusTextSecondary,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
    )
}

@Composable
private fun IconBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = NexusTextPrimary, modifier = Modifier.size(22.dp))
    }
}
