package dev.aezochka.glimpse.ui

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.aezochka.glimpse.CrashLog
import dev.aezochka.glimpse.GalleryViewModel
import dev.aezochka.glimpse.MediaItem
import dev.aezochka.m3e.M3ESegmentedButtonRow
import dev.aezochka.m3e.m3ePopIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.util.Size
import kotlin.math.roundToInt

/** Конфиг режима «выбрать файлы для другого приложения». */
data class PickConfig(
    val allowMultiple: Boolean,
    val onDone: (List<MediaItem>) -> Unit,
)

data class Album(val name: String, val items: List<MediaItem>)

/** Состояние нижнего бара — переключается анимированно. */
private enum class BarMode { TABS, SELECT, PICK, ALBUM }

sealed interface Screen {
    data object Gallery : Screen
    data class Viewer(val list: List<MediaItem>, val index: Int) : Screen
    data class Editor(val item: MediaItem) : Screen

    fun depth(): Int = when (this) {
        is Gallery -> 0
        is Viewer -> 1
        is Editor -> 2
    }
}

@Composable
fun GalleryRoot(vm: GalleryViewModel = viewModel(factory = GalleryViewModel.Factory)) {
    val media by vm.media.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.Gallery) }
    val context = LocalContext.current

    // если прошлый запуск упал — можно забрать стектрейс одним тапом
    var crashFile by remember { mutableStateOf<java.io.File?>(null) }
    LaunchedEffect(Unit) { crashFile = CrashLog.latest(context) }

    BackHandler(enabled = screen != Screen.Gallery) { screen = Screen.Gallery }

    Box(Modifier.fillMaxSize()) {
        // Плавные переходы между экранами: слайд + фейд по направлению
        AnimatedContent(
            targetState = screen,
            label = "screens",
            transitionSpec = {
                val dir = if (targetState.depth() >= initialState.depth()) 1 else -1
                // большие поверхности едут tween'ом: спринг на них даёт рывок в конце
                (slideInHorizontally(Motion.screenIn()) { dir * it / 4 } +
                    fadeIn(Motion.screenIn()) +
                    scaleIn(Motion.screenIn(), initialScale = 0.98f)) togetherWith
                    (slideOutHorizontally(Motion.screenOut()) { -dir * it / 6 } +
                        fadeOut(Motion.screenOut()))
            },
        ) { s ->
            when (s) {
                is Screen.Gallery -> GalleryScreen(
                    media = media,
                    onOpen = { list, idx -> screen = Screen.Viewer(list, idx) },
                )
                is Screen.Viewer -> ViewerScreen(
                    items = s.list,
                    startIndex = s.index,
                    onBack = { screen = Screen.Gallery },
                    onEdit = { screen = Screen.Editor(it) },
                )
                is Screen.Editor -> EditorScreen(
                    item = s.item,
                    onBack = { screen = Screen.Gallery },
                    onSaved = { screen = Screen.Gallery },
                )
            }
        }

        val pendingCrash = crashFile
        if (pendingCrash != null && screen == Screen.Gallery) {
            CrashBar(
                onCopy = {
                    CrashLog.copy(context, CrashLog.text(pendingCrash))
                    CrashLog.clear(context)
                    crashFile = null
                },
                onClose = { crashFile = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 92.dp),
            )
        }
    }
}

