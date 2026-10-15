package dev.aezochka.glimpse.ui

import android.Manifest
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.aezochka.glimpse.MediaItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.shape.RoundedCornerShape

/** Один словарь движений на всё приложение — M3E-спринги с овершутом. */
object Motion {
    fun <T> spatial(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)

    fun <T> spatialFast(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)

    fun <T> effects(): FiniteAnimationSpec<T> =
        tween(190, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
}

/** Уникальный ключ элемента: id фото и видео могут совпадать! */
fun MediaItem.stableKey(): String = (if (isVideo) "v" else "i") + id

/** Плитка: пружинный спад + морф угла — вместо стандартного ripple. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.cellPress(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = Motion.spatialFast(), label = "cellScale",
    )
    val corner by animateDpAsState(
        targetValue = if (pressed) 26.dp else 10.dp,
        animationSpec = Motion.spatialFast(), label = "cellCorner",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(corner))
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
}

/** Пилюля/кнопка: пружинный спад без ripple. */
fun Modifier.pillPress(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = Motion.spatialFast(), label = "pillScale",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

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
