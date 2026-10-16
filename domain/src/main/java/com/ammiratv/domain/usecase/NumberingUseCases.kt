package com.ammiratv.domain.usecase

import com.ammiratv.core.datastore.AmmiraTvPreferences
import com.ammiratv.core.model.Channel
import com.ammiratv.core.model.DuplicateMerger
import com.ammiratv.core.model.StreamType
import com.ammiratv.core.numbering.CanonicalMatcher
import com.ammiratv.core.numbering.ChannelNumbering
import com.ammiratv.core.numbering.ChannelNumberingRepository
import com.ammiratv.core.numbering.NumberingInput
import com.ammiratv.core.numbering.NumberingResult
import com.ammiratv.core.numbering.RemoteUpdateResult
import com.ammiratv.domain.repository.CategoryRepository
import com.ammiratv.domain.repository.ChannelRepository
import com.ammiratv.domain.repository.ManualNumberRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the numbering screens need to know about the data in use. */
data class NumberingInfo(val version: Int, val channelCount: Int, val generatedAt: String)

/**
 * Runs the pure numbering algorithm (core:numbering) on a playlist's channels. Heavy work: call it
 * from a background scope. The matcher is cached until the data file's version changes.
 */
@Singleton
class NumberingEngine @Inject constructor(
    private val numberingRepository: ChannelNumberingRepository,
    private val channelRepository: ChannelRepository,
    private val categoryRepository: CategoryRepository,
    private val manualNumbers: ManualNumberRepository,
    private val preferences: AmmiraTvPreferences
) {
    /** Channels are not kept: only the ones a screen asks for (by id) are loaded afterwards. */
    class Computation(val result: NumberingResult)

    @Volatile private var cached: Pair<Int, CanonicalMatcher>? = null

    private suspend fun matcher(): CanonicalMatcher {
        val db = numberingRepository.database()
        cached?.let { (version, matcher) -> if (version == db.version) return matcher }
        return CanonicalMatcher(db).also { cached = db.version to it }
    }

    suspend fun info(): NumberingInfo {
        val db = numberingRepository.database()
        return NumberingInfo(db.version, db.channels.size, db.generatedAt)
    }

    suspend fun compute(playlistId: Long): Computation {
        val groupNames = categoryRepository.getCategoriesByPlaylist(playlistId).first()
            .associate { it.id to it.name }

        // Page by page: a 50k-channel playlist never sits in memory as full entities
        val inputs = ArrayList<NumberingInput>()
        channelRepository.forEachLiveChannelPage(playlistId) { page ->
            for (c in page) {
                inputs += NumberingInput(
                    id = c.id,
                    key = DuplicateMerger.keyOf(c),
                    rawName = c.name,
                    tvgId = c.tvgId,
                    tvgName = c.tvgName,
                    groupName = groupNames[c.categoryId] ?: c.groupTitle,
                    streamUrl = c.streamUrl,
                    isLive = c.streamType == StreamType.LIVE
                )
            }
        }
        val result = ChannelNumbering.assign(
            inputs = inputs,
            matcher = matcher(),
            manual = manualNumbers.all(),
            includeUnofficial = preferences.unofficialNumbers.first()
        )
        return Computation(result)
    }
}

/** Recomputes and stores the numbers of one playlist. Called after every import or refresh. */
class RecalculateChannelNumbersUseCase @Inject constructor(
    private val engine: NumberingEngine,
    private val channelRepository: ChannelRepository,
    private val preferences: AmmiraTvPreferences,
    private val numberingRepository: ChannelNumberingRepository
) {
    /** @return how many channels ended up with a number. */
    suspend operator fun invoke(playlistId: Long): Int {
        if (playlistId <= 0) return 0
        if (!preferences.numberingEnabled.first()) {
            channelRepository.replaceChannelNumbers(playlistId, emptyMap())
            return 0
        }
        val computed = engine.compute(playlistId)
        val numbers = computed.result.assignments.associate { it.channelId to it.number }
        channelRepository.replaceChannelNumbers(playlistId, numbers)
        preferences.setNumberingStamp(stamp(playlistId))
        return numbers.size
    }

    suspend fun stamp(playlistId: Long): String {
        val version = numberingRepository.database().version
        val enabled = preferences.numberingEnabled.first()
        val unofficial = preferences.unofficialNumbers.first()
        return "a$ALGORITHM_VERSION:d$version:p$playlistId:e$enabled:u$unofficial"
    }

    /** Recomputes only when the inputs of the stored numbers changed (app start). */
    suspend fun ensureCurrent(playlistId: Long) {
        if (playlistId <= 0) return
        if (preferences.numberingStamp.first() != stamp(playlistId)) invoke(playlistId)
    }

    private companion object {
        /** Bump when the matching rules change, so existing numbers are recomputed. */
        const val ALGORITHM_VERSION = 1
    }
}