/** Плашка «прошлый запуск упал»: забрать лог в буфер обмена. */
@Composable
private fun CrashBar(onCopy: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(24.dp), clip = false)
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.errorContainer)
            .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Прошлый запуск упал",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onCopy) {
            Text("Лог", color = scheme.onErrorContainer, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, "Скрыть", tint = scheme.onErrorContainer)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GalleryScreen(
    media: List<MediaItem>,
    onOpen: ((List<MediaItem>, Int) -> Unit)? = null,
    pickConfig: PickConfig? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var openAlbum by rememberSaveable { mutableStateOf<String?>(null) }
    var mediaFilter by rememberSaveable { mutableIntStateOf(0) }
    val pickMode = pickConfig != null

    // мультивыбор в обычном режиме
    var selectionMode by remember { mutableStateOf(false) }
    val selected: SnapshotStateMap<String, Boolean> = remember { mutableStateMapOf() }
    val selectedCount = selected.values.count { it }

    val filteredMedia = remember(media, mediaFilter) {
        when (mediaFilter) {
            1 -> media.filterNot { it.isVideo }
            2 -> media.filter { it.isVideo }
            else -> media
        }
    }
    val albums = remember(filteredMedia) {
        filteredMedia.groupBy { it.bucket.ifBlank { "Без альбома" } }
            .map { (name, list) -> Album(name, list) }
            .sortedByDescending { it.items.firstOrNull()?.date ?: 0L }
    }
    val items = remember(filteredMedia, openAlbum) {
        if (openAlbum != null) filteredMedia.filter { it.bucket.ifBlank { "Без альбома" } == openAlbum }
        else filteredMedia
    }
    // счётчики шапки: O(n) по всей библиотеке, поэтому считаем один раз,
    // а не на каждой перерисовке (иначе прокрутка большой галереи подтормаживает)
    val photoCount = remember(items, media, openAlbum) {
        if (openAlbum != null) items.count { !it.isVideo } else media.count { !it.isVideo }
    }
    val videoCount = remember(items, media, openAlbum) {
        if (openAlbum != null) items.count { it.isVideo } else media.count { it.isVideo }
    }

    fun toggle(item: MediaItem) {
        val k = item.stableKey()
        if (selected[k] == true) selected.remove(k) else selected[k] = true
        // 0 выделений — выходим из режима выбора сами
        if (pickConfig == null && selectionMode && selected.isEmpty()) {
            selectionMode = false
        }
    }

    // ---- камера (Android 10+) ----
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = camUri
        camUri = null
        if (uri != null && ok) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    context.contentResolver.update(uri, values, null, null)
                }
            }
        }
        if (uri != null && !ok) {
            scope.launch(Dispatchers.IO) { runCatching { context.contentResolver.delete(uri, null, null) } }
        }
        // успешный снимок подхватит ContentObserver, IS_PENDING снимет вставка камеры
    }

    fun takePhoto() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(context, "Камера доступна с Android 10", Toast.LENGTH_SHORT).show()
            return
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            camUri = uri
            runCatching { camLauncher.launch(uri) }.onFailure {
                camUri = null
                scope.launch(Dispatchers.IO) {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
                Toast.makeText(context, "Не удалось открыть камеру", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Не удалось подготовить фото", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- удаление с системным диалогом на Android 11+ ----
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            selectionMode = false
            selected.clear()
        }
    }

    fun deleteSelected() {
        val sel = items.filter { selected[it.stableKey()] == true }
        if (sel.isEmpty()) return
        val uris = sel.map { it.uri }
        scope.launch(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pi = MediaStore.createDeleteRequest(context.contentResolver, uris)
                    withContext(Dispatchers.Main) {
                        deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    }
                } else {
                    uris.forEach { context.contentResolver.delete(it, null, null) }
                    withContext(Dispatchers.Main) { selectionMode = false; selected.clear() }
                }
            } catch (e: Exception) {
                val rse = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) e else null
                if (rse != null) {
                    withContext(Dispatchers.Main) {
                        deleteLauncher.launch(
                            IntentSenderRequest.Builder(rse.userAction.actionIntent.intentSender).build()
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Не удалось удалить", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun shareSelected() {
        val sel = items.filter { selected[it.stableKey()] == true }
        if (sel.isEmpty()) return
        val type = when {
            sel.all { !it.isVideo } -> "image/*"
            sel.all { it.isVideo } -> "video/*"
            else -> "*/*"
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            this.type = type
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(sel.map { it.uri }))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
        selectionMode = false
        selected.clear()
    }

    fun exitSelection() {
        selectionMode = false
        selected.clear()
    }

    BackHandler(enabled = selectionMode) { exitSelection() }

    // общий grid-state: скролл НЕ сбрасывается при входе/выходе из выбора
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    // вкладки листаются свайпом
    val tabPager = androidx.compose.foundation.pager.rememberPagerState(
    pageCount = { if (openAlbum != null || pickMode) 1 else 2 },
)

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.fillMaxSize()) {
                // ---- шапка: герой-блок с M3E-формами ----
                GalleryHeader(
                    title = when {
                        selectionMode -> "Выбрано: $selectedCount"
                        openAlbum != null -> openAlbum.orEmpty()
                        pickMode -> "Glimpse"
                        else -> "Галерея"
                    },
                    photos = photoCount,
                    videos = videoCount,
                    selectionMode = selectionMode,
                    selectedCount = selectedCount,
                    pickMode = pickMode,
                    gridState = gridState,
                    showCamera = openAlbum == null && !pickMode && !selectionMode,
                    onCamera = ::takePhoto,
                    onExitSelection = ::exitSelection,
                )

                // ---- фильтр типа медиа — настоящий M3E segmented button ----
                if (openAlbum == null && !pickMode && !selectionMode) {
                    M3ESegmentedButtonRow(
                        labels = listOf("Все", "Фото", "Видео"),
                        selectedIndex = mediaFilter,
                        onSelect = { index ->
                            mediaFilter = index
                            scope.launch { gridState.animateScrollToItem(0) }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }

                // ---- контент: одна сетка на все режимы, вкладки листаются свайпом ----
                Box(Modifier.weight(1f)) {
                    when {
                        media.isEmpty() -> EmptyState("Пока пусто")
                        items.isEmpty() -> EmptyState(
                            when {
                                openAlbum != null -> "В альбоме пока пусто"
                                mediaFilter == 1 -> "Фото не найдены"
                                mediaFilter == 2 -> "Видео не найдены"
                                else -> "Пока пусто"
                            },
                        )
                        else -> androidx.compose.foundation.pager.HorizontalPager(
                            state = tabPager,
                            modifier = Modifier.fillMaxSize().testTag("galleryPager"),
                        ) { page ->
                            if (page == 0) {
                                // одна и та же сетка во всех режимах: вход/выход из выбора
                                // больше не пересоздаёт список (раньше был заметный прыжок)
                                PhotosGrid(
                                    items = items,
                                    pickConfig = pickConfig,
                                    selectionMode = selectionMode,
                                    selected = selected,
                                    gridState = gridState,
                                    onToggle = ::toggle,
                                    onOpen = onOpen,
                                    onEnterSelection = { item ->
                                        selectionMode = true
                                        selected[item.stableKey()] = true
                                    },
                                )
                            } else {
                                AlbumsGrid(albums) { album ->
                                    // альбом открывается на первой вкладке: иначе пейджер
                                    // остаётся на «Альбомах», которых в этом режиме уже нет
                                    openAlbum = album.name
                                    scope.launch { tabPager.scrollToPage(0) }
                                }
                            }
                        }
                    }
                }
            }

            // ---- нижние оверлеи: смена состояния бара анимируется ----
            val barMode = when {
                pickMode -> BarMode.PICK
                selectionMode -> BarMode.SELECT
                openAlbum != null -> BarMode.ALBUM
                else -> BarMode.TABS
            }
            AnimatedContent(
                targetState = barMode,
                label = "bar",
                modifier = Modifier.align(Alignment.BottomCenter),
                transitionSpec = {
                    (fadeIn(Motion.screenIn()) + slideInVertically(Motion.screenIn()) { it / 2 }) togetherWith
                        (fadeOut(Motion.screenOut()) + slideOutVertically(Motion.screenOut()) { it / 3 })
                },
            ) { bar ->
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = if (bar == BarMode.ALBUM) Alignment.BottomStart else Alignment.BottomCenter,
                ) {
                    when (bar) {
                BarMode.PICK -> PickBar(
                    count = selectedCount,
                    allowMultiple = pickConfig!!.allowMultiple,
                    onConfirm = {
                        val sel = items.filter { selected[it.stableKey()] == true }
                        pickConfig.onDone(sel)
                    },
                    onClear = { selected.clear() },
                    modifier = Modifier,
                )
                        BarMode.SELECT -> SelectBar(
                            count = selectedCount,
                            onShare = ::shareSelected,
                            onDelete = ::deleteSelected,
                            modifier = Modifier,
                        )
                        BarMode.ALBUM -> BackAlbumBar(onBack = { openAlbum = null }, modifier = Modifier)
                        BarMode.TABS -> TabsBar(
                            tab = tabPager.settledPage,
                            onSelect = { t ->
                                scope.launch {
                                    // тап по активной вкладке — наверх (привычка из Photos)
                                    if (t == tabPager.settledPage) gridState.animateScrollToItem(0)
                                    else tabPager.animateScrollToPage(t)
                                }
                            },
                            modifier = Modifier,
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================
// Шапка: цветная подложка + M3E-формы, гаснут при скролле
// =====================================================================

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GalleryHeader(
    title: String,
    photos: Int,
    videos: Int,
    selectionMode: Boolean,
    selectedCount: Int,
    pickMode: Boolean,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    showCamera: Boolean,
    onCamera: () -> Unit,
    onExitSelection: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val flower = MaterialShapes.Flower.toShape()
    val cookie = MaterialShapes.Cookie12Sided.toShape()
    val pill = MaterialShapes.Pill.toShape()
    // читаем скролл здесь, а не уровнем выше: перерисовывается только шапка
    val collapsed = gridState.firstVisibleItemIndex > 0
    // акцент тает, когда сетка уезжает под шапку
    val accent by animateFloatAsState(
        targetValue = if (collapsed) 0f else 1f,
        animationSpec = Motion.effects(),
        label = "hdrAccent",
    )
    Box(Modifier.fillMaxWidth().clipToBounds()) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = accent }
                .background(
                    Brush.verticalGradient(
                        0f to scheme.primaryContainer.copy(alpha = 0.45f),
                        0.7f to scheme.primaryContainer.copy(alpha = 0.08f),
                        1f to scheme.background,
                    )
                ),
        )
        // декор живёт в matchParentSize-слое: он не растягивает шапку и не лезет в сетку
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-56).dp, y = (-48).dp)
                    .size(136.dp)
                    .graphicsLayer { rotationZ = 18f; alpha = 0.5f * accent }
                    .clip(flower)
                    .background(scheme.primary.copy(alpha = 0.14f)),
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 44.dp, y = 34.dp)
                    .size(96.dp)
                    .graphicsLayer { rotationZ = -14f; alpha = 0.35f * accent }
                    .clip(cookie)
                    .background(scheme.tertiary.copy(alpha = 0.10f)),
            )
        }

        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                when {
                    showCamera -> CameraButton(onClick = onCamera)
                    selectionMode -> Box(
                        Modifier
                            .pillPress(onExitSelection)
                            .size(48.dp)
                            .clip(pill)
                            .background(scheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Close, "Выйти из выбора", tint = scheme.onSurfaceVariant)
                    }
                }
            }
            if (!selectionMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                ) {
                    if (pickMode) {
                        StatChip(pill, "…", "Выберите файлы", false)
                    } else {
                        Text(
                            buildList {
                                if (photos > 0) add("${formatCount(photos)} фото")
                                if (videos > 0) add("${formatCount(videos)} видео")
                            }.joinToString(" · ").ifEmpty { "пока пусто" },
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================
// Сетка фото
// =====================================================================

@Composable
private fun PhotosGrid(
    items: List<MediaItem>,
    pickConfig: PickConfig?,
    selectionMode: Boolean,
    selected: SnapshotStateMap<String, Boolean>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onToggle: (MediaItem) -> Unit,
    onOpen: ((List<MediaItem>, Int) -> Unit)?,
    onEnterSelection: (MediaItem) -> Unit,
) {
    val sections = remember(items) {
        items.groupBy { dayKey(it.date) }
    }
    // индекс элемента в общем списке — открытие вьювера без O(n) поиска
    val indexByKey = remember(items) {
        items.withIndex().associate { (i, m) -> m.stableKey() to i }
    }
    // входная волна плиток играет один раз: после 700мс модификатор снимается,
    // иначе он повторяется при каждом возврате к сетке и выглядит как дёрганье
    var armed by remember { mutableStateOf(true) }
    LaunchedEffect(items) {
        armed = true
        kotlinx.coroutines.delay(700)
        armed = false
    }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(start = 3.dp, end = 3.dp, top = 2.dp, bottom = 116.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        sections.forEach { (day, sectionItems) ->
            item(key = "h$day", span = { GridItemSpan(maxLineSpan) }) {
                DayHeader(dayLabel(day), sectionItems.size)
            }
            itemsIndexed(sectionItems, key = { _, it -> it.stableKey() }) { sectionIndex, item ->
                val isSel = (pickConfig != null || selectionMode) && selected[item.stableKey()] == true
                PhotoCell(
                    item = item,
                    pickMode = pickConfig != null || selectionMode,
                    selected = isSel,
                    onClick = {
                        if (pickConfig != null) {
                            if (pickConfig.allowMultiple) onToggle(item)
                            else pickConfig.onDone(listOf(item))
                        } else if (selectionMode) {
                            onToggle(item)
                        } else {
                            onOpen?.invoke(items, indexByKey[item.stableKey()] ?: 0)
                        }
                    },
                    onLongClick = {
                        if (pickConfig == null) onEnterSelection(item)
                    },
                    modifier = Modifier
                        .animateItem()
                        .then(
                            if (armed) Modifier.m3ePopIn(index = sectionIndex.coerceAtMost(8)) else Modifier
                        ),
                )
            }
        }
    }
}

@Composable
private fun DayHeader(label: String, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 19.dp, end = 19.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PhotoCell(
    item: MediaItem,
    pickMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val checkShape = MaterialShapes.Cookie9Sided.toShape()
    val tileShape = RoundedCornerShape(14.dp)
    // выделение: плитка чуть «садится» — родной M3E-отклик вместо жирной рамки
    val lift by animateFloatAsState(if (selected) 0.90f else 1f, Motion.spatialFast(), label = "cellSel")
    Box(
        modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .graphicsLayer { scaleX = lift; scaleY = lift }
            .clip(tileShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .testTag("cell")
            .cellPress(onClick, onLongClick, corner = 14.dp, cornerPressed = 20.dp),
    ) {
        if (item.isVideo) {
            val thumb by rememberVideoThumb(item)
            if (thumb != null) {
                Image(
                    bitmap = thumb!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // мягкая подложка под бейджи, чтобы длительность читалась на любом кадре
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.45f),
                    )
                )
            )
            Box(
                Modifier.align(Alignment.Center)
                    .size(38.dp)
                    .background(Color.Black.copy(alpha = 0.32f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Text(
                durationLabel(item.durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(7.dp),
            )
        } else {
            val req = remember(item.id) {
                ImageRequest.Builder(context)
                    .data(item.uri)
                    .size(300)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = req,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (pickMode) {
            if (selected) {
                Box(
                    Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
                )
                Box(
                    Modifier.fillMaxSize()
                        .border(2.dp, MaterialTheme.colorScheme.primary, tileShape)
                )
            }
            Box(
                Modifier.align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(26.dp)
                    .clip(checkShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.28f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(visible = selected) {
                    Icon(
                        Icons.Rounded.Check, null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun rememberVideoThumb(item: MediaItem): androidx.compose.runtime.State<Bitmap?> {
    val context = LocalContext.current
    return androidx.compose.runtime.produceState<Bitmap?>(initialValue = null, item.uri) {
        value = withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    context.contentResolver.loadThumbnail(item.uri, Size(256, 256), null)
                } catch (e: Exception) {
                    null
                }
            } else null
        }
    }
}

// =====================================================================
// Альбомы
// =====================================================================

@Composable
private fun AlbumsGrid(albums: List<Album>, onOpen: (Album) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 116.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(albums, key = { _, a -> a.name }) { _, album ->
            Column(
                Modifier
                    .padding(6.dp)
                    .cellPress(onClick = { onOpen(album) }, corner = 20.dp, cornerPressed = 26.dp)
                    .animateItem(),
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    val cover = album.items.firstOrNull()
                    if (cover != null) {
                        val context = LocalContext.current
                        if (cover.isVideo) {
                            val thumb by rememberVideoThumb(cover)
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb!!.asImageBitmap(), null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        } else {
                            val req = remember(cover.id) {
                                ImageRequest.Builder(context).data(cover.uri).size(400).crossfade(true).build()
                            }
                            AsyncImage(
                                model = req, null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                Text(
                    album.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp, start = 6.dp),
                )
                Text(
                    "${album.items.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

// =====================================================================
// Нижние бары
// =====================================================================

/** Пилюля вкладок: парит над контентом, тень, пружины. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TabsBar(tab: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val pill = MaterialShapes.Pill.toShape()
    Row(
        modifier
            .padding(bottom = 18.dp)
            .shadow(10.dp, pill, clip = false)
            .clip(pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(6.dp),
    ) {
        TabPill(active = tab == 0, icon = Icons.Rounded.PhotoLibrary, label = "Фото") { onSelect(0) }
        Spacer(Modifier.width(6.dp))
        TabPill(active = tab == 1, icon = Icons.Rounded.Collections, label = "Альбомы") { onSelect(1) }
    }
}

/**
 * Кнопка-пилюля: активная заливается цветом и раскрывает подпись.
 *
 * ВАЖНО: угол НЕ анимируется пружиной. Спринг с овершутом на пути 999dp→20dp
 * уходил в минус, а RoundedCornerShape(negative) кидает
 * IllegalArgumentException: «Corner size in Px can't be negative» — это и был
 * краш при свайпе вкладок галереи.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TabPill(active: Boolean, icon: ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val pill = MaterialShapes.Pill.toShape()
    val container by androidx.compose.animation.animateColorAsState(
        if (active) scheme.primary else Color.Transparent, Motion.effects(), label = "tabC",
    )
    val content by androidx.compose.animation.animateColorAsState(
        if (active) scheme.onPrimary else scheme.onSurfaceVariant, Motion.effects(), label = "tabT",
    )
    Row(
        Modifier
            .pillPress(onClick)
            .clip(pill)
            .background(container)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = content, modifier = Modifier.size(22.dp))
        AnimatedVisibility(visible = active) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * Панель выбора: две явные пилюли вместо ряда иконок.
 * Счётчик живёт в шапке («Выбрано: N») — дублировать его здесь не нужно.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectBar(
    count: Int,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val pill = MaterialShapes.Pill.toShape()
    val enabled = count > 0
    Row(
        modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .navigationBarsPadding()
            .shadow(10.dp, pill, clip = false)
            .clip(pill)
            .background(scheme.surfaceContainerHigh)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SelectAction("Поделиться", Icons.Rounded.Share, scheme.onSurface, enabled, onShare)
        Box(
            Modifier
                .width(1.dp)
                .height(24.dp)
                .background(scheme.outline.copy(alpha = 0.35f)),
        )
        SelectAction("Удалить", Icons.Rounded.Delete, scheme.error, enabled, onDelete)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectAction(
    label: String,
    icon: ImageVector,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val pill = MaterialShapes.Pill.toShape()
    val alpha = if (enabled) 1f else 0.38f
    Row(
        Modifier
            .then(if (enabled) Modifier.pillPress(onClick) else Modifier)
            .clip(pill)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = tint.copy(alpha = alpha), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = tint.copy(alpha = alpha),
        )
    }
}

@Composable
private fun PickBar(
    count: Int,
    allowMultiple: Boolean,
    onConfirm: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!allowMultiple) {
        Row(
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                "Нажмите на файл, чтобы выбрать",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClear, enabled = count > 0) { Text("Сброс") }
        Spacer(Modifier.weight(1f))
        Text(
            "Выбрано: $count",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(end = 12.dp),
        )
        Button(onClick = onConfirm, enabled = count > 0) {
            Icon(Icons.Rounded.Done, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Готово")
        }
    }
}

@Composable
private fun BackAlbumBar(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(start = 16.dp, bottom = 18.dp)
            .shadow(8.dp, RoundedCornerShape(999.dp))
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(8.dp),
    ) {
        TabPill(active = true, icon = Icons.AutoMirrored.Rounded.ArrowBack, label = "Все фото", onClick = onBack)
    }
}

// =====================================================================
// Шапка: чипы-счётчики в материальных формах
// =====================================================================

@Composable
private fun StatChip(
    shape: androidx.compose.ui.graphics.Shape,
    value: String,
    label: String,
    highlight: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(shape)
            .background(
                if (highlight) scheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (highlight) scheme.onPrimaryContainer else scheme.primary,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) scheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// =====================================================================
// Кнопка камеры — форма Cookie, пружинит и подкручивается
// =====================================================================

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CameraButton(onClick: () -> Unit) {
    val shape = MaterialShapes.Cookie12Sided.toShape()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, Motion.spatialFast(), label = "camScale")
    val rot by animateFloatAsState(if (pressed) 18f else 0f, Motion.spatialFast(), label = "camRot")
    Box(
        Modifier
            .size(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = rot }
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.PhotoCamera, "Камера",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(26.dp),
        )
    }
}


