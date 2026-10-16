package com.ammiratv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammiratv.core.network.localimport.LocalImportServer
import com.ammiratv.core.vpn.OpenVpnBridge
import com.ammiratv.core.vpn.OpenVpnSummary
import com.ammiratv.core.vpn.VpnConfigSummary
import com.ammiratv.core.vpn.VpnManager
import com.ammiratv.core.vpn.VpnState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OpenVpnUi(
    val summary: OpenVpnSummary? = null,
    /** Name of the installed OpenVPN client app, or null when there is none. */
    val client: String? = null,
    val message: String? = null
)

data class VpnUiState(
    val message: String? = null,
    val phoneUrl: String? = null,
    val autoConnect: Boolean = false
)

@HiltViewModel
class VpnViewModel @Inject constructor(
    private val vpn: VpnManager,
    private val server: LocalImportServer,
    private val openVpn: OpenVpnBridge
) : ViewModel() {

    // ── OpenVPN (through an installed OpenVPN client) ────────────────────
    private val _openVpn = MutableStateFlow(
        OpenVpnUi(summary = openVpn.summary(), client = openVpn.clientName())
    )
    val openVpnUi: StateFlow<OpenVpnUi> = _openVpn.asStateFlow()

    private fun refreshOpenVpn(message: String? = null) {
        _openVpn.value = OpenVpnUi(summary = openVpn.summary(), client = openVpn.clientName(), message = message)
    }

    fun openVpnImportToClient() = refreshOpenVpn(openVpn.openInClient() ?: "Profilo inviato all\u0027app OpenVPN: conferma l\u0027importazione lì")
    fun openVpnConnect() = refreshOpenVpn(openVpn.connect())
    fun openVpnDisconnect() = refreshOpenVpn(openVpn.disconnect())
    fun openVpnOpenClient() = refreshOpenVpn(openVpn.openClient())
    fun openVpnRemove() {
        openVpn.remove()
        refreshOpenVpn("Profilo OpenVPN rimosso")
    }
    fun refreshOpenVpnState() = refreshOpenVpn(_openVpn.value.message)

    val vpnState: StateFlow<VpnState> = vpn.state
    val summary: StateFlow<VpnConfigSummary?> = vpn.summary

    private val _ui = MutableStateFlow(VpnUiState(autoConnect = vpn.autoConnect))
    val ui: StateFlow<VpnUiState> = _ui.asStateFlow()

    private var serverJob: Job? = null

    fun consentIntent() = vpn.consentIntent()

    fun connect() = viewModelScope.launch { vpn.connect() }
    fun disconnect() = viewModelScope.launch { vpn.disconnect() }

    fun remove() = viewModelScope.launch {
        vpn.removeConfig()
        _ui.update { it.copy(message = "Configurazione VPN rimossa") }
    }

    fun setAutoConnect(value: Boolean) {
        vpn.autoConnect = value
        _ui.update { it.copy(autoConnect = value) }
    }

    fun importFromText(text: String) {
        if (text.isBlank()) {
            _ui.update { it.copy(message = "Gli appunti sono vuoti: copia prima il file .conf del provider") }
            return
        }
        // The file tells which kind it is: .ovpn (OpenVPN) or [Interface]/[Peer] (WireGuard)
        if (openVpn.looksLikeOpenVpn(text)) {
            val error = openVpn.store(text)
            refreshOpenVpn(error ?: "Profilo OpenVPN salvato. Premi \"Importa nell\u0027app OpenVPN\".")
            return
        }
        viewModelScope.launch {
            val error = vpn.importConfig(text)
            _ui.update { it.copy(message = error ?: "Configurazione importata. Ora puoi connetterti.") }
        }
    }

    /** QR code: the phone opens a page, pastes the .conf there, and it lands here. */
    fun startPhoneImport() {
        if (serverJob?.isActive == true) return
        val ip = LocalImportServer.localIpAddress()
        if (ip == null) {
            _ui.update { it.copy(message = "Nessuna rete locale trovata: collega il dispositivo al Wi-Fi") }
            return
        }
        val token = server.newToken()
        serverJob = viewModelScope.launch {
            try {
                server.serve(
                    token = token,
                    vpnForm = true,
                    onReady = { port -> _ui.update { it.copy(phoneUrl = "http://$ip:$port/$token") } },
                    onRequest = { request ->
                        stopPhoneImport()
                        importFromText(request.url)
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(message = "Impossibile avviare l'importazione dal telefono") }
            } finally {
                _ui.update { it.copy(phoneUrl = null) }
            }
        }
    }

    fun stopPhoneImport() {
        serverJob?.cancel()
        serverJob = null
    }

    override fun onCleared() {
        stopPhoneImport()
        super.onCleared()
    }
}