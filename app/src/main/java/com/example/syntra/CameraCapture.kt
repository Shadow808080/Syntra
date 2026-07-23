package com.example.syntra

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Takes a photo, asking for the camera permission first.
 *
 * This indirection is not optional: because the app declares
 * `android.permission.CAMERA` in its manifest (video calls need it), Android
 * requires that permission to actually be *granted* before it will honour an
 * `ACTION_IMAGE_CAPTURE` intent — otherwise the capture throws a
 * SecurityException and takes the app down with it. Apps that don't declare the
 * permission at all are exempt, which is why this only started crashing once
 * calls were added.
 *
 * Returns a launcher: call [CameraCapture.launch] from a click handler.
 */
class CameraCapture internal constructor(
    private val onLaunch: () -> Unit,
) {
    fun launch() = onLaunch()
}

@Composable
fun rememberCameraCapture(onCaptured: (Bitmap) -> Unit): CameraCapture {
    val context = LocalContext.current
    // Set while we wait for the permission dialog, so a grant opens the camera
    // straight away instead of making the user tap twice.
    var pendingCapture by remember { mutableStateOf(false) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap -> if (bitmap != null) onCaptured(bitmap) }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            if (pendingCapture) runCatching { takePicture.launch(null) }
        } else {
            Toast.makeText(
                context,
                "Izin kamera diperlukan untuk mengambil foto.",
                Toast.LENGTH_LONG,
            ).show()
        }
        pendingCapture = false
    }

    return remember(takePicture, requestPermission) {
        CameraCapture {
            if (context.hasCameraPermission()) {
                // Still guarded: a device without a camera app would throw too.
                runCatching { takePicture.launch(null) }.onFailure {
                    Toast.makeText(context, "Kamera tidak tersedia.", Toast.LENGTH_SHORT).show()
                }
            } else {
                pendingCapture = true
                requestPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
