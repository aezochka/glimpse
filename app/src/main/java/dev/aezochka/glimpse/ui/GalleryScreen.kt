package dev.aezochka.glimpse.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshot.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.withContext
import java.util.Size

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
}

@Composable
fun GalleryRoot(vm: GalleryViewModel = viewModel(factory = GalleryViewModel.Factory)) {
    val media by vm.media.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.Gallery) }

    LaunchedEffect(media) {
        // если открытый файл исчез из хранилища — не залипаем на мёртвом экране
        val cur = screen
        if (media.isNotEmpty()) {
            when (cur) {
                is Screen.Viewer -> if (cur.list.all { it.id !in media.map { m -> m.id } }) screen = Screen.Gallery
                is Screen.Editor -> if (cur.item.id !in media.map { m -> m.id }) screen = Screen.Gallery
                else -> {}
            }
        }
    }

    when (val s = screen) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    media: List<MediaItem>,
    onOpen: ((List<MediaItem>, Int) -> Unit)? = null,
    pickConfig: PickConfig? = null,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openAlbum by rememberSaveable { mutableStateOf<String?>(null) }
    val pickMode = pickConfig != null

    val albums = remember(media) {
        media.groupBy { it.bucket.ifBlank { "Без альбома" } }
            .map { (name, list) -> Album(name, list) }
            .sortedByDescending { it.items.firstOrNull()?.date ?: 0L }
    }
    val items = remember(media, openAlbum) {
        if (openAlbum != null) media.filter { it.bucket.ifBlank { "Без альбома" } == openAlbum }
        else media
    }
    val selected: SnapshotStateMap<Long, Boolean> = remember { mutableStateMapOf() }
    val selectedCount = selected.values.count { it }

    fun toggle(item: MediaItem) {
        if (selected[item.id] == true) selected.remove(item.id) else selected[item.id] = true
    }
    fun confirm() {
        val sel = items.filter { selected[it.id] == true }
        pickConfig?.onDone(sel)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            when {
                pickMode -> PickBar(
                    count = selectedCount,
                    allowMultiple = pickConfig!!.allowMultiple,
                    onConfirm = ::confirm,
                    onClear = { selected.clear() },
                )
                openAlbum != null -> BackAlbumBar(openAlbum ?: "") { openAlbum = null }
                else -> TabsBar(tab, onSelect = { tab = it })
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // шапка
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        openAlbum ?: "Галерея",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!pickMode) {
                        val imgC = items.count { !it.isVideo }
                        val vidC = items.count { it.isVideo }
                        Text(
                            "$imgC фото · $vidC видео",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "Выберите файлы",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            when {
                media.isEmpty() -> EmptyState("Пока пусто")
                openAlbum == null && tab == 1 && !pickMode -> AlbumsGrid(albums) { openAlbum = it.name }
                else -> PhotosGrid(items, pickConfig, selected, ::toggle, onOpen)
            }
        }
    }
}

@Composable
private fun PhotosGrid(
    items: List<MediaItem>,
    pickConfig: PickConfig?,
    selected: SnapshotStateMap<Long, Boolean>,
    onToggle: (MediaItem) -> Unit,
    onOpen: ((List<MediaItem>, Int) -> Unit)?,
) {
    val sections = remember(items) {
        items.groupBy { dayKey(it.date) }
            .map { (day, list) -> formatDay(day * 86400L) to list }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(1.5.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        var offset = 0
        sections.forEach { (label, sectionItems) ->
            item(key = "h_$label", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.padding(start = 18.dp, top = 14.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${sectionItems.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(sectionItems, key = { _, it -> it.id }) { idx, item ->
                val isSel = pickConfig != null && selected[item.id] == true
                PhotoCell(
                    item = item,
                    pickMode = pickConfig != null,
                    selected = isSel,
                    onClick = {
                        if (pickConfig != null) {
                            if (pickConfig.allowMultiple) onToggle(item)
                            else pickConfig.onDone(listOf(item))
                        } else {
                            onOpen?.invoke(items, offset + idx)
                        }
                    },
                )
            }
            offset += sectionItems.size
        }
    }
}

@Composable
fun PhotoCell(
    item: MediaItem,
    pickMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Box(modifier.aspectRatio(1f).cellPress(onClick)) {
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
                if (selected) {
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

@Composable
private fun AlbumsGrid(albums: List<Album>, onOpen: (Album) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(albums, key = { _, a -> a.name }) { _, album ->
            Column(Modifier.padding(6.dp).cellPress { onOpen(album) }) {
                Box(
                    Modifier.fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp))
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
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
                Text(
                    "${album.items.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TabsBar(tab: Int, onSelect: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(bottom = 14.dp), contentAlignment = Alignment.Center) {
        HorizontalFloatingToolbar(expanded = false) {
            TabPill(active = tab == 0, icon = Icons.Rounded.PhotoLibrary, label = "Фото") { onSelect(0) }
            Spacer(Modifier.width(10.dp))
            TabPill(active = tab == 1, icon = Icons.Rounded.Collections, label = "Альбомы") { onSelect(1) }
        }
    }
}

/** Кнопка-пилюля: активная растёт и расцветается — сигнатура M3E. */
@Composable
fun TabPill(active: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (active) scheme.primary else scheme.surfaceContainer, Motion.effects(), label = "tabC",
    )
    val corner by animateDpAsState(
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

@Composable
private fun PickBar(count: Int, allowMultiple: Boolean, onConfirm: () -> Unit, onClear: () -> Unit) {
    if (!allowMultiple) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
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
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
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
private fun BackAlbumBar(album: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Button(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Все фото")
        }
    }
}
