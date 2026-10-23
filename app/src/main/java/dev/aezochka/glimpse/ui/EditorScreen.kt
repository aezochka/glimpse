package dev.aezochka.glimpse.ui

import android.graphics.Bitmap
import android.graphics.RectF
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.aezochka.glimpse.MediaItem
import dev.aezochka.glimpse.editor.FilterPreset
import dev.aezochka.glimpse.editor.aspectCropRect
import dev.aezochka.glimpse.editor.bakeEdits
import dev.aezochka.glimpse.editor.decodeBitmap
import dev.aezochka.glimpse.editor.filterMatrix
import dev.aezochka.glimpse.editor.saveBitmap
import dev.aezochka.glimpse.editor.transformBitmap
import dev.aezochka.m3e.M3EButton
import dev.aezochka.m3e.M3EButtonSize
import dev.aezochka.m3e.M3EIconButton
import dev.aezochka.m3e.M3EIconButtonStyle
import dev.aezochka.m3e.M3ESlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// =====================================================================
// Состояние редактора
// =====================================================================

/** Открытая нижняя панель. null — панели нет, виден только тулбар. */
private enum class EditPanel { CROP, FILTERS, ADJUST }

/** Один шаг истории правок: сравнивается по значению, поэтому годится для undo/redo. */
private data class Edits(
    val rotation: Int = 0,
    val flip: Boolean = false,
    val filter: FilterPreset = FilterPreset.NONE,
    val brightness: Float = 0f,   // -0.5..0.5
    val contrast: Float = 1f,     // 0.5..1.5
    val saturation: Float = 1f,   // 0..2
    val crop: RectF? = null,      // нормализованный прямоугольник
    val aspect: String? = null,
) {
    val adjusted: Boolean get() = brightness != 0f || contrast != 1f || saturation != 1f
}

private val ASPECTS: List<Pair<String, Float?>> = listOf(
    "Свободно" to null,
    "1:1" to 1f,
    "4:3" to 4f / 3f,
    "16:9" to 16f / 9f,
    "9:16" to 9f / 16f,
)

