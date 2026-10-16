package com.ammiratv.data.recording

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ammiratv.core.common.ApplicationScope
import com.ammiratv.core.model.Recording
import com.ammiratv.core.model.RecordingStatus
import com.ammiratv.domain.repository.RecordingEngine
import com.ammiratv.domain.repository.RecordingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records a stream by copying its bytes to a file (MPEG-TS stays playable as-is).
 *
 * Timers are in-process coroutines: they are re-armed at app start by [rearmScheduled]. A
 * recording due while the app is not running will be marked as missed rather than started late
 * (starting from the middle of a programme without telling the user would be worse).
 */
@Singleton
class RecordingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
    private val repository: RecordingRepository,
    @ApplicationScope private val scope: CoroutineScope
) : RecordingEngine {

    // No read timeout on the stream itself would hang forever on a dead server: use a long one.
    private val client = okHttpClient.newBuilder()
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    private val jobs = ConcurrentHashMap<Long, Job>()

    private val _runningCount = MutableStateFlow(0)
    override val runningCount: StateFlow<Int> = _runningCount.asStateFlow()

    override suspend fun rearmScheduled() {
        val now = System.currentTimeMillis()

        // Anything still flagged RECORDING lost its process: its file is whatever was written.
        repository.getRunning().forEach {
            repository.update(it.copy(status = RecordingStatus.DONE, errorMessage = "Interrotta alla chiusura dell'app"))
        }

        repository.getScheduled().forEach { rec ->
            when {
                rec.endTime <= now -> repository.update(
                    rec.copy(status = RecordingStatus.FAILED, errorMessage = "Persa: l'app non era in esecuzione")
                )
                else -> arm(rec)
            }
        }
    }

    override suspend fun schedule(recording: Recording): Long {
        val id = repository.insert(recording.copy(status = RecordingStatus.SCHEDULED))
        arm(recording.copy(id = id))
        return id
    }

    override suspend fun stop(id: Long) {
        jobs.remove(id)?.cancel()
        val rec = repository.getById(id) ?: return
        when (rec.status) {
            RecordingStatus.SCHEDULED ->
                repository.update(rec.copy(status = RecordingStatus.CANCELLED))
            // The writer's own cancellation handler finalises a running recording; this covers
            // the case where it already vanished (e.g. after a crash).
            RecordingStatus.RECORDING ->
                repository.update(rec.copy(status = RecordingStatus.DONE, sizeBytes = rec.filePath?.let { File(it).length() } ?: 0))
            else -> Unit
        }
    }

    override suspend fun delete(id: Long) {
        stop(id)
        repository.getById(id)?.filePath?.let { runCatching { File(it).delete() } }
        repository.delete(id)
    }

    // ── internals ────────────────────────────────────────────────────────

    private fun arm(recording: Recording) {
        jobs.remove(recording.id)?.cancel()
        jobs[recording.id] = scope.launch {
            val wait = recording.startTime - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            record(recording)
        }
    }

    private suspend fun record(initial: Recording) {
        val file = newFile(initial)
        var rec = initial.copy(status = RecordingStatus.RECORDING, filePath = file.absolutePath)
        repository.update(rec)
        setRunning(+1)

        var written = 0L
        var failure: String? = null
        try {
            withContext(Dispatchers.IO) {
                client.newCall(Request.Builder().url(initial.streamUrl).build()).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("Risposta vuota")
                    val buffer = ByteArray(64 * 1024)
                    var lastReport = System.currentTimeMillis()

                    file.outputStream().use { out ->
                        body.byteStream().use { input ->
                            while (isActive && System.currentTimeMillis() < initial.endTime) {
                                val n = input.read(buffer)
                                if (n < 0) break // provider closed the stream
                                out.write(buffer, 0, n)
                                written += n

                                // Progress for the list; DB write throttled to every 5 s
                                if (System.currentTimeMillis() - lastReport > 5_000) {
                                    lastReport = System.currentTimeMillis()
                                    repository.update(rec.copy(sizeBytes = written))
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            // Stopped by the user: finalise below, then rethrow so the job really ends.
            finish(rec, written, error = null, cancelled = true)
            setRunning(-1)
            throw e
        } catch (e: Exception) {
            failure = e.localizedMessage ?: "Errore di rete"
        }

        finish(rec, written, failure, cancelled = false)
        setRunning(-1)
        jobs.remove(initial.id)
    }

    private suspend fun finish(rec: Recording, written: Long, error: String?, cancelled: Boolean) {
        val status = when {
            written > 0 -> RecordingStatus.DONE // keep a partial file the user can still watch
            cancelled -> RecordingStatus.CANCELLED
            else -> RecordingStatus.FAILED
        }
        // Runs also when the surrounding scope is cancelled, so use a non-cancellable write.
        withContext(kotlinx.coroutines.NonCancellable) {
            repository.update(
                rec.copy(
                    status = status,
                    sizeBytes = written,
                    errorMessage = if (status == RecordingStatus.DONE && error != null) "Interrotta: $error" else error
                )
            )
        }
    }

    private fun newFile(rec: Recording): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "recordings").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(rec.startTime))
        val safeName = rec.channelName.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(40)
        return File(dir, "${stamp}_${safeName}_${rec.id}.ts")
    }

    private fun setRunning(delta: Int) {
        val now = _runningCount.updateAndGet { (it + delta).coerceAtLeast(0) }
        // Keep the process alive (foreground service) only while something is being written
        val intent = Intent(context, RecordingService::class.java)
        if (now > 0) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
    }

    private fun MutableStateFlow<Int>.updateAndGet(f: (Int) -> Int): Int {
        var result = 0
        update { f(it).also { v -> result = v } }
        return result
    }
}
