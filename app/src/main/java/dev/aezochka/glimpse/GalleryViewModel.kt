package dev.aezochka.glimpse

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.StateFlow

class GalleryViewModel(application: Application) : ViewModel() {

    val media: StateFlow<List<MediaItem>> =
        (application as GlimpseApp).repository.also { it.ensureStarted() }.media

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                GalleryViewModel(app)
            }
        }
    }
}
