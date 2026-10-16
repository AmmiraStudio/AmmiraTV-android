package com.ammiratv.domain.repository

/** The numbers a user assigned by hand, keyed by channel identity (see DuplicateMerger.keyOf). */
interface ManualNumberRepository {
    suspend fun all(): Map<String, Int>
    suspend fun set(key: String, number: Int)
    suspend fun remove(key: String)
}
