package dev.aezochka.glimpse.editor

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface

enum class FilterPreset(val label: String) {
    NONE("Оригинал"),
    VIVID("Яркий"),
    NOIR("Ч/Б"),
    SEPIA("Сепия"),
    WARM("Тёплый"),
    COOL("Холодный"),
    FADE("Винтаж"),
}

private val IDENTITY = floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

/** Умножение 4x5 матриц: (a*b) — сначала применяется b. */
private fun matMul(a: FloatArray, b: FloatArray): FloatArray {
    val out = FloatArray(20)
    for (r in 0..3) {
        for (c in 0..4) {
            var v = 0f
            for (k in 0..3) v += a[r * 5 + k] * b[k * 5 + c]
            if (c == 4) v += a[r * 5 + 4]
            out[r * 5 + c] = v
        }
    }
    return out
}

private fun matSat(s: Float): FloatArray = floatArrayOf(
    0.213f + 0.787f * s, 0.715f - 0.715f * s, 0.072f - 0.072f * s, 0f, 0f,
    0.213f - 0.213f * s, 0.715f + 0.285f * s, 0.072f - 0.072f * s, 0f, 0f,
    0.213f - 0.213f * s, 0.715f - 0.715f * s, 0.072f + 0.928f * s, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

private fun matContrast(c: Float): FloatArray = floatArrayOf(
    c, 0f, 0f, 0f, (1f - c) * 128f,
    0f, c, 0f, 0f, (1f - c) * 128f,
    0f, 0f, c, 0f, (1f - c) * 128f,
    0f, 0f, 0f, 1f, 0f,
)

private fun matBrightness(b: Float): FloatArray = floatArrayOf(
    1f, 0f, 0f, 0f, b * 255f,
    0f, 1f, 0f, 0f, b * 255f,
    0f, 0f, 1f, 0f, b * 255f,
    0f, 0f, 0f, 1f, 0f,
)

private fun presetBase(preset: FilterPreset): FloatArray = when (preset) {
    FilterPreset.NONE -> IDENTITY
    FilterPreset.VIVID -> matSat(1.18f)
    FilterPreset.NOIR -> matMul(matContrast(1.1f), matSat(0f))
    FilterPreset.SEPIA -> floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
    FilterPreset.WARM -> floatArrayOf(
        1.09f, 0f, 0f, 0f, 12f,
        0f, 1.01f, 0f, 0f, 0f,
        0f, 0f, 0.88f, 0f, -8f,
        0f, 0f, 0f, 1f, 0f,
    )
    FilterPreset.COOL -> floatArrayOf(
        0.9f, 0f, 0.02f, 0f, 0f,
        0f, 1.0f, 0.04f, 0f, 0f,
        0.06f, 0f, 1.1f, 0f, 6f,
        0f, 0f, 0f, 1f, 0f,
    )
    FilterPreset.FADE -> matMul(matBrightness(0.06f), matMul(matContrast(0.82f), matSat(0.75f)))
}

/** Итоговая матрица цвета: пресет × насыщенность × контраст × яркость. */
fun filterMatrix(
    preset: FilterPreset,
    brightness: Float,
    contrast: Float,
    saturation: Float,
): FloatArray {
    var m = presetBase(preset)
    if (saturation != 1f) m = matMul(matSat(saturation), m)
    if (contrast != 1f) m = matMul(matContrast(contrast), m)
    if (brightness != 0f) m = matMul(matBrightness(brightness), m)
    return m
}

/** Декодирование с ограничением размера (быстро, без OOM). */
fun decodeBitmap(resolver: ContentResolver, uri: Uri, maxDim: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        var sample = 1
        while (maxOf(w, h) / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        orientBitmap(decoded, orientation)
    } catch (e: Exception) {
        null
    }
}

/** Apply EXIF orientation to decoded pixels, including mirrored and transposed images. */
internal fun orientBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            else -> return bitmap
        }
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/** Поворот кратно 90° + зеркало. Возвращает новый bitmap. */
fun transformBitmap(src: Bitmap, rotation: Int, flipH: Boolean): Bitmap {
    if (rotation % 360 == 0 && !flipH) return src
    val m = Matrix()
    m.postRotate(rotation.toFloat())
    if (flipH) m.postScale(-1f, 1f)
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

/** Кроп по нормализованному прямоугольнику (0..1). */
fun cropBitmap(src: Bitmap, rect: RectF): Bitmap {
    val x = (rect.left * src.width).toInt().coerceIn(0, src.width - 1)
    val y = (rect.top * src.height).toInt().coerceIn(0, src.height - 1)
    val x2 = (rect.right * src.width).toInt().coerceIn(x + 1, src.width)
    val y2 = (rect.bottom * src.height).toInt().coerceIn(y + 1, src.height)
    return Bitmap.createBitmap(src, x, y, x2 - x, y2 - y)
}

/** Нормализованный кроп под соотношение (null = свободный). */
fun aspectCropRect(target: Float?, imgW: Int, imgH: Int): RectF {
    if (target == null) return RectF(0.08f, 0.08f, 0.92f, 0.92f)
    val ratio = target * imgH / imgW  // нужное rw/rh в нормализованных координатах
    val rw: Float
    val rh: Float
    if (ratio >= 1f) {
        rw = 1f
        rh = 1f / ratio
    } else {
        rh = 1f
        rw = ratio
    }
    val cx = 0.5f
    val cy = 0.5f
    return RectF(cx - rw / 2f, cy - rh / 2f, cx + rw / 2f, cy + rh / 2f)
}

/** Полная сборка результата на максимальном качестве. */
fun bakeEdits(
    context: Context,
    uri: Uri,
    rotation: Int,
    flipH: Boolean,
    crop: RectF?,
    preset: FilterPreset,
    brightness: Float,
    contrast: Float,
    saturation: Float,
): Bitmap? {
    val resolver = context.contentResolver
    var bmp = decodeBitmap(resolver, uri, 4096) ?: return null
    bmp = transformBitmap(bmp, rotation, flipH)
    if (crop != null) bmp = cropBitmap(bmp, crop)
    val cm = ColorMatrix(filterMatrix(preset, brightness, contrast, saturation))
    val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
    canvas.drawBitmap(bmp, 0f, 0f, paint)
    return out
}

/** Сохранение копии в Pictures/Glimpse через MediaStore. */
fun saveBitmap(context: Context, bitmap: Bitmap): Uri? {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Glimpse")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    return try {
        val wroteImage = resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        } ?: false
        if (!wroteImage) error("No image output written")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            if (resolver.update(uri, done, null, null) != 1) error("Could not publish image")
        }
        uri
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        null
    }
}
