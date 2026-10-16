package com.ammiratv.core.numbering

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

sealed interface RemoteUpdateResult {
    data class Updated(val version: Int) : RemoteUpdateResult
    data class UpToDate(val version: Int) : RemoteUpdateResult
    data class Invalid(val errors: List<String>) : RemoteUpdateResult
    data class Failed(val message: String) : RemoteUpdateResult
}

/**
 * Source of the canonical channel database. The interface is what the rest of the app depends on,
 * so another country (or another source) is a new implementation or a new data file, not new logic.
 */
interface ChannelNumberingRepository {
    /** The best valid database available: remote copy if newer, else the bundled one. */
    suspend fun database(): CanonicalDatabase

    /** Downloads [url], validates it, and keeps it only if it is newer than what is in use. */
    suspend fun updateFrom(url: String): RemoteUpdateResult
}

/**
 * Bundled copy in assets (`numbering/it.json`) plus an optional downloaded copy in app storage.
 * A bad download never replaces a good file: it is validated before it is written.
 */
class FileChannelNumberingRepository(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) : ChannelNumberingRepository {

    private val storedFile: File get() = File(File(context.filesDir, "numbering"), "it.json")
    @Volatile private var cached: CanonicalDatabase? = null

    override suspend fun database(): CanonicalDatabase = withContext(Dispatchers.IO) {
        cached ?: loadBest().also { cached = it }
    }

    private fun loadBundled(): CanonicalDatabase {
        val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        return CanonicalDatabaseParser.parse(text).database
            ?: error("Il database canali incluso nell'app non e' valido")
    }

    private fun loadBest(): CanonicalDatabase {
        val bundled = loadBundled()
        val stored = runCatching {
            if (storedFile.exists()) CanonicalDatabaseParser.parse(storedFile.readText()).database else null
        }.getOrNull()
        return if (stored != null && stored.version > bundled.version) stored else bundled
    }

    override suspend fun updateFrom(url: String): RemoteUpdateResult = withContext(Dispatchers.IO) {
        val clean = url.trim()
        if (!clean.startsWith("https://", ignoreCase = true)) {
            return@withContext RemoteUpdateResult.Failed("Serve un indirizzo https://")
        }
        val body = try {
            client.newCall(Request.Builder().url(clean).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext RemoteUpdateResult.Failed("HTTP ${response.code}")
                val bytes = response.body?.byteStream()?.readNBytesCompat(MAX_BYTES + 1)
                    ?: return@withContext RemoteUpdateResult.Failed("Risposta vuota")
                if (bytes.size > MAX_BYTES) return@withContext RemoteUpdateResult.Failed("File troppo grande")
                bytes.decodeToString()
            }
        } catch (e: IOException) {
            return@withContext RemoteUpdateResult.Failed(e.message ?: "errore di rete")
        } catch (e: IllegalArgumentException) {
            return@withContext RemoteUpdateResult.Failed("Indirizzo non valido")
        }

        val result = CanonicalDatabaseParser.parse(body)
        val incoming = result.database ?: return@withContext RemoteUpdateResult.Invalid(result.errors)

        val current = database()
        if (incoming.version <= current.version) return@withContext RemoteUpdateResult.UpToDate(current.version)

        storedFile.parentFile?.mkdirs()
        // Write to a temp file and rename, so a crash mid-write cannot leave a half file behind
        val tmp = File(storedFile.parentFile, "it.json.tmp")
        tmp.writeText(body)
        if (!tmp.renameTo(storedFile)) {
            storedFile.writeText(body)
            tmp.delete()
        }
        cached = incoming
        RemoteUpdateResult.Updated(incoming.version)
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() < limit) {
            val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        const val ASSET_PATH = "numbering/it.json"
        const val MAX_BYTES = 2 * 1024 * 1024
    }
}
