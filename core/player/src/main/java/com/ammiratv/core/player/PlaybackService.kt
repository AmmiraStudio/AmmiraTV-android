package com.ammiratv.core.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PlaybackEntryPoint {
    fun engine(): Media3PlayerEngine
}

/**
 * Foreground media service: keeps audio playing with the screen off or the app in the background,
 * shows the system media notification and routes headset / Bluetooth / lock-screen buttons to the
 * player. It only publishes the session the engine already owns; the engine controls playback.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        // A failure here must never take the app down: playback works without the notification
        return runCatching {
            EntryPointAccessors
                .fromApplication(applicationContext, PlaybackEntryPoint::class.java)
                .engine()
                .mediaSession
        }.getOrNull()
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // Swiping the app away must not leave a notification playing a stream nobody can see
        EntryPointAccessors
            .fromApplication(applicationContext, PlaybackEntryPoint::class.java)
            .engine()
            .pause()
        stopSelf()
    }
}