package dev.aezochka.glimpse.ui

import android.graphics.Bitmap
import android.graphics.RectF
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.toShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.text.font.FontWeight
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

private enum class EditMode { NONE, CROP }

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
    var showFilters by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }

    // базовый битмап декодируем один раз; поворот/зеркало — мгновенно из кэша
    var baseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.uri) {
        baseBitmap = withContext(Dispatchers.IO) {
            decodeBitmap(context.contentResolver, item.uri, 1600)
        }
    }
    LaunchedEffect(baseBitmap, rotation, flip) {
        preview = baseBitmap?.let { b ->
            withContext(Dispatchers.Default) { transformBitmap(b, rotation, flip) }
        }
    }

    LaunchedEffect(mode, preview) {
        if (mode == EditMode.CROP && crop == null) {
            preview?.let { b -> crop = aspectCropRect(null, b.width, b.height) }
        }
    }

    val colorFilter = remember(filter, brightness, contrast, saturation) {
        val m = filterMatrix(filter, brightness, contrast, saturation)
        ColorFilter.colorMatrix(ColorMatrix(m))
    }
    val anyAdjust = brightness != 0f || contrast != 1f || saturation != 1f

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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // ---- фото ----
        val bmp = preview
        if (bmp == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(Modifier.size(48.dp))
            }
        } else {
            var boxSize by remember { mutableStateOf(IntSize.Zero) }
            // в режиме кропа фото выше тулбара — нижние ручки доступны
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = if (mode == EditMode.CROP) 108.dp else 0.dp)
                    .onSizeChanged { boxSize = it }
            ) {
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
                    // поворот/флип «пружинит»: пересоздание превью анимируется
                    AnimatedContent(
                        targetState = bmp,
                        label = "preview",
                        transitionSpec = {
                            (scaleIn(Motion.spatial(), initialScale = 0.92f) + fadeIn(Motion.spatial())) togetherWith
                                (scaleOut(Motion.effects(), targetScale = 0.92f) + fadeOut(Motion.effects()))
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
                    val c = crop
                    if (mode == EditMode.CROP && c != null) {
                        CropOverlay(rect = c, onChange = { crop = it })
                    }
                }
            }
        }

        // ---- верх: пилюли поверх фото ----
        Row(
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.pillPress(onBack),
            ) {
                Text(
                    "Отмена",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { doSave() },
                enabled = !saving && preview != null,
                shape = RoundedCornerShape(999.dp),
            ) {
                if (saving) {
                    LoadingIndicator(Modifier.size(20.dp))
                } else {
                    Text("Сохранить")
                }
            }
        }

        // ---- низ: плавающая тулбар-пилюля + пресеты кропа ----
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = mode == EditMode.CROP,
                enter = fadeIn(Motion.spatial()) + slideInVertically(Motion.spatial()) { it / 2 },
                exit = fadeOut(Motion.effects()) + slideOutVertically(Motion.effects()) { it / 2 },
            ) {
                val bmp2 = preview
                if (bmp2 != null) {
                    val options = remember {
                        listOf(
                            AspectOption("Свободно", null),
                            AspectOption("1:1", 1f),
                            AspectOption("4:3", 4f / 3f),
                            AspectOption("16:9", 16f / 9f),
                            AspectOption("9:16", 9f / 16f),
                        )
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(options, key = { _, o -> o.label }) { _, opt ->
                            FilterChip(
                                selected = aspectLabel == opt.label,
                                onClick = {
                                    aspectLabel = opt.label
                                    crop = aspectCropRect(opt.ratio, bmp2.width, bmp2.height)
                                },
                                label = { Text(opt.label) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .shadow(8.dp, RoundedCornerShape(999.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ToolIcon(Icons.Rounded.Crop, "Обрезать", active = mode == EditMode.CROP) {
                    if (mode == EditMode.CROP) {
                        // выход из кропа: что видно на превью, то и сохранится
                        mode = EditMode.NONE
                        crop = null
                        aspectLabel = null
                    } else {
                        mode = EditMode.CROP
                    }
                }
                ToolIcon(Icons.Rounded.RotateRight, "Повернуть", active = false) {
                    rotation = (rotation + 90) % 360
                    crop = null
                    aspectLabel = null
                }
                ToolIcon(Icons.Rounded.Flip, "Отразить", active = false) { flip = !flip }
                ToolIcon(Icons.Rounded.AutoFixHigh, "Фильтры", active = filter != FilterPreset.NONE) {
                    showFilters = true
                }
                ToolIcon(Icons.Rounded.Tune, "Настройки", active = anyAdjust) {
                    showAdjust = true
                }
            }
        }

        // ---- шторка фильтров ----
        if (showFilters) {
            ModalBottomSheet(onDismissRequest = { showFilters = false }) {
                FiltersSheet(
                    preview = preview,
                    selected = filter,
                    onPick = { filter = it },
                )
            }
        }

        // ---- шторка настроек ----
        if (showAdjust) {
            ModalBottomSheet(onDismissRequest = { showAdjust = false }) {
                AdjustSheet(
                    brightness = brightness,
                    contrast = contrast,
                    saturation = saturation,
                    onBrightness = { brightness = it },
                    onContrast = { contrast = it },
                    onSaturation = { saturation = it },
                    onReset = {
                        brightness = 0f; contrast = 1f; saturation = 1f
                    },
                )
            }
        }
    }
}

// =====================================================================
// Панели
// =====================================================================

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToolIcon(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = if (active) MaterialShapes.Cookie12Sided.toShape() else RoundedCornerShape(999.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .pillPress(onClick)
            .clip(shape)
            .background(if (active) scheme.primary else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(
            icon, label,
            tint = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FiltersSheet(preview: Bitmap?, selected: FilterPreset, onPick: (FilterPreset) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Фильтры", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (selected != FilterPreset.NONE) {
                TextButton(onClick = { onPick(FilterPreset.NONE) }) { Text("Сбросить") }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(FilterPreset.entries, key = { _, p -> p.name }) { _, preset ->
                val active = selected == preset
                val scale by animateFloatAsState(
                    if (active) 1.05f else 1f, Motion.spatialFast(), label = "filterScale",
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .pillPress { onPick(preset) }
                        .graphicsLayer { scaleX = scale; scaleY = scale },
                ) {
                    Box(
                        Modifier
                            .size(width = 76.dp, height = 100.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = if (active) 3.dp else 2.dp,
                                color = if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(18.dp),
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
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AdjustSheet(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    onBrightness: (Float) -> Unit,
    onContrast: (Float) -> Unit,
    onSaturation: (Float) -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Настройки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onReset) { Text("Сбросить") }
        }
        AdjustSlider("Яркость", brightness, -0.5f, 0.5f, onBrightness)
        AdjustSlider("Контраст", (contrast - 1f) * 2f, -1f, 1f) { onContrast(1f + it / 2f) }
        AdjustSlider("Насыщенность", saturation - 1f, -1f, 1f) { onSaturation(1f + it) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AdjustSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text(
                "${((value - min) / (max - min) * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(value = value.coerceIn(min, max), onValueChange = onChange, valueRange = min..max)
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
private fun CropOverlay(rect: RectF, onChange: (RectF) -> Unit) {
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
