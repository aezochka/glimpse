package dev.aezochka.glimpse

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.aezochka.glimpse.ui.GalleryScreen
import dev.aezochka.glimpse.ui.PermissionScreen
import dev.aezochka.glimpse.ui.PickConfig
import dev.aezochka.glimpse.ui.hasMediaAccess
import dev.aezochka.glimpse.ui.mediaPermissions
import dev.aezochka.glimpse.ui.theme.GlimpseTheme

/**
 * Пикер для других приложений: GET_CONTENT / PICK.
 * Любое приложение, где можно «прикрепить файл», увидит Glimpse в списке источников.
 */
class PickerActivity : ComponentActivity() {

    private val grantedState = mutableStateOf(false)

    private fun hasMediaPermission(): Boolean = hasMediaAccess(
        sdkInt = Build.VERSION.SDK_INT,
        legacyStorageGranted = granted(Manifest.permission.READ_EXTERNAL_STORAGE),
        imagesGranted = Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_IMAGES),
        videosGranted = Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_VIDEO),
        visualSelectedGranted = Build.VERSION.SDK_INT >= 34 &&
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
    )

    private fun granted(perm: String): Boolean =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        grantedState.value = hasMediaPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        grantedState.value = hasMediaPermission()
        val launcher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                grantedState.value = hasMediaPermission()
            }

        val allowMultiple = intent?.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false) ?: false
        val wanted = buildSet {
            intent?.type?.let { add(it) }
            intent?.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.let { addAll(it) }
        }
        val wantImages = wanted.isEmpty() || wanted.any { it.contains("image") || it == "*/*" }
        val wantVideos = wanted.isEmpty() || wanted.any { it.contains("video") || it == "*/*" }

        setContent {
            GlimpseTheme {
                if (!grantedState.value) {
                    PermissionScreen { launcher.launch(mediaPermissions()) }
                } else {
                    val vm: GalleryViewModel = viewModel(factory = GalleryViewModel.Factory)
                    val media by vm.media.collectAsState()
                    val items = remember(media, wantImages, wantVideos) {
                        media.filter { (it.isVideo && wantVideos) || (!it.isVideo && wantImages) }
                    }
                    GalleryScreen(
                        media = items,
                        pickConfig = PickConfig(allowMultiple) { sel -> finishWithResult(sel.map { it.uri }) },
                    )
                }
            }
        }
    }

    private fun finishWithResult(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val result = Intent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        result.data = uris.first()
        if (uris.size > 1) {
            val clip = ClipData.newRawUri("media", uris.first())
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            result.clipData = clip
        }
        setResult(RESULT_OK, result)
        finish()
    }
}
