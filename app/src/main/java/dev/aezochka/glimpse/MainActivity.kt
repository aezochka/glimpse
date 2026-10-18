package dev.aezochka.glimpse

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.aezochka.glimpse.ui.GalleryRoot
import dev.aezochka.glimpse.ui.PermissionScreen
import dev.aezochka.glimpse.ui.hasMediaAccess
import dev.aezochka.glimpse.ui.mediaPermissions
import dev.aezochka.glimpse.ui.theme.GlimpseTheme

class MainActivity : ComponentActivity() {

    private val grantedState = androidx.compose.runtime.mutableStateOf(false)

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

        setContent {
            GlimpseTheme {
                if (!grantedState.value) {
                    PermissionScreen { launcher.launch(mediaPermissions()) }
                } else {
                    GalleryRoot()
                }
            }
        }
    }
}
