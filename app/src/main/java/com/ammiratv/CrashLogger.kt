package com.ammiratv

import android.content.Context
import java.io.File

/**
 * Keeps the last crash on the device so it can be shown at the next start. Nothing is sent
 * anywhere. Addresses (which contain account names and passwords) are replaced before saving.
 */
object CrashLogger {
    private const val FILE = "last_crash.txt"
    private const val MAX_CHARS = 6_000
    private val URL = Regex("""https?://\S+""")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val text = buildString {
                    append("Dispositivo: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, ")
                    append("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
                    append("Thread: ${thread.name}\n\n")
                    append(android.util.Log.getStackTraceString(error))
                }
                File(app.filesDir, FILE).writeText(URL.replace(text, "<url>").take(MAX_CHARS))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The saved crash, if any. Reading it does not delete it; call [clear] when shown. */
    fun read(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