// =====================================================================
// Экран
// =====================================================================

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditorScreen(item: MediaItem, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var panel by remember { mutableStateOf<EditPanel?>(null) }
    val history = remember { mutableStateListOf(Edits()) }
    var index by remember { mutableIntStateOf(0) }
    val edits = history[index]

    /** Новая ступень истории — всё, что можно отменить. */
    fun commit(next: Edits) {
        if (next == edits) return
        while (history.size > index + 1) history.removeAt(history.lastIndex)
        history.add(next)
        index = history.lastIndex
    }

    /** Живая правка текущей ступени (ползунки, перетаскивание рамки). */
    fun live(next: Edits) {
        history[index] = next
    }

    var base by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var saving by remember { mutableStateOf(false) }
    var comparing by remember { mutableStateOf(false) }
    var cropDraft by remember { mutableStateOf<RectF?>(null) }

    LaunchedEffect(item.uri) {
        base = withContext(Dispatchers.IO) { decodeBitmap(context.contentResolver, item.uri, 1600) }
    }
    LaunchedEffect(base, edits.rotation, edits.flip) {
        preview = base?.let { b ->
            withContext(Dispatchers.Default) { transformBitmap(b, edits.rotation, edits.flip) }
        }
    }
    // undo/redo и смена соотношения возвращают рамку к значению из истории
    LaunchedEffect(edits.crop) { cropDraft = edits.crop }
    LaunchedEffect(panel, preview) {
        if (panel == EditPanel.CROP && cropDraft == null) {
            preview?.let { live(edits.copy(crop = aspectCropRect(null, it.width, it.height))) }
        }
    }

    val matrix = remember(edits.filter, edits.brightness, edits.contrast, edits.saturation) {
        filterMatrix(edits.filter, edits.brightness, edits.contrast, edits.saturation)
    }
    val colorFilter = remember(matrix) { ColorFilter.colorMatrix(ColorMatrix(matrix)) }
    val activeCrop = cropDraft ?: edits.crop

    fun doSave() {
        if (saving || preview == null) return
        saving = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                bakeEdits(
                    context, item.uri, edits.rotation, edits.flip, activeCrop, edits.filter,
                    edits.brightness, edits.contrast, edits.saturation,
                )?.let { saveBitmap(context, it) }
            }
            saving = false
            if (saved != null) {
                Toast.makeText(context, "Сохранено в Pictures/Glimpse", Toast.LENGTH_SHORT).show()
                onSaved()
            } else {
                Toast.makeText(context, "Не удалось сохранить", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            PhotoStage(
                preview = preview,
                colorFilter = if (comparing) null else colorFilter,
                cropMode = panel == EditPanel.CROP,
                crop = activeCrop,
                onCropChange = { cropDraft = it },
                onCropEnd = { cropDraft?.let { c -> commit(edits.copy(crop = c)) } },
                onCompare = { down -> comparing = down },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            // панель инструмента: высота едет плавно, фото ужимается вместе с ней
            EditorPanel(
                panel = panel,
                preview = preview,
                edits = edits,
                onLive = ::live,
                onCommit = { next -> cropDraft = next.crop; commit(next) },
                onReset = { commit(edits.copy(brightness = 0f, contrast = 1f, saturation = 1f)) },
            )

            EditorToolbar(
                panel = panel,
                adjustActive = edits.adjusted,
                onPanel = { p -> panel = if (panel == p) null else p },
                onRotate = {
                    panel = null
                    cropDraft = null
                    commit(edits.copy(rotation = (edits.rotation + 90) % 360, crop = null, aspect = null))
                },
                onFlip = {
                    panel = null
                    cropDraft = null
                    commit(edits.copy(flip = !edits.flip, crop = null, aspect = null))
                },
            )
        }

        EditorTopBar(
            canUndo = index > 0,
            canRedo = index < history.lastIndex,
            canSave = preview != null && !saving,
            saving = saving,
            resetEnabled = edits.adjusted || edits.filter != FilterPreset.NONE,
            onBack = onBack,
            onUndo = {
                if (index > 0) {
                    index -= 1
                    cropDraft = history[index].crop
                }
            },
            onRedo = {
                if (index < history.lastIndex) {
                    index += 1
                    cropDraft = history[index].crop
                }
            },
            onSave = ::doSave,
            onReset = {
                cropDraft = edits.crop
                commit(edits.copy(brightness = 0f, contrast = 1f, saturation = 1f, filter = FilterPreset.NONE))
            },
        )
    }
}

// =====================================================================
// Сцена с фото
// =====================================================================

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PhotoStage(
    preview: Bitmap?,
    colorFilter: ColorFilter?,
    cropMode: Boolean,
    crop: RectF?,
    onCropChange: (RectF) -> Unit,
    onCropEnd: () -> Unit,
    onCompare: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier.onSizeChanged { boxSize = it }) {
        val bmp = preview
        if (bmp == null) {
            LoadingIndicator(Modifier.align(Alignment.Center).size(44.dp))
        } else {
            val disp = remember(bmp, boxSize) { fitRect(boxSize, bmp.width, bmp.height) }
            val density = LocalDensity.current
            Box(
                Modifier
                    .offset { IntOffset(disp.left.roundToInt(), disp.top.roundToInt()) }
                    .size(
                        with(density) { disp.width.toDp() },
                        with(density) { disp.height.toDp() },
                    )
                    .then(
                        if (!cropMode) {
                            // удержание — «до/после»
                            Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        onCompare(true)
                                        tryAwaitRelease()
                                        onCompare(false)
                                    },
                                )
                            }
                        } else Modifier
                    ),
            ) {
                AnimatedContent(
                    targetState = bmp,
                    label = "preview",
                    transitionSpec = {
                        (fadeIn(Motion.screenIn()) + scaleIn(Motion.glide(), initialScale = 0.97f)) togetherWith
                            fadeOut(Motion.screenOut())
                    },
                ) { b ->
                    Image(
                        bitmap = b.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        colorFilter = colorFilter,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (cropMode) {
                    crop?.let { c -> CropOverlay(rect = c, onChange = onCropChange, onChangeEnd = onCropEnd) }
                }
            }
        }
    }
}

