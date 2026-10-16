package com.ammiratv.core.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface VpnState {
    data object Disconnected : VpnState
    data object Connecting : VpnState
    data class Connected(val sinceMs: Long) : VpnState
    data class Error(val message: String) : VpnState
}

/** What is safe to show about the stored configuration (never keys). */
data class VpnConfigSummary(val endpoint: String, val address: String)

/**
 * Built-in VPN, WireGuard based.
 *
 * - The tunnel carries **only this app's traffic** (IncludedApplications), so playlists, guides
 *   and streams go through the VPN while the rest of the device is untouched.
 * - The configuration (it contains a private key) is kept in the app's no-backup storage and is
 *   never logged or shown.
 * - Nothing is bundled: no server list, no account.
 */
@Singleton
class VpnManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()
    private val backend by lazy { GoBackend(context) }
    private val configFile get() = File(context.noBackupFilesDir, "vpn.conf")
    private val prefs get() = context.getSharedPreferences("vpn", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<VpnState>(VpnState.Disconnected)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    private val _summary = MutableStateFlow(loadSummary())
    val summary: StateFlow<VpnConfigSummary?> = _summary.asStateFlow()

    private val tunnel = object : Tunnel {
        override fun getName() = "ammiratv"
        override fun onStateChange(newState: Tunnel.State) {
            // The system can also take the tunnel down (another VPN started, permission revoked)
            if (newState == Tunnel.State.DOWN && _state.value is VpnState.Connected) {
                _state.value = VpnState.Disconnected
            }
        }
    }

    var autoConnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO, false)
        set(value) { prefs.edit().putBoolean(KEY_AUTO, value).apply() }

    val hasConfig: Boolean get() = configFile.exists()

    /** Non-null when the system must first ask the user to allow a VPN (launch it for a result). */
    fun consentIntent(): Intent? = VpnService.prepare(context)

    /**
     * Validates [text] as a WireGuard configuration and stores it, restricted to this app.
     * @return null on success, otherwise a message for the user.
     */
    suspend fun importConfig(text: String): String? = withContext(Dispatchers.IO) {
        val prepared = restrictToThisApp(text.trim())
        try {
            prepared.byteInputStream().use { Config.parse(it) }
        } catch (e: Exception) {
            return@withContext "Configurazione non valida: ${e.message?.take(120) ?: "formato non riconosciuto"}"
        }
        configFile.writeText(prepared)
        _summary.value = loadSummary()
        null
    }

    suspend fun removeConfig() = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
            configFile.delete()
            _summary.value = null
            _state.value = VpnState.Disconnected
        }
    }

    suspend fun connect() = mutex.withLock {
        if (_state.value is VpnState.Connected) return@withLock
        val text = runCatching { configFile.readText() }.getOrNull()
        if (text == null) {
            _state.value = VpnState.Error("Nessuna configurazione VPN importata")
            return@withLock
        }
        _state.value = VpnState.Connecting
        withContext(Dispatchers.IO) {
            try {
                val config = text.byteInputStream().use { Config.parse(it) }
                backend.setState(tunnel, Tunnel.State.UP, config)
                _state.value = VpnState.Connected(System.currentTimeMillis())
            } catch (e: Exception) {
                _state.value = VpnState.Error(describe(e))
            }
        }
    }

    suspend fun disconnect() = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
            _state.value = VpnState.Disconnected
        }
    }

    /** App start: reconnect if the user asked for it and the system permission is already granted. */
    suspend fun connectIfAuto() {
        if (autoConnect && hasConfig && consentIntent() == null) connect()
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private fun describe(e: Exception): String = when {
        e.message?.contains("NOT_AUTHORIZED", true) == true ||
            e.message?.contains("authorized", true) == true -> "Permesso VPN non concesso"
        else -> "Connessione VPN non riuscita: ${e.message?.take(120) ?: e::class.java.simpleName}"
    }

    /** Adds `IncludedApplications = <this app>` so the tunnel never captures other apps. */
    private fun restrictToThisApp(text: String): String {
        val lines = text.lines().filterNot { it.trim().startsWith("IncludedApplications", ignoreCase = true) ||
            it.trim().startsWith("ExcludedApplications", ignoreCase = true) }.toMutableList()
        val at = lines.indexOfFirst { it.trim().equals("[Interface]", ignoreCase = true) }
        if (at >= 0) lines.add(at + 1, "IncludedApplications = ${context.packageName}")
        return lines.joinToString("\n")
    }

    private fun loadSummary(): VpnConfigSummary? {
        val text = runCatching { configFile.readText() }.getOrNull() ?: return null
        fun value(key: String) = text.lines()
            .firstOrNull { it.trim().startsWith(key, ignoreCase = true) }
            ?.substringAfter('=')?.trim().orEmpty()
        return VpnConfigSummary(
            endpoint = value("Endpoint").ifEmpty { "-" },
            address = value("Address").ifEmpty { "-" }
        )
    }

    private companion object {
        const val KEY_AUTO = "auto_connect"
    }
}