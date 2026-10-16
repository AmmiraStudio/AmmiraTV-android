package com.ammiratv.core.numbering

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One national TV channel as it is numbered on the real platform.
 *
 * Metadata only: names, numbers and aliases. No stream, no link to content, no logo.
 */
@Serializable
data class CanonicalChannel(
    val id: String,
    val name: String,
    val number: Int,
    /** "dtt" (digitale terrestre) or "sky". */
    val platform: String,
    val aliases: List<String> = emptyList(),
    val tvgIds: List<String> = emptyList(),
    /** Key into [CanonicalDatabase.sources]. */
    val source: String,
    /** ISO date on which the number was checked against the source. */
    val verifiedAt: String
)

@Serializable
data class CanonicalSource(
    val url: String,
    val note: String = ""
)

/** A country's channel-numbering data file. Adding another country means adding another file. */
@Serializable
data class CanonicalDatabase(
    val version: Int,
    val country: String,
    @SerialName("generatedAt") val generatedAt: String = "",
    val sources: Map<String, CanonicalSource> = emptyMap(),
    val channels: List<CanonicalChannel>
)

/** Outcome of validating a data file. [database] is null when [errors] is not empty. */
data class ValidationResult(
    val database: CanonicalDatabase?,
    val errors: List<String>,
    /** Channels sharing a number inside the file: reported, never silently resolved. */
    val numberConflicts: Map<Int, List<String>>
) {
    val isValid: Boolean get() = database != null && errors.isEmpty()
}

object CanonicalDatabaseParser {

    private val json = Json { ignoreUnknownKeys = true }

    private val PLATFORMS = setOf("dtt", "sky")
    private val URL_LIKE = Regex("""(?i)(https?://|www\.|\.m3u8?|rtmp://)""")

    /**
     * Parses and validates [text]. A file is rejected (so the caller keeps its last good version)
     * if it is not valid JSON, has duplicate ids, non-positive numbers, an unknown platform, a
     * missing source, or anything that looks like a stream link inside names/aliases.
     */
    fun parse(text: String): ValidationResult {
        val db = try {
            json.decodeFromString(CanonicalDatabase.serializer(), text)
        } catch (e: Exception) {
            return ValidationResult(null, listOf("JSON non valido: ${e.message}"), emptyMap())
        }

        val errors = mutableListOf<String>()
        if (db.version < 1) errors += "version deve essere >= 1"
        if (db.channels.isEmpty()) errors += "nessun canale"

        val seenIds = HashSet<String>()
        for (c in db.channels) {
            if (c.id.isBlank()) errors += "canale senza id"
            if (!seenIds.add(c.id)) errors += "id duplicato: ${c.id}"
            if (c.name.isBlank()) errors += "${c.id}: nome vuoto"
            if (c.number <= 0) errors += "${c.id}: numero non valido (${c.number})"
            if (c.platform !in PLATFORMS) errors += "${c.id}: piattaforma sconosciuta (${c.platform})"
            if (c.source !in db.sources) errors += "${c.id}: fonte mancante (${c.source})"
            if (c.verifiedAt.isBlank()) errors += "${c.id}: verifiedAt mancante"
            if ((listOf(c.name) + c.aliases).any { URL_LIKE.containsMatchIn(it) }) {
                errors += "${c.id}: nome o alias sembrano un link"
            }
        }

        val conflicts = db.channels.groupBy { it.number }
            .filterValues { it.size > 1 }
            .mapValues { (_, list) -> list.map { it.id } }

        return ValidationResult(if (errors.isEmpty()) db else null, errors, conflicts)
    }
}
