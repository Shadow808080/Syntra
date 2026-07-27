package com.example.syntra

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusSurfaceElevated
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary

/**
 * Details step of publishing a Short: collected AFTER the video is trimmed. Title,
 * description and tags are folded into the single [caption] the backend stores;
 * audience + comments map to real reel fields; the agreement is a local gate so a
 * Short can't be posted without confirming it follows the guidelines.
 */
@Composable
fun ReelDetailsScreen(
    onBack: () -> Unit,
    onPost: (caption: String, visibility: String, commentsEnabled: Boolean) -> Unit,
) {
    BackHandler(onBack = onBack)
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf("public") }
    var commentsEnabled by remember { mutableStateOf(true) }
    var agreed by remember { mutableStateOf(false) }

    val canPost = title.isNotBlank() && agreed

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground),
    ) {
        // Top bar.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali",
                tint = NexusTextPrimary,
                modifier = Modifier
                    .size(26.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onBack,
                    ),
            )
            Spacer(Modifier.width(14.dp))
            Text("Detail Reel", color = NexusTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            LabeledField(
                label = "Judul",
                value = title,
                onValueChange = { if (it.length <= 100) title = it },
                placeholder = "Beri judul yang menarik",
            )
            Spacer(Modifier.height(16.dp))
            LabeledField(
                label = "Deskripsi",
                value = description,
                onValueChange = { if (it.length <= 2000) description = it },
                placeholder = "Ceritakan tentang video ini…",
                minHeight = 96.dp,
                singleLine = false,
            )
            Spacer(Modifier.height(16.dp))
            LabeledField(
                label = "Tag",
                value = tags,
                onValueChange = { if (it.length <= 200) tags = it },
                placeholder = "lucu viral fyp",
                leading = Icons.Filled.Tag,
            )
            Text(
                "Pisahkan dengan spasi. Otomatis jadi #tag.",
                color = NexusTextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )

            Spacer(Modifier.height(22.dp))
            SectionLabel("Siapa yang bisa melihat")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AudienceChip("Publik", Icons.Filled.Public, visibility == "public", Modifier.weight(1f)) { visibility = "public" }
                AudienceChip("Pengikut", Icons.Filled.Group, visibility == "followers", Modifier.weight(1f)) { visibility = "followers" }
                AudienceChip("Privat", Icons.Filled.Lock, visibility == "private", Modifier.weight(1f)) { visibility = "private" }
            }

            Spacer(Modifier.height(20.dp))
            ToggleRow(
                title = "Izinkan komentar",
                subtitle = "Orang lain bisa mengomentari reel ini",
                checked = commentsEnabled,
                onToggle = { commentsEnabled = !commentsEnabled },
            )

            Spacer(Modifier.height(20.dp))
            // Agreement gate.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { agreed = !agreed },
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(
                            if (agreed) NexusAccent else Color.Transparent,
                            RoundedCornerShape(6.dp),
                        )
                        .border(
                            1.5.dp,
                            if (agreed) NexusAccent else NexusStroke,
                            RoundedCornerShape(6.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (agreed) {
                        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "Saya menyatakan konten ini milik saya dan mematuhi pedoman komunitas Syntra.",
                    color = NexusTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        // Post button.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        brush = if (canPost) {
                            Brush.horizontalGradient(listOf(NexusAccentSoft, NexusAccent))
                        } else {
                            Brush.horizontalGradient(listOf(NexusSurfaceElevated, NexusSurfaceElevated))
                        },
                        shape = RoundedCornerShape(26.dp),
                    )
                    .clickable(
                        enabled = canPost,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onPost(buildCaption(title, description, tags), visibility, commentsEnabled) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Posting Reel",
                    color = if (canPost) Color.White else NexusTextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Folds title + description + #tags into the single caption the backend stores. */
private fun buildCaption(title: String, description: String, tags: String): String = buildString {
    if (title.isNotBlank()) append(title.trim())
    if (description.isNotBlank()) {
        if (isNotEmpty()) append("\n\n")
        append(description.trim())
    }
    val hashtags = tags.split(Regex("[\\s,]+"))
        .filter { it.isNotBlank() }
        .map { "#" + it.trimStart('#') }
    if (hashtags.isNotEmpty()) {
        if (isNotEmpty()) append("\n\n")
        append(hashtags.joinToString(" "))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = NexusTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minHeight: androidx.compose.ui.unit.Dp = 50.dp,
    singleLine: Boolean = true,
    leading: ImageVector? = null,
) {
    SectionLabel(label)
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(minHeight)
            .background(NexusSurface, RoundedCornerShape(14.dp))
            .border(1.dp, NexusStroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
    ) {
        if (leading != null) {
            Icon(leading, null, tint = NexusTextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, color = NexusTextSecondary, fontSize = 14.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = TextStyle(color = NexusTextPrimary, fontSize = 14.sp, lineHeight = 20.sp),
                cursorBrush = SolidColor(NexusAccentSoft),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AudienceChip(
    label: String,
    icon: ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .background(
                if (active) NexusAccent.copy(alpha = 0.16f) else NexusSurface,
                RoundedCornerShape(14.dp),
            )
            .border(
                1.dp,
                if (active) NexusAccent else NexusStroke,
                RoundedCornerShape(14.dp),
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            null,
            tint = if (active) NexusAccentSoft else NexusTextSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = if (active) NexusTextPrimary else NexusTextSecondary,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onToggle,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = NexusTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = NexusTextSecondary, fontSize = 12.sp)
        }
        // Compact custom switch (icons over text, on-brand).
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(28.dp)
                .background(
                    if (checked) NexusAccent else NexusSurfaceElevated,
                    RoundedCornerShape(50),
                )
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(Color.White, CircleShape),
            )
        }
    }
}
