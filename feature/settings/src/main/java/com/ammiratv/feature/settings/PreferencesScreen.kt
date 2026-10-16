package com.ammiratv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.ammiratv.core.ui.component.CategoryPill
import com.ammiratv.core.ui.theme.DarkBackground
import com.ammiratv.core.ui.theme.DarkSurface
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary

/** Language codes as ExoPlayer reports them (ISO 639-1/2 mixed); both forms are listed. */
private val LANGUAGES = listOf(
    "Nessuna" to "",
    "Italiano" to "it",
    "Inglese" to "en",
    "Francese" to "fr",
    "Tedesco" to "de",
    "Spagnolo" to "es",
    "Portoghese" to "pt",
    "Arabo" to "ar"
)

private val BUFFERS = listOf(
    "Zapping veloce (1 s)" to 1000,
    "Standard (2 s)" to 2000,
    "Stabile (5 s)" to 5000,
    "Connessione debole (10 s)" to 10000
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferencesScreen(
    onBackClick: () -> Unit,
    viewModel: PreferencesViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val audio by viewModel.audioLanguage.collectAsState()
    val subtitle by viewModel.subtitleLanguage.collectAsState()
    val cinema by viewModel.cinemaMode.collectAsState()
    val buffer by viewModel.bufferMs.collectAsState()
    val adaptive by viewModel.adaptiveBuffer.collectAsState()
    val merge by viewModel.mergeDuplicates.collectAsState()
    val official by viewModel.officialFallback.collectAsState()
    val background by viewModel.backgroundPlayback.collectAsState()
    val fontScale by viewModel.fontScale.collectAsState()
    val volumeBoost by viewModel.volumeBoost.collectAsState()

    BackHandler(onBack = onBackClick)

    // Backup goes through the system file picker (Storage Access Framework): no storage permission
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var backupMessage by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<String?>(null)
    }
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            backupMessage = try {
                val text = viewModel.exportBackup()
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                "Backup salvato"
            } catch (e: java.io.IOException) {
                "Salvataggio non riuscito: ${e.message}"
            }
        }
    }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            val text = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            } catch (e: java.io.IOException) {
                null
            }
            if (text == null) {
                backupMessage = "Impossibile leggere il file"
            } else {
                viewModel.importBackup(text) { ok ->
                    backupMessage = if (ok) "Impostazioni ripristinate" else "File di backup non valido"
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Preferenze", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

        Section("Lingua audio preferita", "Selezionata da sola all'avvio di ogni film e serie") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LANGUAGES.forEach { (label, code) ->
                    CategoryPill(label, audio == code, { viewModel.setAudioLanguage(code) })
                }
            }
        }

        Section("Lingua sottotitoli preferita", "Con \"Nessuna\" i sottotitoli restano spenti") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LANGUAGES.forEach { (label, code) ->
                    CategoryPill(label, subtitle == code, { viewModel.setSubtitleLanguage(code) })
                }
            }
        }

        Section("Buffer del player", "Più alto = meno interruzioni, zapping più lento. Si applica al prossimo canale.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BUFFERS.forEach { (label, ms) ->
                    CategoryPill(label, buffer == ms, { viewModel.setBufferMs(ms) })
                }
            }
        }

        Section(
            "Unisci canali duplicati",
            "Nell'elenco \"Tutti i canali\" le versioni HD/FHD/backup dello stesso canale diventano una voce sola; le altre restano come riserva per il failover."
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryPill("Attivo", merge, { viewModel.setMergeDuplicates(true) })
                CategoryPill("Disattivo", !merge, { viewModel.setMergeDuplicates(false) })
            }
        }

        Section(
            "Riproduzione in background",
            "Attiva: l'audio continua con lo schermo spento o l'app in secondo piano, con la notifica dei controlli. " +
                "Disattiva: quando esci dall'app la riproduzione si mette in pausa e la notifica sparisce; al ritorno riparte da sola."
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryPill("Attiva", background, { viewModel.setBackgroundPlayback(true) })
                CategoryPill("Disattiva", !background, { viewModel.setBackgroundPlayback(false) })
            }
        }

        Section(
            "Fonte ufficiale RaiPlay",
            "Per Rai 1, 2, 3, 4, Movie, Premium, Storia, News 24 e Sport usa RaiPlay come prima scelta (gratuito, solo con " +
                "IP italiano: fuori dall'Italia serve una VPN con uscita italiana). Se non è disponibile o si interrompe, passa " +
                "alla tua playlist."
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryPill("Attivo", official, { viewModel.setOfficialFallback(true) })
                CategoryPill("Disattivo", !official, { viewModel.setOfficialFallback(false) })
            }
        }

        Section(
            "Buffer che impara",
            "Impara a che ora il tuo provider dà problemi e aumenta il buffer poco prima. Usa solo i dati di riproduzione già raccolti sul dispositivo."
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryPill("Attivo", adaptive, { viewModel.setAdaptiveBuffer(true) })
                CategoryPill("Disattivo", !adaptive, { viewModel.setAdaptiveBuffer(false) })
            }
        }

        Section("Modalità cinema", "Nasconde le barre informative durante la riproduzione e tiene lo schermo acceso") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryPill("Attiva", cinema, { viewModel.setCinemaMode(true) })
                CategoryPill("Disattiva", !cinema, { viewModel.setCinemaMode(false) })
            }
        }

        Section("Modalità notte / dialoghi", "Alza il volume complessivo di 6 dB. Si applica al prossimo canale o film.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryPill("Attiva", volumeBoost, { viewModel.setVolumeBoost(true) })
                CategoryPill("Disattiva", !volumeBoost, { viewModel.setVolumeBoost(false) })
            }
        }

        Section("Dimensione del testo", "Ingrandisce tutta l'interfaccia, utile se guardi da lontano") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Normale" to 1f, "Grande" to 1.25f, "Molto grande" to 1.5f).forEach { (label, scale) ->
                    CategoryPill(label, kotlin.math.abs(fontScale - scale) < 0.01f, { viewModel.setFontScale(scale) })
                }
            }
        }

        Section(
            "Backup impostazioni",
            "Salva o ripristina preferenze, parole chiave e layout multi-view in un file. Non include PIN, playlist e credenziali."
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryPill("Esporta", false, {
                    try {
                        exportLauncher.launch("ammiratv-backup.json")
                    } catch (_: android.content.ActivityNotFoundException) {
                        backupMessage = "Questo dispositivo non ha un selettore di file"
                    }
                })
                CategoryPill("Ripristina", false, {
                    try {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    } catch (_: android.content.ActivityNotFoundException) {
                        backupMessage = "Questo dispositivo non ha un selettore di file"
                    }
                })
            }
            backupMessage?.let { Text(it, color = TextSecondary, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun Section(title: String, description: String, content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .background(DarkSurface, RoundedCornerShape(14.dp))
            .padding(18.dp)
    ) {
        Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(description, color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        content()
    }
}
