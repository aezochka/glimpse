package dev.aezochka.glimpse.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
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
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import dev.aezochka.glimpse.MediaItem
import dev.aezochka.glimpse.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Полноэкранный вьювер: свайп между элементами, зум фото, свои M3E-контролы видео.
 *
 * Плеер создаётся ТОЛЬКО у активной страницы: при быстром свайпе ExoPlayer
 * с декодером на каждой предзагруженной странице выжирал память и кодеки —
 * второй источник «краша при свапе».
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    // свайп доводится пружиной ровно на одну страницу — без «пролётов» и рывков
    val fling = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
    )
    var chrome by remember { mutableStateOf(true) }
    BackHandler(onBack = onBack)

    val current = items.getOrNull(pagerState.currentPage)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            flingBehavior = fling,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize().testTag("viewerPager"),
        ) { page ->
            val item = items.getOrNull(page)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .graphicsLayer {
                        // «карусельный» переход: соседняя страница чуть мельче.
                        // Альфу не трогаем: полупрозрачный слайд поверх галереи
                        // читался как баг рендера.
                        val dist = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                            .absoluteValue.coerceIn(0f, 1f)
                        val vis = 1f - dist
                        val s = 0.94f + 0.06f * vis
                        scaleX = s
                        scaleY = s
                    },
            ) {
                when {
                    item == null -> Box(Modifier.fillMaxSize())
                    item.isVideo -> VideoPage(
                        item = item,
                        active = page == pagerState.currentPage,
                        onTap = { chrome = !chrome },
                        chrome = chrome,
                    )
                    else -> ZoomPage(item, onTap = { chrome = !chrome })
                }
            }
        }

        // ---- верх: скрим + название + счётчик ----
        AnimatedVisibility(
            visible = chrome,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.80f),
                            1f to Color.Transparent,
                        )
                    )
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", onBack)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        current?.name ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (current != null) {
                        Text(
                            formatDateTime(current.date),
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (items.size > 1) {
                    AnimatedContent(
                        targetState = pagerState.currentPage,
                        label = "counter",
                        transitionSpec = {
                            (fadeIn(Motion.screenIn()) + slideInVertically(Motion.screenIn()) { it / 2 }) togetherWith
                                fadeOut(Motion.screenOut())
                        },
                    ) { page -> CounterPill(page, items.size) }
                }
            }
        }

        // ---- низ: плавающая стеклянная пилюля действий ----
        AnimatedVisibility(
            visible = chrome && current != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.72f),
                        )
                    )
                    .navigationBarsPadding()
                    .padding(top = 34.dp, bottom = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier
                        .glassPill()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassAction("Поделиться", Icons.Rounded.Share) {
                        current?.let { shareItem(context, it) }
                    }
                    if (current != null && !current.isVideo) {
                        // тонкий разделитель: две зоны нажатия читаются как разные
                        Box(
                            Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(Color.White.copy(alpha = 0.18f)),
                        )
                        GlassAction("Изменить", Icons.Rounded.Edit) { onEdit(current) }
                    }
                }
            }
        }
    }
}

// =====================================================================
// Стеклянные элементы хрома
// =====================================================================

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Modifier.glassPill(): Modifier {
    val pill = MaterialShapes.Pill.toShape()
    return this
        .clip(pill)
        .background(Color.White.copy(alpha = 0.16f))
        .border(1.dp, Color.White.copy(alpha = 0.22f), pill)
}

@Composable
private fun GlassIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.13f))
            .pillPress(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GlassAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .pillPress(onClick)
            .clip(MaterialShapes.Pill.toShape())
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CounterPill(page: Int, total: Int) {
    Box(
        Modifier
            .clip(MaterialShapes.Pill.toShape())
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            "${page + 1} / $total",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
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

// =====================================================================
// Фото: пинч-зум, двойной тап, панорамирование с ограничением краёв
// =====================================================================

/**
 * Страница-фото: пинч-зум, двойной тап, панорамирование.
 * ВАЖНО: при scale == 1 жесты НЕ потребляются — иначе пейджер не листается.
 */
@Composable
private fun ZoomPage(item: MediaItem, onTap: () -> Unit) {
    val scope = rememberCoroutineScope()
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { box = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (zoom != 1f) {
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale <= 1f) Offset.Zero else clampOffset(offset, scale, box)
                            event.changes.forEach { it.consume() }
                        } else if (scale > 1f) {
                            offset = clampOffset(offset + pan, scale, box)
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        val target = if (scale > 1f) 1f else 2.6f
                        scope.launch {
                            animate(
                                initialValue = scale,
                                targetValue = target,
                                animationSpec = Motion.glide(),
                            ) { v, _ ->
                                scale = v
                                if (v <= 1.02f) offset = Offset.Zero
                            }
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

/** Не даём утащить увеличенное фото за края экрана. */
private fun clampOffset(offset: Offset, scale: Float, size: IntSize): Offset {
    if (size.width == 0 || size.height == 0) return offset
    val maxX = size.width * (scale - 1f) / 2f
    val maxY = size.height * (scale - 1f) / 2f
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

// =====================================================================
// Видео: плеер со СВОИМИ M3E-контролами
// =====================================================================

/**
 * Страница-видео: плеер создаётся при первом показе страницы и живёт до её
 * уничтожения. Экран — TextureView (res/layout/view_player.xml), чтобы альфа и
 * масштаб карусельного перехода применялись и к видео.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VideoPage(item: MediaItem, active: Boolean, onTap: () -> Unit, chrome: Boolean) {
    val context = LocalContext.current
    val playerState = remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }

    // декодер держит только активная страница
    LaunchedEffect(active) {
        if (active && playerState.value == null) {
            playerState.value = ExoPlayer.Builder(context).build().apply {
                setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
                prepare()
                playWhenReady = true
            }
        }
    }
    LaunchedEffect(active, playerState.value) {
        playerState.value?.let { if (active) it.play() else it.pause() }
    }
    DisposableEffect(Unit) {
        onDispose { playerState.value?.release() }
    }

    val player = playerState.value
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    durationMs = player?.duration?.coerceAtLeast(0L) ?: 0L
                }
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    LaunchedEffect(isPlaying, player) {
        while (isPlaying && player != null) {
            positionMs = player.currentPosition
            delay(200)
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
                (android.view.LayoutInflater.from(ctx)
                    .inflate(R.layout.view_player, null) as PlayerView).apply {
                    useController = false
                }
            },
            update = { view -> view.player = playerState.value },
            modifier = Modifier.fillMaxSize(),
        )

        if (!active && !isPlaying) {
            Box(
                Modifier.align(Alignment.Center)
                    .size(56.dp)
                    .background(Color.Black.copy(alpha = 0.32f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }

        if (active && durationMs == 0L) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.Center).size(30.dp),
            )
        }

        // M3E-контролы
        AnimatedVisibility(
            visible = chrome && active,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = 112.dp),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Column {
                val fraction = if (durationMs > 0) {
                    (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                } else 0f
                // сглаживаем опрос 200мс → плавная полоса; при скрабе — честное значение
                val smooth by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = tween(200, easing = LinearEasing),
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
                        playerState.value?.seekTo(positionMs)
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
                        val p = playerState.value ?: return@PlayPauseCookie
                        if (isPlaying) p.pause() else p.play()
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