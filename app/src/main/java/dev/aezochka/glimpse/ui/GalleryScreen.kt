package dev.aezochka.glimpse.ui

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
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.aezochka.glimpse.GalleryViewModel
import dev.aezochka.glimpse.MediaItem
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

    BackHandler(enabled = screen != Screen.Gallery) { screen = Screen.Gallery }

    // Плавные переходы между экранами: слайд + фейд по направлению
    AnimatedContent(
        targetState = screen,
        label = "screens",
        transitionSpec = {
            val dir = if (targetState.depth() >= initialState.depth()) 1 else -1
            (slideInHorizontally(Motion.spatial()) { dir * it / 3 } + fadeIn(Motion.spatial())) togetherWith
                (slideOutHorizontally(Motion.spatial()) { -dir * it / 3 } + fadeOut(Motion.effects()))
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
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openAlbum by rememberSaveable { mutableStateOf<String?>(null) }
    val pickMode = pickConfig != null

    // мультивыбор в обычном режиме
    var selectionMode by remember { mutableStateOf(false) }
    val selected: SnapshotStateMap<String, Boolean> = remember { mutableStateMapOf() }
    val selectedCount = selected.values.count { it }

    val albums = remember(media) {
        media.groupBy { it.bucket.ifBlank { "Без альбома" } }
            .map { (name, list) -> Album(name, list) }
            .sortedByDescending { it.items.firstOrNull()?.date ?: 0L }
    }
    val items = remember(media, openAlbum) {
        if (openAlbum != null) media.filter { it.bucket.ifBlank { "Без альбома" } == openAlbum }
        else media
    }

    fun toggle(item: MediaItem) {
        val k = item.stableKey()
        if (selected[k] == true) selected.remove(k) else selected[k] = true
    }

    // ---- камера (Android 10+) ----
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = camUri
        camUri = null
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
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            camUri = uri
            camLauncher.launch(uri)
        }
    }

    // ---- удаление с системным диалогом на Android 11+ ----
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        selectionMode = false
        selected.clear()
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

    val flowerShape = MaterialShapes.Flower.toShape()
    val cookieShape = MaterialShapes.Cookie9Sided.toShape()
    val pillShape = MaterialShapes.Pill.toShape()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.fillMaxSize()) {
                // ---- шапка с M3E-блобом ----
                Box(Modifier.fillMaxWidth()) {
                    // декоративная цветочная форма, уходящая за край
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .offset(x = (-34).dp, y = (-56).dp)
                            .size(170.dp)
                            .graphicsLayer { rotationZ = 24f }
                            .clip(flowerShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                openAlbum ?: if (pickMode) "Glimpse" else "Галерея",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 6.dp),
                            ) {
                                when {
                                    selectionMode -> StatChip(
                                        shape = pillShape,
                                        value = "$selectedCount",
                                        label = "выбрано",
                                        highlight = true,
                                    )
                                    pickMode -> StatChip(pillShape, "…", "Выберите файлы", false)
                                    else -> {
                                        StatChip(cookieShape, "${items.count { !it.isVideo }}", "фото", false)
                                        StatChip(pillShape, "${items.count { it.isVideo }}", "видео", false)
                                    }
                                }
                            }
                        }
                        if (openAlbum == null && !pickMode && !selectionMode) {
                            CameraButton(onClick = ::takePhoto)
                        }
                    }
                }

                // ---- контент с анимацией переключения ----
                val viewKey = when {
                    selectionMode -> 3
                    openAlbum != null -> 2
                    tab == 1 && !pickMode -> 1
                    else -> 0
                }
                AnimatedContent(
                    targetState = viewKey,
                    label = "views",
                    transitionSpec = {
                        (fadeIn(Motion.spatial()) + scaleIn(Motion.spatial(), initialScale = 0.96f)) togetherWith
                            (fadeOut(Motion.effects()) + scaleOut(Motion.effects(), targetScale = 0.96f))
                    },
                ) { v ->
                    Box(Modifier.fillMaxSize()) {
                        when {
                            media.isEmpty() -> EmptyState("Пока пусто")
                            v == 1 -> AlbumsGrid(albums) { openAlbum = it.name }
                            else -> PhotosGrid(
                                items = items,
                                pickConfig = pickConfig,
                                selectionMode = selectionMode,
                                selected = selected,
                                onToggle = ::toggle,
                                onOpen = onOpen,
                                onEnterSelection = { item ->
                                    selectionMode = true
                                    selected[item.stableKey()] = true
                                },
                            )
                        }
                    }
                }
            }

            // ---- нижние оверлеи (плавают поверх контента) ----
            when {
                pickMode -> PickBar(
                    count = selectedCount,
                    allowMultiple = pickConfig!!.allowMultiple,
                    onConfirm = {
                        val sel = items.filter { selected[it.stableKey()] == true }
                        pickConfig.onDone(sel)
                    },
                    onClear = { selected.clear() },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                selectionMode -> SelectBar(
                    count = selectedCount,
                    onShare = ::shareSelected,
                    onDelete = ::deleteSelected,
                    onClose = ::exitSelection,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                openAlbum != null -> BackAlbumBar(
                    onBack = { openAlbum = null },
                    modifier = Modifier.align(Alignment.BottomStart),
                )
                else -> TabsBar(
                    tab = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
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
    onToggle: (MediaItem) -> Unit,
    onOpen: ((List<MediaItem>, Int) -> Unit)?,
    onEnterSelection: (MediaItem) -> Unit,
) {
    val sections = remember(items) {
        items.groupBy { dayKey(it.date) }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        contentPadding = PaddingValues(start = 1.5.dp, end = 1.5.dp, top = 1.5.dp, bottom = 112.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        sections.forEach { (day, sectionItems) ->
            item(key = "h$day", span = { GridItemSpan(maxLineSpan) }) {
                DayHeader(dayLabel(day), sectionItems.size)
            }
            itemsIndexed(sectionItems, key = { _, it -> it.stableKey() }) { _, item ->
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
                            onOpen?.invoke(items, items.indexOf(item).coerceAtLeast(0))
                        }
                    },
                    onLongClick = {
                        if (pickConfig == null) onEnterSelection(item)
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun DayHeader(label: String, count: Int) {
    Row(
        Modifier.padding(start = 18.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
    Box(modifier.aspectRatio(1f).cellPress(onClick, onLongClick)) {
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
            Box(
                Modifier.align(Alignment.Center)
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Text(
                durationLabel(item.durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
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
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                )
            }
            Box(
                Modifier.align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(26.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.35f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(visible = selected) {
                    Icon(
                        Icons.Rounded.Check, null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
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
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 112.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(albums, key = { _, a -> a.name }) { _, album ->
            Column(
                Modifier
                    .padding(6.dp)
                    .cellPress(onClick = { onOpen(album) })
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
@Composable
private fun TabsBar(tab: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(bottom = 18.dp)
            .shadow(8.dp, RoundedCornerShape(999.dp))
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(8.dp),
    ) {
        TabPill(active = tab == 0, icon = Icons.Rounded.PhotoLibrary, label = "Фото") { onSelect(0) }
        Spacer(Modifier.width(8.dp))
        TabPill(active = tab == 1, icon = Icons.Rounded.Collections, label = "Альбомы") { onSelect(1) }
    }
}

/** Кнопка-пилюля: активная растёт и расцветается — сигнатура M3E. */
@Composable
fun TabPill(active: Boolean, icon: ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container by androidx.compose.animation.animateColorAsState(
        if (active) scheme.primary else Color.Transparent, Motion.effects(), label = "tabC",
    )
    val corner by androidx.compose.animation.core.animateDpAsState(
        if (active) 20.dp else 999.dp, Motion.spatialFast(), label = "tabR",
    )
    Row(
        Modifier
            .pillPress(onClick)
            .clip(RoundedCornerShape(corner))
            .background(container)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = if (active) scheme.onPrimary else scheme.onSurfaceVariant)
        AnimatedVisibility(visible = active) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onPrimary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** Бар мультивыбора: анимированный счётчик, поделиться, удалить. */
@Composable
private fun SelectBar(
    count: Int,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .shadow(12.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, "Выйти из выбора")
        }
        AnimatedContent(
            targetState = count,
            label = "selCount",
            transitionSpec = {
                (scaleIn(Motion.spatial()) + fadeIn(Motion.spatial())) togetherWith
                    (scaleOut(Motion.effects()) + fadeOut(Motion.effects()))
            },
        ) { n ->
            Text(
                if (n == 0) "Ничего не выбрано" else "$n",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onShare, enabled = count > 0) {
            Icon(Icons.Rounded.Share, "Поделиться")
        }
        IconButton(onClick = onDelete, enabled = count > 0) {
            Icon(Icons.Rounded.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
        }
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


