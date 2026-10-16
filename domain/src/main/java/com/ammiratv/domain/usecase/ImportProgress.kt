package com.ammiratv.domain.usecase

sealed interface ImportProgress {
    data class Loading(val message: String) : ImportProgress
    data class Parsing(val count: Int) : ImportProgress

    /**
     * @param epgUrl XMLTV guide advertised by the source (Xtream `xmltv.php` or the M3U
     * `url-tvg` header), if any. The caller decides when to download it.
     */
    data class Success(
        val playlistId: Long,
        val totalChannels: Int,
        val epgUrl: String? = null
    ) : ImportProgress

    data class Error(val message: String) : ImportProgress
}
