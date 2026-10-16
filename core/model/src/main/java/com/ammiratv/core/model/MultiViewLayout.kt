package com.ammiratv.core.model

/**
 * A named multi-view arrangement ("Serie A: 4 partite"). Slots are channel ids in reading order;
 * an empty slot is null. Stored as one string so it fits in a DataStore string set.
 */
data class MultiViewLayout(
    val name: String,
    val channelIds: List<Long?>
) {
    /** `name|1,2,,4` — channel ids may be omitted for empty slots. The name can't contain '|'. */
    fun encode(): String =
        name.replace("|", "/").replace("\n", " ") + "|" + channelIds.joinToString(",") { it?.toString().orEmpty() }

    companion object {
        const val SLOTS = 4

        fun decode(raw: String): MultiViewLayout? {
            val sep = raw.lastIndexOf('|')
            if (sep <= 0) return null
            val name = raw.substring(0, sep).trim()
            if (name.isEmpty()) return null

            val parts = raw.substring(sep + 1).split(',')
            val ids = parts.map { it.trim().toLongOrNull() }
            // Always exactly SLOTS entries, so old/short layouts still load into a 2x2 grid
            val padded = (ids + List(SLOTS) { null }).take(SLOTS)
            return MultiViewLayout(name, padded)
        }
    }
}

/** How many simultaneous streams the provider allows us to open for the grid. */
object MultiViewBudget {
    /**
     * @param maxConnections provider limit, null if unknown
     * @return number of tiles that may play at once, between 1 and [MultiViewLayout.SLOTS]
     */
    fun maxTiles(maxConnections: Int?): Int =
        (maxConnections ?: MultiViewLayout.SLOTS).coerceIn(1, MultiViewLayout.SLOTS)
}
