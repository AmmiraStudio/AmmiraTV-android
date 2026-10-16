package com.ammiratv.domain.usecase

import com.ammiratv.core.common.ChannelNameCleaner
import com.ammiratv.core.network.m3u.M3uDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class OfficialProvider(val label: String) {
    RAIPLAY("RaiPlay")
}

/**
 * Where a national channel can be watched from its broadcaster, as a fallback for a playlist
 * stream that does not work.
 *
 * @property playUrl a stream the player can open directly.
 */
data class OfficialSource(
    val provider: OfficialProvider,
    val channel: String,
    val playUrl: String
)

/**
 * Official, free source of the Rai channels.
 *
 * - **RaiPlay**: the live channels are public and need no account. The address below asks Rai
 *   for the current HLS stream (it hands out a short-lived token each time, so it is always
 *   resolved at playback time, never stored). Rai serves it only to Italian addresses: outside
 *   Italy a VPN with an Italian exit is needed.
 */
object OfficialStreams {

    private const val RELINKER = "https://mediapolis.rai.it/relinker/relinkerServlet.htm"

    /** Rai channel -> RaiPlay content id (checked: each one answers with a live HLS playlist). */
    private val RAI_IDS = mapOf(
        "rai1" to ("Rai 1" to 2606803),
        "rai2" to ("Rai 2" to 308718),
        "rai3" to ("Rai 3" to 308709),
        "rai4" to ("Rai 4" to 746966),
        "raimovie" to ("Rai Movie" to 747002),
        "raipremium" to ("Rai Premium" to 746992),
        "raistoria" to ("Rai Storia" to 746990),
        "rainews24" to ("Rai News 24" to 1),
        "rainews" to ("Rai News 24" to 1),
        "raisport" to ("Rai Sport" to 358025)
    )

    private val QUALITY_TAIL = Regex("(hd|fhd|uhd|sd|4k|hevc|h265|h264|1080p|720p|1080|720)+$")

    /** "IT: RAI 1 HD" -> "rai1". Regional editions ("Rai 1 Sicilia") do not match on purpose. */
    fun key(name: String): String {
        val cleaned = ChannelNameCleaner.clean(name).lowercase()
        val compact = cleaned.replace(Regex("[^a-z0-9]"), "")
        return compact.replace(QUALITY_TAIL, "")
    }

    fun forChannel(name: String): OfficialSource? {
        val k = key(name)
        RAI_IDS[k]?.let { (label, id) ->
            return OfficialSource(
                provider = OfficialProvider.RAIPLAY,
                channel = label,
                playUrl = "$RELINKER?cont=$id&output=7&forceUserAgent=raiplayappletv"
            )
        }
        return null
    }
}
/**
 * Turns a Rai channel into a stream that is playable right now: asks Rai for the current live
 * playlist and checks it really is live (outside Italy Rai answers with a short "not available"
 * clip instead, which must not be mistaken for the channel). Null means "use the playlist".
 */
@Singleton
class OfficialStreamResolver @Inject constructor(
    private val downloader: M3uDownloader
) {
    suspend fun resolve(channelName: String): OfficialSource? {
        val source = OfficialStreams.forChannel(channelName) ?: return null
        val live = withContext(Dispatchers.IO) { downloader.resolveLiveHls(source.playUrl, TIMEOUT_MS) } ?: return null
        return source.copy(playUrl = live)
    }

    private companion object {
        const val TIMEOUT_MS = 6_000L
    }
}
