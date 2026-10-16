package com.ammiratv.core.common

import java.util.Locale

/**
 * Keyword heuristic used by child profiles to hide adult groups and channels.
 * Playlists carry no content rating, so this is deliberately conservative: a false positive
 * hides a channel from kids, a false negative is why the settings are PIN-protected.
 */
object AdultContentFilter {

    // Whole-word matches; "xxx" and "18+" are the usual markers, the rest are common group names.
    private val ADULT_PATTERN = Regex(
        "(?i)(?<![a-z0-9])(xxx|adult[io]?|adults|porn\\w*|erotic\\w*|hentai|sex\\w*|playboy|hustler|18\\s*\\+|\\+\\s*18|vm\\s*18)(?![a-z0-9])"
    )

    fun isAdult(vararg labels: String?): Boolean =
        labels.any { label ->
            !label.isNullOrBlank() && ADULT_PATTERN.containsMatchIn(label.lowercase(Locale.ROOT))
        }
}
