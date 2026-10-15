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
import dev.aezochka.glimpse.ui.mediaPermissions
import dev.aezochka.glimpse.ui.theme.GlimpseTheme

class MainActivity : ComponentActivity() {

    private fun hasMediaPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            granted(Manifest.permission.READ_MEDIA_IMAGES) ||
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        } else {
            granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun granted(perm: String): Boolean =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val grantedState = androidx.compose.runtime.mutableStateOf(hasMediaPermission())
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
