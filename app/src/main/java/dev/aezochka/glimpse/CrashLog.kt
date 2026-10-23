package dev.aezochka.glimpse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Диагностика device-only крашей без adb: перехватываем фатальный стек,
 * пишем его в filesDir, а на следующем запуске галерея предлагает скопировать
 * лог в буфер и прислать. Старые логи не копим (последние [KEEP]).
 */
object CrashLog {

    private const val DIR = "crashes"
    private const val KEEP = 3

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // сам обработчик не должен превращаться в второй краш
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun latest(context: Context): File? =
        dir(context).listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }

    fun clear(context: Context) {
        runCatching { dir(context).listFiles()?.forEach { it.delete() } }
    }

    fun text(file: File): String = runCatching { file.readText() }.getOrDefault("")

    fun copy(context: Context, text: String) {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Glimpse crash", text))
        }
    }

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val body = buildString {
            appendLine("Glimpse ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("$stamp · Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(Log.getStackTraceString(error))
        }
        File(dir(context), "crash-${System.currentTimeMillis()}.txt").writeText(body)
        dir(context).listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP)
            ?.forEach { it.delete() }
    }
}