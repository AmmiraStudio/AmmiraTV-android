package com.ammiratv.core.model

/** Playback preferences remembered for one channel ("profili per canale"). Keyed by stream URL. */
data class ChannelSetting(
    val streamUrl: String,
    val aspectRatio: AspectRatio? = null,
    val audioLanguage: String? = null,
    val subtitleLanguage: String? = null
) {
    val isEmpty: Boolean get() = aspectRatio == null && audioLanguage == null && subtitleLanguage == null
}
