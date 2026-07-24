package com.example.syntra

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Gallery model + query
// ---------------------------------------------------------------------------

private data class GalleryItem(val uri: Uri, val isVideo: Boolean, val durationMs: Long)

private fun galleryPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun hasGalleryPermission(context: Context): Boolean =
    galleryPermissions().any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

/** Reads recent photos & videos from MediaStore (newest first). */
private suspend fun queryGallery(context: Context): List<GalleryItem> = withContext(Dispatchers.IO) {
    val items = mutableListOf<GalleryItem>()
    val collection = MediaStore.Files.getContentUri("external")
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
        MediaStore.Files.FileColumns.DURATION,
        MediaStore.Files.FileColumns.DATE_ADDED,
    )
    val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR " +
        "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
    val args = arrayOf(
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
    )
    val sort = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
    runCatching {
        context.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val durCol = c.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
            while (c.moveToNext() && items.size < 200) {
                val id = c.getLong(idCol)
                val isVideo = c.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val dur = if (durCol >= 0) c.getLong(durCol) else 0L
                items.add(GalleryItem(ContentUris.withAppendedId(collection, id), isVideo, dur))
            }
        }
    }
    items
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

// ---------------------------------------------------------------------------
// Styling
// ---------------------------------------------------------------------------

private val SheetGradient = listOf(Color(0xFF1B1430), Color(0xFF141021), Color(0xFF0E0E14))
private val TitleGradient = listOf(Color(0xFFB79CFF), Color(0xFF6E8BFF))
private val AccentGradient = listOf(Color(0xFF7C4DFF), Color(0xFF3B68F5))

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun AddStatusScreen(
    onClose: () -> Unit,
    onSelectUri: (Uri) -> Unit,
    onCaptureBitmap: (Bitmap) -> Unit,
    onTextStory: () -> Unit = {},
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current

    var hasPermission by remember { mutableStateOf(hasGalleryPermission(context)) }
    var media by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { hasPermission = hasGalleryPermission(context) }

    val systemPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) onSelectUri(uri) }

    // Asks for the camera permission first — capturing without it crashes.
    val cameraLauncher = rememberCameraCapture { bitmap -> onCaptureBitmap(bitmap) }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(galleryPermissions())
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission) media = queryGallery(context)
    }

    fun openSystemPicker() =
        systemPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(SheetGradient)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Grabber handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 10.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 5.dp)
                        .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(50)),
                )
            }

            // Title row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onClose,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, "Close", tint = NexusTextPrimary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Tambah status",
                        style = TextStyle(brush = Brush.horizontalGradient(TitleGradient)),
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Bagikan momenmu hari ini",
                        color = NexusTextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }

            // Action buttons row
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                actionButtons.forEach { action ->
                    item {
                        ActionButton(action) {
                            when (action.label) {
                                "Galeri" -> openSystemPicker()
                                "Teks" -> onTextStory()
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // "Terbaru" pill
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(50))
                        .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Terbaru", color = NexusTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = NexusTextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (!hasPermission) {
                PermissionPrompt(onGrant = { permissionLauncher.launch(galleryPermissions()) })
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { CameraTile(onClick = { cameraLauncher.launch() }) }
                    items(media) { gItem ->
                        GalleryTile(gItem, imageLoader) { onSelectUri(gItem.uri) }
                    }
                }
            }
        }

        // Floating "open files" button
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 20.dp, bottom = 20.dp)
                .size(58.dp)
                .background(Brush.verticalGradient(AccentGradient), RoundedCornerShape(20.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { openSystemPicker() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = "Open files",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

private data class StatusAction(val label: String, val icon: ImageVector, val gradient: List<Color>)

private val actionButtons = listOf(
    StatusAction("Teks", Icons.Filled.Edit, listOf(Color(0xFF7C4DFF), Color(0xFF448AFF))),
    StatusAction("Musik", Icons.Filled.MusicNote, listOf(Color(0xFFFF6A88), Color(0xFFFF9A8B))),
    StatusAction("Tata letak", Icons.Filled.Dashboard, listOf(Color(0xFF11998E), Color(0xFF38EF7D))),
    StatusAction("Suara", Icons.Filled.Mic, listOf(Color(0xFFFF512F), Color(0xFFDD2476))),
    StatusAction("Galeri", Icons.Filled.PhotoLibrary, listOf(Color(0xFF2196F3), Color(0xFF3B68F5))),
)

@Composable
private fun ActionButton(action: StatusAction, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(Brush.linearGradient(action.gradient), CircleShape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(action.icon, action.label, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(action.label, color = NexusTextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun CameraTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF241C3A), Color(0xFF15131E))))
            .border(1.dp, Color(0xFF7C4DFF).copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(Brush.linearGradient(AccentGradient), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = "Camera",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text("Kamera", color = NexusTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun GalleryTile(item: GalleryItem, imageLoader: ImageLoader, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF101014))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
    ) {
        AsyncImage(
            model = item.uri,
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.isVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
                if (item.durationMs > 0) {
                    Spacer(Modifier.width(2.dp))
                    Text(formatDuration(item.durationMs), color = Color.White, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Izinkan akses galeri untuk melihat foto & video di sini.",
            color = NexusTextSecondary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(AccentGradient), RoundedCornerShape(24.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onGrant,
                )
                .padding(horizontal = 22.dp, vertical = 11.dp),
        ) {
            Text("Izinkan akses", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
