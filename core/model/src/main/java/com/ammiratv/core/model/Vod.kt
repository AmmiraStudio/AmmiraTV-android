package com.ammiratv.core.model

enum class VodKind { MOVIE, SERIES }

/** A VOD/series category as the provider names it. [id] is the provider's id (unique per playlist only). */
data class VodCategory(
    val id: String,
    val name: String
)

/** A movie or a series in the catalogue. [id] is the provider's stream_id / series_id. */
data class VodItem(
    val id: Long,
    val playlistId: Long,
    val kind: VodKind,
    val name: String,
    val posterUrl: String? = null,
    val categoryId: String = "",
    val rating: String? = null,
    val year: String? = null,
    /** File extension for movies (mkv, mp4…); unused for series. */
    val containerExtension: String? = null,
    val addedAt: Long = 0
)

data class VodDetails(
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val durationLabel: String? = null,
    val rating: String? = null,
    val backdropUrl: String? = null,
    /** YouTube video id of the trailer, if the provider gives one. */
    val trailerYoutubeId: String? = null
)

data class Episode(
    val id: Long,
    val season: Int,
    val number: Int,
    val title: String,
    val plot: String? = null,
    val durationSecs: Long = 0,
    val posterUrl: String? = null,
    val containerExtension: String = "mp4"
)

data class SeriesDetails(
    val details: VodDetails,
    /** Episodes grouped by season number, both in ascending order. */
    val seasons: Map<Int, List<Episode>>
)

/** Stored playback position for a movie/episode of one profile. */
data class WatchProgress(
    val profileId: Long,
    val contentKey: String,
    val kind: VodKind,
    val playlistId: Long,
    val title: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val streamUrl: String,
    val seriesId: Long? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isWatched: Boolean = false,
    val updatedAt: Long = 0
) {
    /** 0..1, for the progress bar on a poster. */
    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /**
     * Progress after playing up to [positionMs]. Reaching the end marks the title watched and
     * resets the position, so the next play starts from the beginning instead of the credits.
     */
    fun withPosition(positionMs: Long, durationMs: Long, nowMs: Long): WatchProgress {
        val finished = durationMs > 0 && positionMs.toFloat() / durationMs >= WATCHED_THRESHOLD
        return copy(
            positionMs = if (finished) 0 else positionMs,
            durationMs = durationMs,
            isWatched = finished,
            updatedAt = nowMs
        )
    }

    companion object {
        /** Anything shorter than this is a mis-click, not a viewing worth remembering. */
        const val MIN_POSITION_MS = 10_000L

        fun movieKey(playlistId: Long, streamId: Long) = "movie:$playlistId:$streamId"
        fun episodeKey(playlistId: Long, episodeId: Long) = "episode:$playlistId:$episodeId"

        /** Past this fraction a title counts as finished (end credits). */
        const val WATCHED_THRESHOLD = 0.92f
    }
}
