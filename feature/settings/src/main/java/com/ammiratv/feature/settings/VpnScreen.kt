package com.ammiratv.feature.settings

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.ammiratv.core.ui.component.CategoryPill
import com.ammiratv.core.ui.theme.DarkBackground
import com.ammiratv.core.ui.theme.DarkSurface
import com.ammiratv.core.ui.theme.AmmiraTeal
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary
import com.ammiratv.core.vpn.VpnState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VpnScreen(
    onBackClick: () -> Unit,
    viewModel: VpnViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.vpnState.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val ui by viewModel.ui.collectAsState()
    val ovpn by viewModel.openVpnUi.collectAsState()
    val context = LocalContext.current
    // Coming back from the OpenVPN app: the installed client / profile may have changed
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refreshOpenVpnState() }

    BackHandler(onBack = onBackClick)

    // The system asks the user once to allow this app to create a VPN
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.connect()
    }
    fun connectWithConsent() {
        val intent = viewModel.consentIntent()
        if (intent == null) viewModel.connect() else runCatching { consent.launch(intent) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("VPN", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(
            "WireGuard (integrato): importa il file .conf del tuo provider; il tunnel protegge solo il " +
                "traffico di AmmiraTv. OpenVPN: vedi la sezione sotto. L'app non include nessun servizio VPN.",
            color = TextSecondary, fontSize = 13.sp
        )

        // Status
        val (label, color) = when (val s = state) {
            is VpnState.Connected -> "Connessa" to Color(0xFF4CAF50)
            is VpnState.Connecting -> "Connessione in corso…" to AmmiraTeal
            is VpnState.Disconnected -> "Non connessa" to TextSecondary
            is VpnState.Error -> s.message to Color(0xFFFF5252)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface, RoundedCornerShape(14.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                summary?.let { "Server: ${it.endpoint}  •  Indirizzo: ${it.address}" }
                    ?: "Nessuna configurazione importata",
                color = TextSecondary, fontSize = 12.sp
            )
        }

        ui.message?.let { Text(it, color = AmmiraTeal, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val connected = state is VpnState.Connected
            if (summary != null) {
                CategoryPill(
                    name = if (connected) "Disconnetti" else "Connetti",
                    isSelected = !connected,
                    onClick = { if (connected) viewModel.disconnect() else connectWithConsent() }
                )
            }
            CategoryPill("Importa dagli appunti", false, onClick = {
                val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                    ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                viewModel.importFromText(clip)
            })
            CategoryPill("Invia dal telefono (QR)", false, onClick = { viewModel.startPhoneImport() })
            if (summary != null) {
                CategoryPill("Rimuovi configurazione", false, onClick = { viewModel.remove() })
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface, RoundedCornerShape(14.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Connetti all'avvio", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "Riconnette la VPN ogni volta che apri l'app (serve aver già concesso il permesso una volta).",
                color = TextSecondary, fontSize = 12.sp
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryPill("Attivo", ui.autoConnect, onClick = { viewModel.setAutoConnect(true) })
                CategoryPill("Spento", !ui.autoConnect, onClick = { viewModel.setAutoConnect(false) })
            }
        }

        // ── OpenVPN: NordVPN, ProtonVPN, Surfshark… ──────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface, RoundedCornerShape(14.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("OpenVPN (NordVPN, ProtonVPN, Surfshark…)", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "Scarica dal tuo provider il file .ovpn e importalo qui. Serve l'app \"OpenVPN per Android\" " +
                    "o \"OpenVPN Connect\" installata: AmmiraTv le passa il profilo e la avvia. " +
                    "Con OpenVPN è tutto il dispositivo a passare dalla VPN, non solo AmmiraTv.",
                color = TextSecondary, fontSize = 12.sp
            )
            Text(
                (ovpn.client?.let { "App OpenVPN: $it" } ?: "App OpenVPN non installata") +
                    (ovpn.summary?.let { "  •  Profilo salvato: ${it.server}" } ?: "  •  Nessun profilo salvato"),
                color = if (ovpn.client != null) TextPrimary else Color(0xFFFFB74D),
                fontSize = 13.sp
            )
            ovpn.message?.let { Text(it, color = AmmiraTeal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryPill("Importa .ovpn dagli appunti", false, onClick = {
                    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                        ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    viewModel.importFromText(clip)
                })
                CategoryPill("Invia .ovpn dal telefono (QR)", false, onClick = { viewModel.startPhoneImport() })
                if (ovpn.summary != null) {
                    CategoryPill("Importa nell'app OpenVPN", false, onClick = { viewModel.openVpnImportToClient() })
                    CategoryPill("Connetti", true, onClick = { viewModel.openVpnConnect() })
                    CategoryPill("Disconnetti", false, onClick = { viewModel.openVpnDisconnect() })
                    CategoryPill("Rimuovi profilo", false, onClick = { viewModel.openVpnRemove() })
                }
                if (ovpn.client != null) {
                    CategoryPill("Apri app OpenVPN", false, onClick = { viewModel.openVpnOpenClient() })
                }
            }
        }
    }

    ui.phoneUrl?.let { url ->
        Dialog(onDismissRequest = { viewModel.stopPhoneImport() }) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(DarkSurface, RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Text("Scansiona con il telefono", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                QrCode(content = url, size = 220.dp)
                Spacer(Modifier.height(10.dp))
                Text(url, color = AmmiraTeal, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Incolla nella pagina il file .conf del provider. Telefono e dispositivo sulla stessa rete Wi-Fi.",
                    color = TextSecondary, fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                CategoryPill("Chiudi", false, onClick = { viewModel.stopPhoneImport() })
            }
        }
    }
}