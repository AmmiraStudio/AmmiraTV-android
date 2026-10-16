package com.ammiratv.domain.usecase

import com.ammiratv.core.network.m3u.M3uDownloader
import com.ammiratv.core.network.xtream.XtreamClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** The server that answered best, and the rest (kept as fallbacks). */
data class ServerPick(val best: String, val others: List<String>)

/**
 * Chooses among several addresses (DNS / mirrors) of the same provider: all are tried at once
 * and the fastest one that really works wins. Providers often change or block one hostname;
 * with a list the playlist keeps loading.
 */
@Singleton
class ServerSelector @Inject constructor(
    private val xtream: XtreamClient,
    private val m3u: M3uDownloader
) {
    suspend fun pickXtream(candidates: List<String>, user: String, pass: String): ServerPick? =
        pick(candidates) { url ->
            val info = xtream.authenticate(url, user, pass).userInfo
            info != null && (info.status.isEmpty() || info.status.equals("Active", ignoreCase = true))
        }

    suspend fun pickM3u(candidates: List<String>): ServerPick? =
        pick(candidates) { url -> withContext(Dispatchers.IO) { m3u.looksLikePlaylist(url, PROBE_TIMEOUT_MS) } }

    private suspend fun pick(candidates: List<String>, probe: suspend (String) -> Boolean): ServerPick? =
        coroutineScope {
            val results = candidates.map { url ->
                async {
                    val start = System.nanoTime()
                    val ok = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                        runCatching { probe(url) }.getOrDefault(false)
                    } ?: false
                    Triple(url, ok, System.nanoTime() - start)
                }
            }.awaitAll()
            val best = results.filter { it.second }.minByOrNull { it.third }?.first ?: return@coroutineScope null
            ServerPick(best, candidates.filter { it != best })
        }

    private companion object {
        const val PROBE_TIMEOUT_MS = 8_000L
    }
}