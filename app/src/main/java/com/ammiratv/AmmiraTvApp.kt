package com.ammiratv

import android.app.Application
import com.ammiratv.core.common.ApplicationScope
import com.ammiratv.domain.repository.RecordingEngine
import com.ammiratv.domain.usecase.EpgSyncCoordinator
import com.ammiratv.domain.usecase.ManageProfilesUseCase
import com.ammiratv.domain.usecase.PlaylistRefreshCoordinator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application class for AmmiraTv.
 * @HiltAndroidApp triggers Hilt's code generation for dependency injection.
 */
@HiltAndroidApp
class AmmiraTvApp : Application(), coil.ImageLoaderFactory {

    // One shared image loader sized for the device: a 1 GB Fire TV Stick gets a small cache and
    // RGB565 bitmaps, a tablet gets more. Logos and posters are small, so this stays crisp.
    override fun newImageLoader(): coil.ImageLoader {
        val am = getSystemService(ACTIVITY_SERVICE) as? android.app.ActivityManager
        val lowRam = am?.isLowRamDevice == true
        return coil.ImageLoader.Builder(this)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(if (lowRam) 0.08 else 0.2)
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(if (lowRam) 50L * 1024 * 1024 else 150L * 1024 * 1024)
                    .build()
            }
            .allowRgb565(lowRam)
            .crossfade(!lowRam)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Background or critically low: give the image memory back; it reloads from the disk cache
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            runCatching { coil.Coil.imageLoader(this).memoryCache?.clear() }
        }
    }


    @Inject
    lateinit var epgSyncCoordinator: EpgSyncCoordinator

    @Inject
    lateinit var playlistRefreshCoordinator: PlaylistRefreshCoordinator

    @Inject
    lateinit var manageProfiles: ManageProfilesUseCase

    @Inject
    lateinit var recordingEngine: RecordingEngine

    @Inject
    lateinit var numberingCoordinator: com.ammiratv.domain.usecase.NumberingCoordinator

    @Inject
    lateinit var preferences: com.ammiratv.core.datastore.AmmiraTvPreferences

    @Inject
    lateinit var vpnManager: com.ammiratv.core.vpn.VpnManager

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            // Debug builds only: log work on the main thread and leaked resources
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build()
            )
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects()
                    .penaltyLog().build()
            )
        }
        // VPN first when the user asked for it, so the first playlist/guide downloads go through it
        appScope.launch { runCatching { vpnManager.connectIfAuto() } }
        appScope.launch {
            manageProfiles.ensureDefault()
            recordingEngine.rearmScheduled()
            // Numbers are stored with the channels; recompute only if their inputs changed
            numberingCoordinator.ensureCurrent(preferences.activePlaylistId.first())
        }
        // Background refresh: channel lists older than 24h, guides older than 12h.
        // Each runs one at a time so providers never see parallel downloads. On a weak box the
        // first seconds belong to the UI: the downloads wait until the screen has settled.
        val lowRam = (getSystemService(ACTIVITY_SERVICE) as? android.app.ActivityManager)?.isLowRamDevice == true
        appScope.launch {
            kotlinx.coroutines.delay(if (lowRam) 30_000L else 5_000L)
            playlistRefreshCoordinator.refreshStale()
            epgSyncCoordinator.syncStale()
        }
    }
}
