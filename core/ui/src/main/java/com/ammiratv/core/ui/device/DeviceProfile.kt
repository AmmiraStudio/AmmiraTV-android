package com.ammiratv.core.ui.device

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

enum class FormFactor { PHONE, TABLET, TV, FIRE_TV }

/** Width buckets, same breakpoints as Material's WindowSizeClass (600dp / 840dp). */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

/**
 * The single place that knows what kind of device the app runs on. Screens read this instead of
 * sprinkling `isTv` checks around.
 *
 * [formFactor], [hasTouch], [lowRam] and [maxVideoDecoders] are fixed for the process; the window
 * size can change at runtime (split screen, foldables) and is read through [LocalWidthClass].
 */
data class DeviceProfile(
    val formFactor: FormFactor,
    /** True when there is a touch screen the user can actually use. */
    val hasTouch: Boolean,
    /** ~1 GB class devices (entry Fire TV Stick, old phones): smaller caches, fewer decoders. */
    val lowRam: Boolean,
    /** How many simultaneous hardware H.264 decoders we expect to get; used to cap multi-view. */
    val maxVideoDecoders: Int,
    /** True when the system "remove animations" setting is on. */
    val reduceMotion: Boolean
) {
    val isTv: Boolean get() = formFactor == FormFactor.TV || formFactor == FormFactor.FIRE_TV
    val isFireTv: Boolean get() = formFactor == FormFactor.FIRE_TV
    val isPhone: Boolean get() = formFactor == FormFactor.PHONE
    val isTablet: Boolean get() = formFactor == FormFactor.TABLET

    companion object {
        val Default = DeviceProfile(FormFactor.PHONE, true, false, 2, false)

        fun detect(context: Context): DeviceProfile {
            val app = context.applicationContext
            val pm = app.packageManager
            val uiMode = app.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            val television = uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                pm.hasSystemFeature("amazon.hardware.fire_tv")
            val amazon = Build.MANUFACTURER.equals("Amazon", ignoreCase = true) ||
                pm.hasSystemFeature("amazon.hardware.fire_tv")
            val smallestWidthDp = app.resources.configuration.smallestScreenWidthDp

            val form = when {
                television && amazon -> FormFactor.FIRE_TV
                television -> FormFactor.TV
                smallestWidthDp >= 600 -> FormFactor.TABLET
                else -> FormFactor.PHONE
            }

            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val totalMemMb = runCatching {
                ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }.totalMem / (1024 * 1024)
            }.getOrDefault(0L)
            val lowRam = (am?.isLowRamDevice == true) || (totalMemMb in 1..2200)

            val hasTouch = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) && form != FormFactor.TV &&
                form != FormFactor.FIRE_TV

            val animationsOff = runCatching {
                Settings.Global.getFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }.getOrDefault(false)

            return DeviceProfile(
                formFactor = form,
                hasTouch = hasTouch,
                lowRam = lowRam,
                maxVideoDecoders = probeVideoDecoders(lowRam),
                reduceMotion = animationsOff
            )
        }

        /**
         * Asks the codec list how many H.264 decoder instances the hardware promises. Falls back to
         * a conservative guess when the platform cannot tell (API < 23 or no hardware decoder).
         */
        private fun probeVideoDecoders(lowRam: Boolean): Int {
            val fallback = if (lowRam) 2 else 4
            return runCatching {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return fallback
                val mime = "video/avc"
                val limits = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                    .filter { !it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, true) } }
                    .filter { !isSoftware(it) }
                    .map { it.getCapabilitiesForType(mime).maxSupportedInstances }
                    .filter { it > 0 }
                val best = limits.maxOrNull() ?: return fallback
                best.coerceIn(1, if (lowRam) 2 else 6)
            }.getOrDefault(fallback)
        }

        private fun isSoftware(info: MediaCodecInfo): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return info.isSoftwareOnly
            val n = info.name.lowercase()
            return n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.contains("sw")
        }
    }
}

val LocalDeviceProfile = compositionLocalOf { DeviceProfile.Default }

/** Reads the current window width bucket; recomposes on rotation, split screen and fold changes. */
@Composable
fun rememberWidthClass(): WidthClass {
    val width = LocalConfiguration.current.screenWidthDp
    return when {
        width < 600 -> WidthClass.COMPACT
        width < 840 -> WidthClass.MEDIUM
        else -> WidthClass.EXPANDED
    }
}

val LocalWidthClass = compositionLocalOf { WidthClass.COMPACT }

@Composable
fun rememberDeviceProfile(): DeviceProfile {
    val context = LocalContext.current
    return remember(context) { DeviceProfile.detect(context) }
}
