package dev.aezochka.glimpse.ui

import android.Manifest
import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.aezochka.glimpse.MediaItem
import dev.aezochka.m3e.M3EMotion
import dev.aezochka.m3e.m3ePillPress
import dev.aezochka.m3e.m3ePress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Один словарь движений на всё приложение — делегат в общий M3E-порт (m3e-compose). */
object Motion {
    fun <T> spatial(): FiniteAnimationSpec<T> = M3EMotion.spatial()

    fun <T> spatialFast(): FiniteAnimationSpec<T> = M3EMotion.spatialFast()

    fun <T> effects(): FiniteAnimationSpec<T> = M3EMotion.effects()
}

/** Уникальный ключ элемента: id фото и видео могут совпадать! */
fun MediaItem.stableKey(): String = (if (isVideo) "v" else "i") + id

/** Плитка: пружинный спад + морф угла — из m3e-compose. */
fun Modifier.cellPress(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier =
    m3ePress(onClick = onClick, onLongClick = onLongClick)

/** Пилюля/кнопка: пружинный спад — из m3e-compose. */
fun Modifier.pillPress(onClick: () -> Unit): Modifier =
    m3ePillPress(onClick = onClick)

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.PhotoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 20.dp),
        )
        Text(
            "Нужен доступ к фото и видео",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            "Glimpse показывает галерею устройства. Данные не покидают телефон.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Button(onClick = onRequest) { Text("Разрешить доступ") }
    }
}

@Composable
fun EmptyState(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.PhotoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

// ---- форматы ----

private val ruLocale = Locale("ru")

fun formatDay(sec: Long): String =
    SimpleDateFormat("d MMMM yyyy", ruLocale).format(Date(sec * 1000L))

fun formatDateTime(sec: Long): String =
    SimpleDateFormat("d MMMM yyyy, HH:mm", ruLocale).format(Date(sec * 1000L))

/** «Сегодня» / «Вчера» / дата — живее, чем голая дата. */
fun dayLabel(day: Long): String {
    val today = System.currentTimeMillis() / 1000L / 86400L
    return when (today - day) {
        0L -> "Сегодня"
        1L -> "Вчера"
        2L -> "Позавчера"
        else -> formatDay(day * 86400L)
    }
}

fun dayKey(sec: Long): Long = sec / 86400L

fun durationLabel(ms: Long): String {
    val total = ms / 1000L
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}
