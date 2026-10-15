package dev.aezochka.glimpse.ui

import android.graphics.Bitmap
import android.graphics.RectF
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class EditMode { NONE, CROP, FILTER, ADJUST }

private data class AspectOption(val label: String, val ratio: Float?)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(item: MediaItem, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rotation by rememberSaveable { mutableIntStateOf(0) }
    var flip by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(FilterPreset.NONE) }
    var brightness by rememberSaveable { mutableFloatStateOf(0f) }     // -0.5..0.5
    var contrast by rememberSaveable { mutableFloatStateOf(1f) }       // 0.5..1.5
    var saturation by rememberSaveable { mutableFloatStateOf(1f) }     // 0..2
    var crop by remember { mutableStateOf<RectF?>(null) }
    var aspectLabel by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(EditMode.NONE) }
    var saving by remember { mutableStateOf(false) }

    // превью с запечённым поворотом/зеркалом — быстро (лимит 1600px)
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.uri, rotation, flip) {
        preview = withContext(Dispatchers.IO) {
            val base = decodeBitmap(context.contentResolver, item.uri, 1600)
            base?.let { transformBitmap(it, rotation, flip) }
        }
    }

    val colorFilter = remember(filter, brightness, contrast, saturation) {
        val m = filterMatrix(filter, brightness, contrast, saturation)
        ColorFilter.colorMatrix(ColorMatrix(m))
    }

    fun doSave() {
        if (saving) return
        saving = true
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                val bmp = bakeEdits(
                    context, item.uri, rotation, flip, crop, filter,
                    brightness, contrast, saturation,
                )
                bmp?.let { saveBitmap(context, it) }
            }
            saving = false
            if (uri != null) {
                Toast.makeText(context, "Сохранено в Pictures/Glimpse", Toast.LENGTH_SHORT).show()
                onSaved()
            } else {
                Toast.makeText(context, "Не удалось сохранить", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // верхняя панель
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
            }
            Text(
                "Редактор",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { doSave() }, enabled = !saving && preview != null) {
                if (saving) {
                    LoadingIndicator(Modifier.size(24.dp))
                } else {
                    Text("Сохранить")
                }
            }
        }

        // превью
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            val bmp = preview
            if (bmp == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(48.dp))
                }
            } else {
                var boxSize by remember { mutableStateOf(IntSize.Zero) }
                Box(Modifier.fillMaxSize().onSizeChanged { boxSize = it }) {
                    val disp = remember(bmp, boxSize) { fitRect(boxSize, bmp.width, bmp.height) }
                    val density = LocalDensity.current
                    Box(
                        Modifier
                            .offset { IntOffset(disp.left.roundToInt(), disp.top.roundToInt()) }
                            .size(
                                with(density) { disp.width.toDp() },
                                with(density) { disp.height.toDp() },
                            ),
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            colorFilter = colorFilter,
                            modifier = Modifier.fillMaxSize(),
                        )
                        val c = crop
                        if (mode == EditMode.CROP && c != null) {
                            CropOverlay(
                                rect = c,
                                onChange = { crop = it },
                            )
                        }
                    }
                }
            }
        }

        // инструменты
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ToolButton("Обрезать", Icons.Rounded.Crop, mode == EditMode.CROP) { mode = EditMode.CROP }
                ToolButton("Повернуть", Icons.Rounded.RotateRight, false) {
                    rotation = (rotation + 90) % 360
                    crop = null
                    aspectLabel = null
                }
                ToolButton("Отразить", Icons.Rounded.Flip, false) { flip = !flip }
                ToolButton("Фильтры", Icons.Rounded.AutoFixHigh, mode == EditMode.FILTER) { mode = EditMode.FILTER }
                ToolButton("Настройки", Icons.Rounded.Tune, mode == EditMode.ADJUST) { mode = EditMode.ADJUST }
            }
            val bmp = preview
            when (mode) {
                EditMode.CROP -> if (bmp != null) {
                    val options = remember {
                        listOf(
                            AspectOption("Свободно", null),
                            AspectOption("1:1", 1f),
                            AspectOption("4:3", 4f / 3f),
                            AspectOption("16:9", 16f / 9f),
                            AspectOption("9:16", 9f / 16f),
                        )
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                        itemsIndexed(options, key = { _, o -> o.label }) { _, opt ->
                            FilterChip(
                                selected = aspectLabel == opt.label,
                                onClick = {
                                    aspectLabel = opt.label
                                    crop = aspectCropRect(opt.ratio, bmp.width, bmp.height)
                                },
                                label = { Text(opt.label) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }
                EditMode.FILTER -> {
                    val presets = FilterPreset.entries
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                        itemsIndexed(presets, key = { _, p -> p.name }) { _, preset ->
                            FilterChip(
                                selected = filter == preset,
                                onClick = { filter = preset },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (bmp != null) {
                                            val m = remember(preset) { filterMatrix(preset, 0f, 1f, 1f) }
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = preset.label,
                                                colorFilter = ColorFilter.colorMatrix(ColorMatrix(m)),
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(28.dp).clip(CircleShape),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(preset.label)
                                    }
                                },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }
                EditMode.ADJUST -> Column(Modifier.padding(horizontal = 24.dp)) {
                    AdjustSlider("Яркость", brightness, -0.5f, 0.5f) { brightness = it }
                    AdjustSlider("Контраст", (contrast - 1f) * 2f, -1f, 1f) { contrast = 1f + it / 2f }
                    AdjustSlider("Насыщенность", saturation - 1f, -1f, 1f) { saturation = 1f + it }
                }
                EditMode.NONE -> {}
            }
        }
    }
}

@Composable
private fun ToolButton(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .pillPress(onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) scheme.secondaryContainer else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (active) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun AdjustSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onChange, valueRange = min..max)
    }
}

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
private fun CropOverlay(rect: RectF, onChange: (RectF) -> Unit) {
    val cur by rememberUpdatedState(rect)
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val minF = 0.06f

    fun update(f: (RectF) -> RectF) {
        onChange(f(RectF(cur)))
    }

    Box(Modifier.fillMaxSize().onSizeChanged { sizePx = it }) {
        // затемнение + сетка
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
                        detectDragGestures { change, drag ->
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
            CornerHandle({ cur.left * w }, { cur.top * h }, 0, minF, w, h, ::update)
            CornerHandle({ cur.right * w }, { cur.top * h }, 1, minF, w, h, ::update)
            CornerHandle({ cur.left * w }, { cur.bottom * h }, 2, minF, w, h, ::update)
            CornerHandle({ cur.right * w }, { cur.bottom * h }, 3, minF, w, h, ::update)
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
) {
    Box(
        Modifier
            .offset { IntOffset(posX().roundToInt() - 24, posY().roundToInt() - 24) }
            .size(48.dp)
            .pointerInput(corner) {
                detectDragGestures { change, drag ->
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
