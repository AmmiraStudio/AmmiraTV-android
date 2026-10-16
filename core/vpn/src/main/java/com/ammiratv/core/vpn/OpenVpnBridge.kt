package com.ammiratv.core.vpn

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What the user can see about the stored OpenVPN profile (never credentials or keys). */
data class OpenVpnSummary(val server: String)

/**
 * OpenVPN through an OpenVPN client app already on the device.
 *
 * Why not built in: the open-source OpenVPN engines for Android are GPL/AGPL and need a native
 * toolchain, which would also force the whole app under that licence. WireGuard (built in, see
 * [VpnManager]) has none of these problems. So for the providers that only hand out .ovpn files
 * (NordVPN, ProtonVPN, Surfshark, ...) this class stores the profile, passes it to the OpenVPN
 * app the user installed, and starts / stops it there.
 *
 * Supported clients: "OpenVPN for Android" (de.blinkt.openvpn: connect and disconnect from here)
 * and "OpenVPN Connect" (net.openvpn.openvpn: profile import, the connection is made in that app).
 */
@Singleton
class OpenVpnBridge @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val profileFile get() = File(context.noBackupFilesDir, "profile.ovpn")

    val hasProfile: Boolean get() = profileFile.exists()

    /** Which OpenVPN client is installed, or null. The one that can be driven is preferred. */
    fun clientPackage(): String? =
        listOf(ICS, CONNECT).firstOrNull { isInstalled(it) }

    fun clientName(): String? = when (clientPackage()) {
        ICS -> "OpenVPN per Android"
        CONNECT -> "OpenVPN Connect"
        else -> null
    }

    fun summary(): OpenVpnSummary? {
        val text = runCatching { profileFile.readText() }.getOrNull() ?: return null
        val remote = text.lines().map { it.trim() }
            .firstOrNull { it.startsWith("remote ", ignoreCase = true) }
            ?.split(Regex("\\s+"))?.getOrNull(1)
        return OpenVpnSummary(remote ?: "-")
    }

    /** Looks like an OpenVPN client profile (not WireGuard, not something else). */
    fun looksLikeOpenVpn(text: String): Boolean {
        val lower = text.lowercase()
        return !lower.contains("[interface]") &&
            lower.lines().any { it.trim().startsWith("remote ") } &&
            (lower.contains("client") || lower.contains("<ca>") || lower.contains("dev tun"))
    }

    /** @return null when stored, otherwise a message for the user. */
    fun store(text: String): String? {
        if (!looksLikeOpenVpn(text)) return "Non sembra un file OpenVPN (.ovpn): manca la riga \"remote\""
        profileFile.writeText(text.trim() + "\n")
        return null
    }

    fun remove() {
        profileFile.delete()
    }

    /** Opens the stored profile in the OpenVPN app so it can import it. @return error text or null. */
    fun openInClient(): String? {
        if (!hasProfile) return "Nessun profilo OpenVPN salvato"
        if (clientPackage() == null) return NOT_INSTALLED
        return try {
            val dir = File(context.cacheDir, "ovpn").apply { mkdirs() }
            val shared = File(dir, "$PROFILE_NAME.ovpn")
            profileFile.copyTo(shared, overwrite = true)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.vpnfiles", shared)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/x-openvpn-profile")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                clientPackage()?.let { setPackage(it) }
            }
            context.startActivity(intent)
            null
        } catch (_: ActivityNotFoundException) {
            NOT_INSTALLED
        } catch (e: Exception) {
            "Impossibile aprire il profilo: ${e.message?.take(100)}"
        }
    }

    /** Starts the profile named [PROFILE_NAME] (imported before). @return error text or null. */
    fun connect(): String? {
        return when (clientPackage()) {
            ICS -> launch(
                Intent(Intent.ACTION_MAIN)
                    .setComponent(ComponentName(ICS, "de.blinkt.openvpn.LaunchVPN"))
                    .putExtra("de.blinkt.openvpn.shortcutProfileName", PROFILE_NAME)
            )
            CONNECT -> openClient()
            else -> NOT_INSTALLED
        }
    }

    fun disconnect(): String? {
        return when (clientPackage()) {
            ICS -> launch(
                Intent(Intent.ACTION_MAIN)
                    .setComponent(ComponentName(ICS, "de.blinkt.openvpn.activities.DisconnectVPN"))
            )
            CONNECT -> openClient()
            else -> NOT_INSTALLED
        }
    }

    fun openClient(): String? {
        val pkg = clientPackage() ?: return NOT_INSTALLED
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return NOT_INSTALLED
        return launch(intent)
    }

    private fun launch(intent: Intent): String? = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    } catch (_: ActivityNotFoundException) {
        "Apri l'app OpenVPN e importa prima il profilo \"$PROFILE_NAME\""
    } catch (e: SecurityException) {
        "L'app OpenVPN non consente il controllo da altre app: usa il suo pulsante di connessione"
    }

    private fun isInstalled(pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        private const val ICS = "de.blinkt.openvpn"
        private const val CONNECT = "net.openvpn.openvpn"
        /** Name the profile gets in the OpenVPN app (taken from the imported file name). */
        const val PROFILE_NAME = "AmmiraTv"
        const val NOT_INSTALLED =
            "Installa l'app \"OpenVPN per Android\" (F-Droid / Play Store) o \"OpenVPN Connect\", poi riprova"
    }
}