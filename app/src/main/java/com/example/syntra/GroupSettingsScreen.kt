package com.example.syntra

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.AvatarCache
import com.example.syntra.net.NetMember
import com.example.syntra.net.NetUser
import com.example.syntra.net.SyntraClient
import com.example.syntra.ui.theme.DangerFill
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusSurfaceElevated
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import kotlinx.coroutines.launch

private val MemberGradient = listOf(NexusAccentSoft, NexusAccent)

/** Human label for a role. */
private fun roleLabel(role: String): String = when (role) {
    "owner" -> "Pemilik"
    "admin" -> "Admin"
    else -> "Anggota"
}

/**
 * Group settings / info: the group's name and avatar, its members with their roles,
 * and — for an admin or the owner — the ability to add members or remove (kick) one.
 * Anyone can leave the group from here.
 */
@Composable
fun GroupSettingsScreen(
    conversation: Conversation,
    onClose: () -> Unit,
    onAddMembers: () -> Unit,
    onLeave: () -> Unit,
    /** Bumped by the caller to force a member reload (e.g. after adding members). */
    reloadKey: Int = 0,
) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val members = remember { mutableStateListOf<NetMember>() }
    var loading by remember { mutableStateOf(true) }
    var pendingKick by remember { mutableStateOf<NetMember?>(null) }
    // Long-press member menu (jadikan admin / keluarkan), the group's live avatar, and
    // the "uploading a new icon" flag.
    var memberMenu by remember { mutableStateOf<NetMember?>(null) }
    // Seeded from the cache when the row we came from had nothing — which is the normal
    // case, because the conversation LIST never carries a usable group icon.
    var avatarUrl by remember(conversation.id) {
        mutableStateOf(conversation.avatarUrl ?: AvatarCache.get(context, conversation.id))
    }
    var uploadingAvatar by remember { mutableStateOf(false) }
    // Group description (fetched from GET /conversations/{id}) + its edit dialog.
    var description by remember(conversation.id) { mutableStateOf("") }
    // What is in the field right now. Kept apart from [description] (what the server
    // has) so "Simpan" can appear exactly when the two differ, and "Batal" is a reset.
    var descDraft by remember(conversation.id) { mutableStateOf("") }
    var descExpanded by remember(conversation.id) { mutableStateOf(false) }
    // Set by the text layout when the collapsed description was actually clipped — the
    // only honest trigger for "Tampilkan selengkapnya".
    var descOverflows by remember(conversation.id) { mutableStateOf(false) }
    // The description is plain text until you tap it; only then does a field appear.
    var editingDesc by remember(conversation.id) { mutableStateOf(false) }

    // Picked, but not yet committed. Choosing a photo used to upload it and PATCH the
    // group in one step, so a mis-tap in the gallery became everyone's new group icon
    // AND a file in storage. Nothing leaves the device until the preview is approved.
    var pendingAvatar by remember { mutableStateOf<android.net.Uri?>(null) }

    val pickAvatar = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) pendingAvatar = uri }

    // Approved: downsample → upload → PATCH the group.
    fun commitAvatar(uri: android.net.Uri) {
        pendingAvatar = null
        uploadingAvatar = true
        scope.launch {
            val newUrl = runCatching {
                val (bytes, w, h) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    decodeAvatarBytes(context, uri)
                } ?: error("Tidak bisa membaca gambar.")
                val mediaId = SyntraClient.uploadMedia("image", "jpg", "image/jpeg", bytes, w, h)
                SyntraClient.updateGroup(conversation.id, avatarMediaId = mediaId)
            }.getOrNull()
            uploadingAvatar = false
            if (!newUrl.isNullOrBlank()) {
                avatarUrl = newUrl
                // Persist it NOW. This URL is the only one the app will see until
                // someone opens this screen again — the list refresh that happens the
                // moment you go back sends a bare media id, and that used to wipe the
                // photo off every surface seconds after it was set.
                AvatarCache.put(context, conversation.id, newUrl)
                Toast.makeText(context, "Foto grup diperbarui.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Gagal mengubah foto grup.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun load() {
        if (!ApiConfig.ENABLED) { loading = false; return }
        loading = true
        runCatching { SyntraClient.getMembers(conversation.id) }
            .onSuccess { members.clear(); members.addAll(it) }
            .onFailure { Toast.makeText(context, "Gagal memuat anggota: ${it.message}", Toast.LENGTH_SHORT).show() }
        // This is the only endpoint that resolves the group's icon to a real URL, so it
        // feeds the cache every screen reads from — not just this screen's own header.
        runCatching { SyntraClient.getGroupInfo(conversation.id) }.onSuccess { info ->
            description = info.description
            descDraft = info.description
            if (!info.avatarUrl.isNullOrBlank()) {
                avatarUrl = info.avatarUrl
                AvatarCache.put(context, conversation.id, info.avatarUrl)
            }
        }
        loading = false
    }

    fun saveDescription() {
        val newDesc = descDraft
        scope.launch {
            runCatching { SyntraClient.updateGroup(conversation.id, description = newDesc) }
                .onSuccess {
                    description = newDesc
                    Toast.makeText(context, "Deskripsi grup diperbarui.", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, "Gagal memperbarui deskripsi: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
    LaunchedEffect(conversation.id, reloadKey) { load() }

    val myRole = members.firstOrNull { it.userId == SyntraClient.myUserId }?.role ?: "member"
    // Adding/removing members and renaming the group stay with admins and the owner.
    val canManage = myRole == "owner" || myRole == "admin"
    // The description and the icon are shared content: any member can fix them. Locked
    // to admins, a group lives with an empty description until one particular person
    // gets round to it. The server enforces the same split, and now names whoever
    // changed something in the system message.
    val canEditInfo = members.any { it.userId == SyntraClient.myUserId } || members.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground),
    ) {
        // Top bar.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusSurface)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
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
            Text("Info grup", color = NexusTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // Header: avatar + name + member count.
            item(key = "header") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = if (canEditInfo) {
                            Modifier.clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {
                                pickAvatar.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            }
                        } else Modifier,
                    ) {
                        GradientAvatar(
                            gradient = MemberGradient,
                            initial = conversation.name.firstOrNull()?.toString() ?: "#",
                            size = 92.dp,
                            photoUrl = avatarUrl,
                            // The GROUP placeholder, not the single bust — this screen is
                            // about a group, and the one-person mark read as "a person
                            // called Keluarga Besar".
                            group = true,
                        )
                        if (uploadingAvatar) {
                            Box(
                                modifier = Modifier.size(92.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(26.dp)) }
                        } else if (canEditInfo) {
                            // A small camera badge hints the icon can be changed.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(NexusAccent)
                                    .padding(6.dp),
                            ) { Icon(Icons.Filled.PhotoCamera, "Ubah ikon grup", tint = Color.White, modifier = Modifier.size(18.dp)) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        conversation.name,
                        color = NexusTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (members.isEmpty()) "Grup" else "${members.size} anggota",
                        color = NexusTextSecondary,
                        fontSize = 13.sp,
                    )
                }
            }

            // Group description — edited in place. There is no "Ubah" button and no
            // dialog: the text IS the field. A dialog to change one paragraph puts a
            // door in front of a room you can already see into, and the button only
            // existed to open that door.
            item(key = "description") {
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(NexusSurface))
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text("Deskripsi", color = NexusTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    if (editingDesc) {
                        // A field ONLY while you are actually editing. It grows with the
                        // text — no maxLines, no fixed height — so a long description is
                        // written in full rather than through a three-line window.
                        BasicTextField(
                            value = descDraft,
                            onValueChange = { if (it.length <= 500) descDraft = it },
                            textStyle = TextStyle(color = NexusTextPrimary, fontSize = 14.sp, lineHeight = 20.sp),
                            cursorBrush = SolidColor(NexusAccentSoft),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (descDraft.isEmpty()) {
                                    Text(
                                        "Tambahkan deskripsi grup",
                                        color = NexusTextSecondary,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                    )
                                }
                                inner()
                            },
                        )
                    } else {
                        // At rest the description is TEXT, not a box. It used to be a
                        // text field standing open at a fixed three lines whether or not
                        // anyone was editing, which made a paragraph of prose look like a
                        // form control on a screen that is mostly reading.
                        Text(
                            text = description.ifBlank {
                                if (canEditInfo) "Tambahkan deskripsi grup" else "Belum ada deskripsi"
                            },
                            color = if (description.isNotBlank()) NexusTextPrimary else NexusTextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = if (descExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            // Asked of the layout, not guessed from length: the toggle
                            // appears exactly when the third line actually got cut.
                            onTextLayout = { if (!descExpanded) descOverflows = it.hasVisualOverflow },
                            modifier = if (!canEditInfo) Modifier else Modifier.clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { descDraft = description; editingDesc = true },
                        )

                        // Text alone — no chevron, no pill, no row of its own furniture.
                        // It is a continuation of the paragraph above it, and anything
                        // more turns three lines of description into a component.
                        if (descOverflows || descExpanded) {
                            Text(
                                if (descExpanded) "ringkas" else "selengkapnya",
                                color = NexusAccentSoft,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { descExpanded = !descExpanded },
                            )
                        }
                    }

                    // Only while editing. These used to appear the instant the text
                    // differed from the server's copy — which, with a field permanently
                    // open, meant they could show up from a stray keystroke.
                    if (editingDesc) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val changed = descDraft != description
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (changed) NexusAccent else NexusSurfaceElevated)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { if (changed) saveDescription(); editingDesc = false }
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    "Simpan",
                                    color = if (changed) Color.White else NexusTextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { descDraft = description; editingDesc = false }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text("Batal", color = NexusTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(NexusSurface))
            }

            // Add members (admin/owner only).
            if (canManage) {
                item(key = "add") {
                    GroupActionRow(
                        icon = Icons.Filled.PersonAdd,
                        label = "Tambah anggota",
                        tint = NexusAccentSoft,
                        onClick = onAddMembers,
                    )
                }
            }

            item(key = "members-title") {
                Text(
                    "Anggota",
                    color = NexusTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp),
                )
            }

            if (loading) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NexusAccentSoft, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                }
            } else {
                items(members, key = { it.userId }) { member ->
                    val isMe = member.userId == SyntraClient.myUserId
                    // Manageable: I run the group, the target isn't the owner, and isn't me.
                    // Long-press opens the actions menu (jadikan admin / keluarkan).
                    val manageable = canManage && member.role != "owner" && !isMe
                    MemberRow(
                        member = member,
                        isMe = isMe,
                        onLongPress = if (manageable) ({ memberMenu = member }) else null,
                    )
                }
            }

            // Leave group — available to everyone.
            item(key = "leave") {
                Spacer(Modifier.height(10.dp))
                GroupActionRow(
                    icon = Icons.Filled.Logout,
                    label = "Keluar dari grup",
                    tint = Color(0xFFFF6B6B),
                    onClick = onLeave,
                )
                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(16.dp))
            }
        }
    }

    pendingKick?.let { member ->
        ConfirmActionDialog(
            title = "Keluarkan ${member.displayName}?",
            message = "Mereka tidak akan lagi menerima pesan dari grup ini.",
            confirmText = "Keluarkan",
            onDismiss = { pendingKick = null },
            onConfirm = {
                pendingKick = null
                scope.launch {
                    runCatching { SyntraClient.removeMember(conversation.id, member.userId) }
                        .onSuccess {
                            members.removeAll { it.userId == member.userId }
                            Toast.makeText(context, "${member.displayName} dikeluarkan.", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure {
                            Toast.makeText(context, "Gagal mengeluarkan: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            },
        )
    }

    // Long-press member menu: promote/demote, or remove from the group.
    memberMenu?.let { member ->
        fun changeRole(role: String, done: String) {
            memberMenu = null
            scope.launch {
                runCatching { SyntraClient.setMemberRole(conversation.id, member.userId, role) }
                    .onSuccess {
                        val i = members.indexOfFirst { it.userId == member.userId }
                        if (i >= 0) members[i] = members[i].copy(role = role)
                        Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { Toast.makeText(context, "Gagal: ${it.message}", Toast.LENGTH_SHORT).show() }
            }
        }
        androidx.compose.ui.window.Dialog(onDismissRequest = { memberMenu = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NexusSurface, RoundedCornerShape(20.dp))
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    member.displayName,
                    color = NexusTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
                )
                if (member.role == "admin") {
                    MemberMenuRow(Icons.Filled.Person, "Jadikan anggota") {
                        changeRole("member", "${member.displayName} kini anggota biasa.")
                    }
                } else {
                    MemberMenuRow(Icons.Filled.AdminPanelSettings, "Jadikan admin") {
                        changeRole("admin", "${member.displayName} kini admin.")
                    }
                }
                MemberMenuRow(Icons.Filled.PersonRemove, "Keluarkan dari grup", danger = true) {
                    memberMenu = null
                    pendingKick = member
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }

    // Approve the picture before it becomes the group's face. This one really does
    // upload, and it is visible to every member — a mis-tap in the gallery should not
    // be the thing that decides it.
    pendingAvatar?.let { uri ->
        GroupAvatarPreviewDialog(
            uri = uri,
            onCancel = { pendingAvatar = null },
            onApply = { commitAvatar(uri) },
        )
    }

}

/** Shows the picked photo cropped as it will appear, with apply / cancel. */
@Composable
private fun GroupAvatarPreviewDialog(
    uri: android.net.Uri,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusSurfaceElevated, RoundedCornerShape(22.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Foto grup baru", color = NexusTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Semua anggota akan melihat foto ini.",
                color = NexusTextSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(18.dp))
            // Circular, because that is the only shape it will ever be shown in — a
            // square preview would hide exactly what the crop is about to cut off.
            coil.compose.AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(150.dp).clip(CircleShape),
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(NexusSurface)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onCancel,
                        )
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Batal", color = NexusTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(NexusAccent)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onApply,
                        )
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Terapkan", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun MemberMenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) Color(0xFFFF6B6B) else NexusTextPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(icon, null, tint = if (danger) tint else NexusAccentSoft, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = tint, fontSize = 15.sp)
    }
}

@Composable
private fun MemberRow(member: NetMember, isMe: Boolean, onLongPress: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onLongPress != null) {
                    Modifier.combinedClickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                        onLongClick = onLongPress,
                    )
                } else Modifier,
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientAvatar(
            gradient = MemberGradient,
            initial = member.displayName.firstOrNull()?.toString() ?: "?",
            size = 44.dp,
            photoUrl = member.avatarUrl,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (isMe) "${member.displayName} (Anda)" else member.displayName,
                color = NexusTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text("@${member.username}", color = NexusTextSecondary, fontSize = 12.sp)
        }
        if (member.role != "member") {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(NexusAccentSoft.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(roleLabel(member.role), color = NexusAccentSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Downsamples a picked image and returns JPEG bytes + dimensions for a group avatar.
 * Small cap (1024) keeps the upload light and can never blow the Canvas bitmap limit.
 */
private fun decodeAvatarBytes(context: android.content.Context, uri: android.net.Uri): Triple<ByteArray, Int, Int>? = runCatching {
    val cr = context.contentResolver
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    val maxDim = 1024
    var sample = 1
    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = cr.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) } ?: return@runCatching null
    val out = java.io.ByteArrayOutputStream()
    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
    Triple(out.toByteArray(), bmp.width, bmp.height)
}.getOrNull()

@Composable
private fun GroupActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(tint.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (tint == Color(0xFFFF6B6B)) tint else NexusTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Bottom sheet to add people to a group. Candidates are the people you follow who
 * aren't already members; pick any number and confirm.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddMembersSheet(
    conversationId: String,
    onDismiss: () -> Unit,
    onAdded: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    val candidates = remember { mutableStateListOf<NetUser>() }
    val selected = remember { mutableStateListOf<String>() }

    LaunchedEffect(conversationId) {
        if (!ApiConfig.ENABLED) { loading = false; return@LaunchedEffect }
        runCatching {
            val memberIds = SyntraClient.getMembers(conversationId).map { it.userId }.toSet()
            SyntraClient.getFollowing().filter { it.id !in memberIds }
        }.onSuccess { candidates.clear(); candidates.addAll(it) }
            .onFailure { Toast.makeText(context, "Gagal memuat kontak: ${it.message}", Toast.LENGTH_SHORT).show() }
        loading = false
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF15151C),
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp),
        ) {
            Text(
                "Tambah anggota",
                color = NexusTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NexusAccentSoft, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
                candidates.isEmpty() -> Text(
                    "Tidak ada kontak untuk ditambahkan. Semua orang yang kamu ikuti sudah menjadi anggota.",
                    color = NexusTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(candidates, key = { it.id }) { user ->
                        val checked = user.id in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { if (checked) selected.remove(user.id) else selected.add(user.id) }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GradientAvatar(
                                gradient = MemberGradient,
                                initial = user.displayName.firstOrNull()?.toString() ?: "?",
                                size = 42.dp,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.displayName, color = NexusTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("@${user.username}", color = NexusTextSecondary, fontSize = 12.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (checked) NexusAccent else Color(0xFF2C2C36)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (checked) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            if (!loading && candidates.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    val enabled = selected.isNotEmpty() && !submitting
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(if (enabled) NexusAccent else Color(0xFF2C2C36))
                            .clickable(
                                enabled = enabled,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {
                                submitting = true
                                val ids = selected.toList()
                                scope.launch {
                                    val ok = runCatching { SyntraClient.addMembers(conversationId, ids) }.isSuccess
                                    submitting = false
                                    if (ok) {
                                        Toast.makeText(context, "${ids.size} anggota ditambahkan.", Toast.LENGTH_SHORT).show()
                                        onAdded(ids.size)
                                    } else {
                                        Toast.makeText(context, "Gagal menambahkan anggota.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (selected.isEmpty()) "Pilih orang" else "Tambah (${selected.size})",
                            color = if (enabled) Color.White else NexusTextSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