/**
 * Runs recalculation on the application scope, one playlist at a time, so it survives the user
 * leaving the screen that triggered it and never races with itself.
 */
@Singleton
class NumberingCoordinator @Inject constructor(
    private val recalculate: RecalculateChannelNumbersUseCase,
    @com.ammiratv.core.common.ApplicationScope private val scope: kotlinx.coroutines.CoroutineScope
) {
    private val mutex = kotlinx.coroutines.sync.Mutex()

    fun recalculate(playlistId: Long) {
        scope.launch {
            mutex.withLock { runCatching { recalculate.invoke(playlistId) } }
        }
    }

    fun ensureCurrent(playlistId: Long) {
        scope.launch {
            mutex.withLock { runCatching { recalculate.ensureCurrent(playlistId) } }
        }
    }
}

/** Number typed on the remote -> the channel to tune, picking the best variant. */
class FindChannelByNumberUseCase @Inject constructor(
    private val channelRepository: ChannelRepository
) {
    suspend operator fun invoke(playlistId: Long, number: Int): Channel? {
        val candidates = channelRepository.getChannelsByNumber(playlistId, number)
        return ChannelNumbering.bestVariant(
            candidates = candidates,
            rawName = { it.name },
            isFavorite = { it.isFavorite },
            reliability = { it.reliability },
            order = { it.id }
        )
    }
}

/** A user-chosen number (or its removal) always beats the database. */
class SetManualChannelNumberUseCase @Inject constructor(
    private val manualNumbers: ManualNumberRepository,
    private val recalculate: RecalculateChannelNumbersUseCase
) {
    /** @param number null restores the original (catalogue or none). */
    suspend operator fun invoke(channel: Channel, number: Int?) {
        val key = DuplicateMerger.keyOf(channel)
        if (number == null) manualNumbers.remove(key) else manualNumbers.set(key, number)
        recalculate(channel.playlistId)
    }
}

/** Italian live channels the catalogue did not recognise: the "da verificare" list. */
class GetChannelsToVerifyUseCase @Inject constructor(
    private val engine: NumberingEngine,
    private val channelRepository: ChannelRepository
) {
    suspend operator fun invoke(playlistId: Long, limit: Int = 300): List<Channel> {
        val computed = engine.compute(playlistId)
        val manualKeys = computed.result.assignments
            .filter { it.origin == com.ammiratv.core.numbering.NumberOrigin.MANUAL }
            .map { it.channelId }
            .toSet()
        val candidates = computed.result.toVerify.filter { it !in manualKeys }
        val byId = channelRepository.getChannelsByIds(candidates).associateBy { it.id }
        return candidates
            .asSequence()
            .mapNotNull { byId[it] }
            .distinctBy { DuplicateMerger.keyOf(it) }
            .sortedBy { it.cleanName.lowercase() }
            .take(limit)
            .toList()
    }
}

class SearchNumberableChannelsUseCase @Inject constructor(
    private val channelRepository: ChannelRepository
) {
    suspend operator fun invoke(playlistId: Long, query: String): List<Channel> =
        channelRepository.searchChannels(query.trim())
            .filter { it.playlistId == playlistId && it.streamType == StreamType.LIVE }
            .distinctBy { DuplicateMerger.keyOf(it) }
}

class GetNumberingInfoUseCase @Inject constructor(private val engine: NumberingEngine) {
    suspend operator fun invoke(): NumberingInfo = engine.info()
}

/** Fetches a newer canonical database from the configured URL and recomputes on success. */
class UpdateNumberingDataUseCase @Inject constructor(
    private val numberingRepository: ChannelNumberingRepository,
    private val preferences: AmmiraTvPreferences,
    private val recalculate: RecalculateChannelNumbersUseCase
) {
    /** @return a one-line message for the user. */
    suspend operator fun invoke(): String {
        val url = preferences.numberingRemoteUrl.first()
        if (url.isBlank()) return "Nessun indirizzo di aggiornamento configurato"
        return when (val r = numberingRepository.updateFrom(url)) {
            is RemoteUpdateResult.Updated -> {
                recalculate(preferences.activePlaylistId.first())
                "Database aggiornato alla versione ${r.version}"
            }
            is RemoteUpdateResult.UpToDate -> "Gia' aggiornato (versione ${r.version})"
            is RemoteUpdateResult.Invalid -> "File non valido: ${r.errors.firstOrNull().orEmpty()}"
            is RemoteUpdateResult.Failed -> "Aggiornamento non riuscito: ${r.message}"
        }
    }
}
