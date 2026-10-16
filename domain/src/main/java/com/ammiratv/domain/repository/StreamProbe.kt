package com.ammiratv.domain.repository

/**
 * Cheap liveness check for a stream URL: connect and read the first bytes, nothing more.
 * Implemented in :data with OkHttp.
 */
interface StreamProbe {
    /** True when the server answered 2xx and delivered data within the timeout. */
    suspend fun isAlive(url: String): Boolean
}
