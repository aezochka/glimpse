package dev.aezochka.glimpse.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import dev.aezochka.glimpse.MediaItem
import kotlinx.coroutines.delay

@Composable
fun ViewerScreen(
    items: List<MediaItem>,
    startIndex: Int,
    onBack: () -> Unit,
    onEdit: (MediaItem) -> Unit,
) {
    val context = LocalContext.current
    val safeIndex = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeIndex, pageCount = { items.size })
    var chrome by remember { mutableStateOf(true) }
    BackHandler(onBack = onBack)

    val current = items.getOrNull(pagerState.currentPage)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items.getOrNull(page)
            if (item == null) {
                Box(Modifier.fillMaxSize())
            } else if (item.isVideo) {
                VideoPage(
                    item = item,
                    active = page == pagerState.currentPage,
                    onTap = { chrome = !chrome },
                    chrome = chrome,
                )
            } else {
                ZoomPage(item, onTap = { chrome = !chrome })
            }
        }

        AnimatedVisibility(
            visible = chrome,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        current?.name ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (current != null) {
                        Text(
                            formatDateTime(current.date),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
        }

        AnimatedVisibility(
            visible = chrome,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(vertical = 6.dp, horizontal = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            ) {
                if (current != null) {
                    ActionChip("Поделиться", Icons.Rounded.Share) { shareItem(context, current) }
                    if (!current.isVideo) {
                        ActionChip("Изменить", Icons.Rounded.Edit) { onEdit(current) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .pillPress(onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = Color.White)
        Spacer(Modifier.padding(end = 8.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

private fun shareItem(context: android.content.Context, item: MediaItem) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = if (item.isVideo) "video/*" else "image/*"
        putExtra(Intent.EXTRA_STREAM, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

/**
 * Страница-фото: пинч-зум, двойной тап, панорамирование.
 * ВАЖНО: при scale == 1 жесты НЕ потребляются — иначе пейджер не листается.
 */
@Composable
private fun ZoomPage(item: MediaItem, onTap: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (zoom != 1f) {
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            if (scale <= 1f) offset = Offset.Zero
                            event.changes.forEach { it.consume() }
                        } else if (scale > 1f) {
                            offset += pan
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.6f
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

/**
 * Страница-видео: плеер со СВОИМИ M3E-контролами
 * (cookie-кнопка play/pause, тонкий белый слайдер).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VideoPage(item: MediaItem, active: Boolean, onTap: () -> Unit, chrome: Boolean) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = active
        }
    }
    LaunchedEffect(active) {
        if (active) player.play() else player.pause()
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = player.currentPosition
            delay(250)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // M3E-контролы
        AnimatedVisibility(
            visible = chrome,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = 92.dp),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Column {
                val fraction = if (durationMs > 0) {
                    (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                } else 0f
                // сглаживаем опрос 100мс → плавная полоса; при скрабе — честное значение
                val smooth by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = tween(100, easing = LinearEasing),
                    label = "vidPos",
                )
                Slider(
                    value = if (scrubbing) fraction else smooth,
                    onValueChange = { f ->
                        if (durationMs > 0) {
                            scrubbing = true
                            positionMs = (f * durationMs).toLong()
                        }
                    },
                    onValueChangeFinished = {
                        scrubbing = false
                        player.seekTo(positionMs)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                    ),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        durationLabel(positionMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                    Spacer(Modifier.weight(1f))
                    PlayPauseCookie(playing = isPlaying) {
                        if (isPlaying) player.pause() else player.play()
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        durationLabel(durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/** Cookie-кнопка play/pause: вращается и пружинит при переключении. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayPauseCookie(playing: Boolean, onToggle: () -> Unit) {
    val shape = MaterialShapes.Cookie9Sided.toShape()
    val rot by animateFloatAsState(if (playing) 40f else 0f, Motion.spatial(), label = "ppRot")
    val scale by animateFloatAsState(if (playing) 0.92f else 1f, Motion.spatialFast(), label = "ppScale")
    Box(
        Modifier
            .size(64.dp)
            .graphicsLayer { rotationZ = rot; scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(Color.White.copy(alpha = 0.16f))
            .pillPress(onToggle),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = playing,
            label = "ppIcon",
            transitionSpec = {
                (scaleIn(Motion.spatial()) + fadeIn(Motion.spatial())) togetherWith
                    (scaleOut(Motion.effects()) + fadeOut(Motion.effects()))
            },
        ) { p ->
            Icon(
                if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (p) "Пауза" else "Играть",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}
