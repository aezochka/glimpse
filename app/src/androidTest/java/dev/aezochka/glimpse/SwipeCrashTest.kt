package dev.aezochka.glimpse

import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Регресс на «краш при свайпе»: сидим фото+видео в MediaStore,
 * листаем вьювер туда-обратно, свайпаем вкладки галереи и скроллим сетку.
 */
@RunWith(AndroidJUnit4::class)
class SwipeCrashTest {

    private val perms = GrantPermissionRule.grant(
        *(if (Build.VERSION.SDK_INT >= 33) arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        ) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
    )
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(perms).around(compose)

    companion object {
        @JvmStatic
        @BeforeClass
        fun seed() {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val r = ctx.contentResolver
            // фото: разные размеры/ориентации
            val sizes = listOf(1200 to 900, 900 to 1600, 4000 to 3000, 640 to 640, 1920 to 1080, 300 to 2000)
            sizes.forEachIndexed { i, (w, h) ->
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "glimpse_test_$i.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= 29) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GlimpseTest")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val uri = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@forEachIndexed
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                Canvas(bmp).apply {
                    drawColor(Color.HSVToColor(floatArrayOf(i * 55f, 0.6f, 0.9f)))
                    drawCircle(w / 2f, h / 2f, minOf(w, h) / 3f, Paint().apply { color = Color.WHITE })
                }
                r.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                bmp.recycle()
                if (Build.VERSION.SDK_INT >= 29) {
                    r.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                }
            }
            // видео: тестовый клип из assets androidTest
            val testCtx = InstrumentationRegistry.getInstrumentation().context
            repeat(3) { i ->
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "glimpse_clip_$i.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= 29) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/GlimpseTest")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }
                val uri = r.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return@repeat
                testCtx.assets.open("clip.mp4").use { input ->
                    r.openOutputStream(uri)!!.use { input.copyTo(it) }
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    r.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                }
            }
        }
    }

    private fun waitCells() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("cell").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Скриншот корня в external-files: дизайн можно посмотреть не на телефоне. */
    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(1500) // дать Coil дорисовать превью
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        if (bmp == null) {
            Log.e("GlimpseShot", "takeScreenshot вернул null для $name")
            return
        }
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        // Downloads, а не Pictures: файлы переживают uninstall (AGP удаляет app после теста,
        // run-as тогда не работает) и не попадают в нашу галерею
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "glimpse_$name.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/GlimpseShots")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Log.e("GlimpseShot", "не вставил $name в MediaStore")
            return
        }
        try {
            resolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null,
            )
            Log.i("GlimpseShot", "saved $name → Download/GlimpseShots")
        } catch (e: Exception) {
            Log.e("GlimpseShot", "не записал $name", e)
        }
    }

    @Test
    fun screenshots() {
        waitCells()
        shot("01_gallery")
        compose.onNodeWithTag("galleryPager").performTouchInput { swipeLeft() }
        shot("02_albums")
        compose.onNodeWithTag("galleryPager").performTouchInput { swipeRight() }
        compose.waitForIdle()
        // долгое нажатие → режим выбора
        compose.onAllNodesWithTag("cell").onFirst().performTouchInput { longClick() }
        shot("03_selection")
        compose.onAllNodesWithContentDescription("Выйти из выбора").onFirst().performClick()
        compose.waitForIdle()
        // вьювер: фото, затем следующая страница
        compose.onAllNodesWithTag("cell").onFirst().performClick()
        shot("04_viewer")
        compose.onNodeWithTag("viewerPager").performTouchInput { swipeLeft() }
        shot("05_viewer_next")
    }

    @Test
    fun viewerSwipeThroughAll() {
        waitCells()
        compose.onAllNodesWithTag("cell").onFirst().performClick()
        compose.waitForIdle()
        repeat(12) {
            compose.onNodeWithTag("viewerPager").performTouchInput { swipeLeft() }
            compose.waitForIdle()
            Thread.sleep(250)
        }
        repeat(12) {
            compose.onNodeWithTag("viewerPager").performTouchInput { swipeRight() }
            compose.waitForIdle()
            Thread.sleep(150)
        }
        // быстрые свайпы без ожидания — гонки dispose/attach плеера
        repeat(10) {
            compose.onNodeWithTag("viewerPager").performTouchInput { swipeLeft(durationMillis = 80) }
        }
        compose.waitForIdle()
        repeat(10) {
            compose.onNodeWithTag("viewerPager").performTouchInput { swipeRight(durationMillis = 80) }
        }
        compose.waitForIdle()
        Thread.sleep(800)
    }

    @Test
    fun galleryTabsSwipeAndScroll() {
        waitCells()
        repeat(4) {
            compose.onNodeWithTag("galleryPager").performTouchInput { swipeLeft() }
            compose.waitForIdle()
            compose.onNodeWithTag("galleryPager").performTouchInput { swipeRight() }
            compose.waitForIdle()
        }
        repeat(3) {
            compose.onNodeWithTag("galleryPager").performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        Thread.sleep(500)
    }
}
