package com.ammiratv.core.network.xtream

import com.ammiratv.core.model.Episode
import com.ammiratv.core.model.SeriesDetails
import com.ammiratv.core.model.VodCategory
import com.ammiratv.core.model.VodDetails
import com.ammiratv.core.model.VodItem
import com.ammiratv.core.model.VodKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Tolerant parser for the VOD/series half of the Xtream API.
 *
 * Providers are inconsistent: numbers arrive as strings, empty objects arrive as `[]`,
 * fields are missing. So nothing here uses strict `@Serializable` data classes.
 */
object XtreamVodParser {

    fun parseCategories(root: JsonElement?): List<VodCategory> =
        (root as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("category_id") ?: return@mapNotNull null
            VodCategory(id = id, name = o.str("category_name") ?: "Senza nome")
        }

    fun parseMovies(root: JsonElement?, playlistId: Long): List<VodItem> =
        (root as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.long("stream_id") ?: return@mapNotNull null
            VodItem(
                id = id,
                playlistId = playlistId,
                kind = VodKind.MOVIE,
                name = o.str("name") ?: return@mapNotNull null,
                posterUrl = o.str("stream_icon"),
                categoryId = o.str("category_id").orEmpty(),
                rating = o.str("rating"),
                containerExtension = o.str("container_extension") ?: "mp4",
                addedAt = o.long("added") ?: 0
            )
        }

    fun parseSeriesList(root: JsonElement?, playlistId: Long): List<VodItem> =
        (root as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.long("series_id") ?: return@mapNotNull null
            VodItem(
                id = id,
                playlistId = playlistId,
                kind = VodKind.SERIES,
                name = o.str("name") ?: return@mapNotNull null,
                posterUrl = o.str("cover"),
                categoryId = o.str("category_id").orEmpty(),
                rating = o.str("rating"),
                year = o.str("releaseDate")?.take(4),
                addedAt = o.long("last_modified") ?: 0
            )
        }

    /** `get_vod_info`: `{ "info": {...}, "movie_data": {...} }`. */
    fun parseVodInfo(root: JsonElement?): VodDetails {
        val info = (root as? JsonObject)?.obj("info") ?: return VodDetails()
        return detailsFrom(info)
    }

    /** `get_series_info`: `{ "info": {...}, "episodes": { "1": [ {...} ] }, "seasons": [...] }`. */
    fun parseSeriesInfo(root: JsonElement?): SeriesDetails {
        val obj = root as? JsonObject ?: return SeriesDetails(VodDetails(), emptyMap())
        val details = obj.obj("info")?.let(::detailsFrom) ?: VodDetails()

        val episodesNode = obj["episodes"]
        val bySeason = sortedMapOf<Int, MutableList<Episode>>()

        // Normal case: an object keyed by season number. Empty case: a JSON array.
        (episodesNode as? JsonObject)?.forEach { (seasonKey, list) ->
            (list as? JsonArray).orEmpty().forEach { epEl ->
                val e = epEl as? JsonObject ?: return@forEach
                val id = e.long("id") ?: return@forEach
                val season = e.long("season")?.toInt() ?: seasonKey.toIntOrNull() ?: 1
                val info = e.obj("info")
                bySeason.getOrPut(season) { mutableListOf() }.add(
                    Episode(
                        id = id,
                        season = season,
                        number = e.long("episode_num")?.toInt() ?: 0,
                        title = e.str("title") ?: "Episodio ${e.str("episode_num") ?: ""}".trim(),
                        plot = info?.str("plot"),
                        durationSecs = info?.long("duration_secs") ?: 0,
                        posterUrl = info?.str("movie_image"),
                        containerExtension = e.str("container_extension") ?: "mp4"
                    )
                )
            }
        }

        return SeriesDetails(
            details = details,
            seasons = bySeason.mapValues { (_, eps) -> eps.sortedBy { it.number } }
        )
    }

    private fun detailsFrom(info: JsonObject): VodDetails = VodDetails(
        plot = info.str("plot") ?: info.str("description"),
        cast = info.str("cast") ?: info.str("actors"),
        director = info.str("director"),
        genre = info.str("genre"),
        releaseDate = info.str("releasedate") ?: info.str("releaseDate"),
        durationLabel = info.str("duration"),
        rating = info.str("rating"),
        backdropUrl = (info["backdrop_path"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
            ?: info.str("backdrop") ?: info.str("movie_image") ?: info.str("cover"),
        trailerYoutubeId = (info.str("youtube_trailer") ?: info.str("trailer"))?.let(::youtubeId)
    )

    /** Accepts a bare id or a full youtube URL. */
    fun youtubeId(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        Regex("(?:v=|youtu\\.be/|embed/)([A-Za-z0-9_-]{11})").find(s)?.let { return it.groupValues[1] }
        return s.takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }
    }

    // ── tolerant accessors ───────────────────────────────────────────────

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun JsonObject.long(key: String): Long? =
        str(key)?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }

    /** Object child, or null (also when the provider sent `[]` instead of an object). */
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
}
