package dev.aezochka.glimpse

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val date: Long,
    val bucket: String,
    val isVideo: Boolean,
    val durationMs: Long,
    val size: Long,
)

class MediaRepository(context: Context) {

    private val resolver: ContentResolver = context.contentResolver
    private val _media = MutableStateFlow<List<MediaItem>>(emptyList())
    val media: StateFlow<List<MediaItem>> = _media.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var started = false

    private val reloadRunnable = Runnable { reload() }

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            // авто-обновление: новые фото/видео появляются в сетке сами
            handler.removeCallbacks(reloadRunnable)
            handler.postDelayed(reloadRunnable, 400)
        }
    }

    fun ensureStarted() {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            resolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
            resolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
            reload()
        }
    }

    fun reload() {
        scope.launch {
            val items = queryImages() + queryVideos()
            _media.value = items.sortedByDescending { it.date }
        }
    }

    private fun queryImages(): List<MediaItem> = query(
        collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        isVideo = false,
        projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
        ),
    )

    private fun queryVideos(): List<MediaItem> = query(
        collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        isVideo = true,
        projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
        ),
    )

    private fun query(collection: Uri, isVideo: Boolean, projection: Array<String>): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        try {
            resolver.query(
                collection, projection, null, null, "DATE_ADDED DESC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val bucketCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val durCol = if (isVideo) c.getColumnIndex(MediaStore.Video.Media.DURATION) else -1
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    list.add(
                        MediaItem(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            name = c.getString(nameCol) ?: "",
                            date = c.getLong(dateCol),
                            bucket = c.getString(bucketCol) ?: "",
                            isVideo = isVideo,
                            durationMs = if (durCol >= 0) c.getLong(durCol) else 0L,
                            size = c.getLong(sizeCol),
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // нет доступа или пустое хранилище — вернём что есть
        }
        return list
    }

    companion object {
        fun insertPending(resolver: ContentResolver): Uri? {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
            return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }
    }
}
