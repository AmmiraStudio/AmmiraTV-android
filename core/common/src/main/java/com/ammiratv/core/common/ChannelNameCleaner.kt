package com.ammiratv.core.common

import java.util.Locale
import java.util.regex.Pattern

/**
 * Intelligent Channel Name Cleaner and Normalizer.
 *
 * IPTV playlists often include cluttered names such as:
 * - "IT: Rai 1 HD (BACKUP)"
 * - "|IT| Canale 5 FHD [1080p]"
 * - "US | ESPN 4K UHD"
 *
 * This cleaner cleans prefixes, quality tags, and backup indicators
 * while retaining the canonical channel name for EPG matching and display.
 */
object ChannelNameCleaner {

    // Regex for country code prefixes: "IT:", "IT -", "IT |", "[IT]", "|IT|", "ITA:"
    private val COUNTRY_PREFIX_BRACKET = Pattern.compile("^\\[[A-Za-z]{2,4}\\]\\s*|^\\|[A-Za-z]{2,4}\\|\\s*")
    private val COUNTRY_PREFIX_COLON = Pattern.compile("^[A-Za-z]{2,4}\\s*[:|\\-]\\s*")

    // Quality, resolution and codec tokens
    private val QUALITY_TOKENS = Pattern.compile(
        "(?i)\\b(4K|UHD|FHD|HD|SD|HEVC|H\\.?265|H\\.?264|1080p|1080i|720p|576p|50fps|60fps|RAW|DIRECT|VIP)\\b"
    )

    // Backup / alternate stream indicators
    private val BACKUP_TOKENS = Pattern.compile(
        "(?i)\\b(BACKUP|BKP|ALT|OPZIONE|FEED|TEST)\\b"
    )

    // Empty brackets left behind: (), [], {}
    private val EMPTY_BRACKETS = Pattern.compile("[\\[\\(\\{]\\s*[\\]\\)\\}]")

    // Dangling punctuation at start or end
    private val LEADING_TRAILING_PUNCTUATION = Pattern.compile("^[\\s|:\\-*#~+._]+|[\\s|:\\-*#~+._]+$")

    // Multiple spaces
    private val MULTI_SPACE = Pattern.compile("\\s{2,}")

    /**
     * Cleans a raw channel name.
     * Example: "IT: RAI 1 HD [1080p]" -> "Rai 1"
     */
    fun clean(rawName: String): String {
        if (rawName.isBlank()) return rawName

        var name = rawName.trim()

        // 1. Remove bracketed country codes: [IT], |IT|
        name = COUNTRY_PREFIX_BRACKET.matcher(name).replaceFirst("")

        // 2. Remove prefixed country codes: IT:, IT -, IT |
        name = COUNTRY_PREFIX_COLON.matcher(name).replaceFirst("")

        // 3. Remove quality tokens: FHD, HD, 4K, 1080p, HEVC
        name = QUALITY_TOKENS.matcher(name).replaceAll("")

        // 4. Remove backup tokens
        name = BACKUP_TOKENS.matcher(name).replaceAll("")

        // 5. Remove empty brackets
        name = EMPTY_BRACKETS.matcher(name).replaceAll("")
        name = EMPTY_BRACKETS.matcher(name).replaceAll("") // Second pass for nested

        // 6. Clean dangling punctuation
        name = LEADING_TRAILING_PUNCTUATION.matcher(name).replaceAll("")

        // 7. Collapse multiple spaces
        name = MULTI_SPACE.matcher(name).replaceAll(" ").trim()

        // 8. If empty after cleaning, fallback to raw trimmed name
        if (name.isBlank()) {
            return rawName.trim()
        }

        return formatCapitalization(name)
    }

    /**
     * Extracts quality badge if present (e.g. "4K", "FHD", "HD", "HEVC").
     */
    fun extractQuality(rawName: String): String? {
        val upper = rawName.uppercase(Locale.ROOT)
        return when {
            upper.contains("4K") || upper.contains("UHD") -> "4K"
            upper.contains("FHD") || upper.contains("1080") -> "FHD"
            upper.contains("HD") || upper.contains("720") -> "HD"
            upper.contains("SD") || upper.contains("576") -> "SD"
            upper.contains("HEVC") || upper.contains("H265") -> "HEVC"
            else -> null
        }
    }

    /**
     * Formats casing to Title Case when uppercase text is found.
     * Keeps common acronyms (e.g., BBC, CNN, MTV, HBO, TG, TV) in uppercase.
     */
    private fun formatCapitalization(input: String): String {
        val uppercaseAcronyms = setOf("BBC", "CNN", "MTV", "HBO", "TG", "TV", "MAX", "TNT", "NBA", "NFL", "F1", "WWE")

        val words = input.split(" ")
        return words.joinToString(" ") { word ->
            val upperWord = word.uppercase(Locale.ROOT)
            if (upperWord in uppercaseAcronyms) {
                upperWord
            } else if (word.length > 1 && word.all { it.isUpperCase() }) {
                // Convert ALL-CAPS words to TitleCase: "CINEMA" -> "Cinema"
                word.lowercase(Locale.ROOT).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                }
            } else {
                word
            }
        }
    }
}