// =====================================================================
// Верхняя плашка
// =====================================================================

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditorTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    canSave: Boolean,
    saving: Boolean,
    resetEnabled: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.82f),
                    1f to Color.Transparent,
                )
            )
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .pillPress(onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, "Закрыть", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(6.dp))
            if (resetEnabled) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .pillPress(onReset)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("Сброс", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.weight(1f))
            M3EIconButton(
                icon = Icons.Rounded.Undo,
                contentDescription = "Отменить",
                onClick = onUndo,
                style = M3EIconButtonStyle.Tonal,
                size = M3EButtonSize.Small,
                enabled = canUndo,
            )
            Spacer(Modifier.width(4.dp))
            M3EIconButton(
                icon = Icons.Rounded.Redo,
                contentDescription = "Вернуть",
                onClick = onRedo,
                style = M3EIconButtonStyle.Tonal,
                size = M3EButtonSize.Small,
                enabled = canRedo,
            )
            Spacer(Modifier.width(8.dp))
            M3EButton(onClick = onSave, enabled = canSave) {
                if (saving) {
                    LoadingIndicator(Modifier.size(20.dp))
                } else {
                    Text("Сохранить")
                }
            }
        }
    }
}

// =====================================================================
// Тулбар
// =====================================================================

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditorToolbar(
    panel: EditPanel?,
    adjustActive: Boolean,
    onPanel: (EditPanel) -> Unit,
    onRotate: () -> Unit,
    onFlip: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val pill = MaterialShapes.Pill.toShape()
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            Modifier
                .clip(pill)
                .background(scheme.surfaceContainerHigh.copy(alpha = 0.96f))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(Icons.Rounded.Crop, "Обрезка", panel == EditPanel.CROP) { onPanel(EditPanel.CROP) }
            ToolButton(Icons.Rounded.RotateRight, "Поворот", false, onClick = onRotate)
            ToolButton(Icons.Rounded.Flip, "Зеркало", false, onClick = onFlip)
            ToolButton(Icons.Rounded.AutoFixHigh, "Фильтры", panel == EditPanel.FILTERS) {
                onPanel(EditPanel.FILTERS)
            }
            ToolButton(Icons.Rounded.Tune, "Тон", panel == EditPanel.ADJUST || adjustActive) {
                onPanel(EditPanel.ADJUST)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val pill = MaterialShapes.Pill.toShape()
    val bg by animateColorAsState(
        if (active) scheme.primary else Color.Transparent, Motion.effects(), label = "toolBg",
    )
    val tint by animateColorAsState(
        if (active) scheme.onPrimary else scheme.onSurfaceVariant, Motion.effects(), label = "toolTint",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(58.dp)
            .pillPress(onClick)
            .clip(pill)
            .background(bg)
            .padding(vertical = 6.dp),
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// =====================================================================
// Панель инструмента
// =====================================================================

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditorPanel(
    panel: EditPanel?,
    preview: Bitmap?,
    edits: Edits,
    onLive: (Edits) -> Unit,
    onCommit: (Edits) -> Unit,
    onReset: () -> Unit,
) {
    val target = when (panel) {
        null -> 0.dp
        EditPanel.CROP -> 76.dp
        EditPanel.FILTERS -> 136.dp
        EditPanel.ADJUST -> 172.dp
    }
    val height by animateDpAsState(target, Motion.glide(), label = "panelH")
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clipToBounds(),
    ) {
        AnimatedContent(
            targetState = panel,
            label = "panel",
            transitionSpec = {
                (fadeIn(Motion.screenIn()) + slideInVertically(Motion.screenIn()) { it / 3 }) togetherWith
                    (fadeOut(Motion.screenOut()) + slideOutVertically(Motion.screenOut()) { -it / 4 })
            },
        ) { p ->
            when (p) {
                null -> Box(Modifier.fillMaxSize())
                EditPanel.CROP -> AspectRow(edits.aspect) { label, ratio ->
                    val rect = preview?.let { aspectCropRect(ratio, it.width, it.height) }
                    onCommit(edits.copy(crop = rect, aspect = label))
                }
                EditPanel.FILTERS -> FilterRow(preview, edits.filter) { preset ->
                    onCommit(edits.copy(filter = preset))
                }
                EditPanel.ADJUST -> AdjustPanel(edits, onLive, onReset)
            }
        }
    }
}

@Composable
private fun AspectRow(selected: String?, onPick: (String, Float?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(ASPECTS, key = { _, a -> a.first }) { _, aspect ->
            FilterChip(
                selected = (selected ?: "Свободно") == aspect.first,
                onClick = { onPick(aspect.first, aspect.second) },
                label = { Text(aspect.first) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilterRow(preview: Bitmap?, selected: FilterPreset, onPick: (FilterPreset) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(FilterPreset.entries, key = { _, p -> p.name }) { _, preset ->
            val active = selected == preset
            val scale by animateFloatAsState(if (active) 1.04f else 1f, Motion.glide(), label = "filterScale")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .pillPress { onPick(preset) }
                    .graphicsLayer { scaleX = scale; scaleY = scale },
            ) {
                Box(
                    Modifier
                        .size(width = 62.dp, height = 82.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = if (active) 3.dp else 1.dp,
                            color = if (active) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(16.dp),
                        ),
                ) {
                    if (preview != null) {
                        val m = remember(preset) { filterMatrix(preset, 0f, 1f, 1f) }
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = preset.label,
                            colorFilter = ColorFilter.colorMatrix(ColorMatrix(m)),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Text(
                    preset.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun AdjustPanel(
    edits: Edits,
    onLive: (Edits) -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Тон",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            if (edits.adjusted) {
                TextButton(
                    onClick = onReset,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("Сбросить", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        AdjustSlider("Яркость", edits.brightness, -0.5f..0.5f) {
            onLive(edits.copy(brightness = it))
        }
        AdjustSlider("Контраст", edits.contrast, 0.5f..1.5f) {
            onLive(edits.copy(contrast = it))
        }
        AdjustSlider("Насыщенность", edits.saturation, 0f..2f) {
            onLive(edits.copy(saturation = it))
        }
    }
}

@Composable
private fun AdjustSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(46.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(92.dp),
        )
        M3ESlider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f).padding(end = 6.dp),
        )
    }
}

// =====================================================================
// Геометрия и кроп
// =====================================================================

/** Прямоугольник вписывания Fit: (left, top, width, height) в px. */
private fun fitRect(container: IntSize, contentW: Int, contentH: Int): Rect {
    if (container.width == 0 || container.height == 0 || contentW <= 0 || contentH <= 0) {
        return Rect(0f, 0f, 1f, 1f)
    }
    val scale = minOf(
        container.width.toFloat() / contentW,
        container.height.toFloat() / contentH,
    )
    val w = contentW * scale
    val h = contentH * scale
    val left = (container.width - w) / 2f
    val top = (container.height - h) / 2f
    return Rect(left, top, left + w, top + h)
}

/** Оверлей кропа: затемнение, сетка, 4 угла, перетаскивание всего прямоугольника. */
@Composable
private fun CropOverlay(rect: RectF, onChange: (RectF) -> Unit, onChangeEnd: () -> Unit = {}) {
    val cur by rememberUpdatedState(rect)
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val minF = 0.06f

    fun update(f: (RectF) -> RectF) {
        onChange(f(RectF(cur)))
    }

    Box(Modifier.fillMaxSize().onSizeChanged { sizePx = it }) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            val r = Rect(
                rect.left * size.width,
                rect.top * size.height,
                rect.right * size.width,
                rect.bottom * size.height,
            )
            drawRect(Color.Black.copy(alpha = 0.55f))
            drawRect(
                color = Color.Transparent,
                topLeft = r.topLeft,
                size = r.size,
                blendMode = BlendMode.Clear,
            )
            drawRect(
                Color.White,
                topLeft = r.topLeft,
                size = r.size,
                style = Stroke(width = 1.5.dp.toPx()),
            )
            val line = 1.dp.toPx()
            for (i in 1..2) {
                val x = r.left + r.width * i / 3f
                val y = r.top + r.height * i / 3f
                drawLine(Color.White.copy(alpha = 0.55f), Offset(x, r.top), Offset(x, r.bottom), line)
                drawLine(Color.White.copy(alpha = 0.55f), Offset(r.left, y), Offset(r.right, y), line)
            }
        }

        if (sizePx.width > 0 && sizePx.height > 0) {
            val w = sizePx.width.toFloat()
            val h = sizePx.height.toFloat()

            // перетаскивание всего прямоугольника
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (cur.left * w).roundToInt(),
                            (cur.top * h).roundToInt(),
                        )
                    }
                    .size(
                        with(density) { (cur.width() * w).toDp() },
                        with(density) { (cur.height() * h).toDp() },
                    )
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = onChangeEnd,
                            onDragCancel = onChangeEnd,
                        ) { change, drag ->
                            change.consume()
                            val dx = drag.x / w
                            val dy = drag.y / h
                            update { r ->
                                val nw = r.right - r.left
                                val nh = r.bottom - r.top
                                val nl = (r.left + dx).coerceIn(0f, 1f - nw)
                                val nt = (r.top + dy).coerceIn(0f, 1f - nh)
                                RectF(nl, nt, nl + nw, nt + nh)
                            }
                        }
                    },
            )

            // 4 угловые ручки
            CornerHandle({ cur.left * w }, { cur.top * h }, 0, minF, w, h, ::update, onChangeEnd)
            CornerHandle({ cur.right * w }, { cur.top * h }, 1, minF, w, h, ::update, onChangeEnd)
            CornerHandle({ cur.left * w }, { cur.bottom * h }, 2, minF, w, h, ::update, onChangeEnd)
            CornerHandle({ cur.right * w }, { cur.bottom * h }, 3, minF, w, h, ::update, onChangeEnd)
        }
    }
}

@Composable
private fun CornerHandle(
    posX: () -> Float,
    posY: () -> Float,
    corner: Int,
    minF: Float,
    w: Float,
    h: Float,
    update: (((RectF) -> RectF) -> Unit),
    onChangeEnd: () -> Unit,
) {
    Box(
        Modifier
            .offset {
                IntOffset(
                    posX().roundToInt() - with(density) { 24.dp.roundToPx() },
                    posY().roundToInt() - with(density) { 24.dp.roundToPx() },
                )
            }
            .size(48.dp)
            .pointerInput(corner) {
                detectDragGestures(
                    onDragEnd = onChangeEnd,
                    onDragCancel = onChangeEnd,
                ) { change, drag ->
                    change.consume()
                    val dx = drag.x / w
                    val dy = drag.y / h
                    update { r ->
                        var nl = r.left
                        var nt = r.top
                        var nr = r.right
                        var nb = r.bottom
                        if (corner == 0) { nl += dx; nt += dy }
                        if (corner == 1) { nr += dx; nt += dy }
                        if (corner == 2) { nl += dx; nb += dy }
                        if (corner == 3) { nr += dx; nb += dy }
                        nl = nl.coerceIn(0f, nr - minF)
                        nt = nt.coerceIn(0f, nb - minF)
                        nr = nr.coerceIn(nl + minF, 1f)
                        nb = nb.coerceIn(nt + minF, 1f)
                        RectF(nl, nt, nr, nb)
                    }
                }
            },
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White)
                .padding(3.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}