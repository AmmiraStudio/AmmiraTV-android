package com.ammiratv.feature.settings

import com.ammiratv.core.ui.component.tvFieldNav
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ammiratv.core.model.Profile
import com.ammiratv.core.ui.component.CategoryPill
import com.ammiratv.core.ui.component.PinDialog
import com.ammiratv.core.ui.component.dpadFocusable
import com.ammiratv.core.ui.theme.DarkBackground
import com.ammiratv.core.ui.theme.DarkCard
import com.ammiratv.core.ui.theme.DarkSurface
import com.ammiratv.core.ui.theme.AmmiraTeal
import com.ammiratv.core.ui.theme.StatusOffline
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary

/** What the PIN keypad is currently collecting. */
private sealed interface PinStep {
    /** Verify the current PIN, then run [then]. */
    data class Verify(val title: String, val then: () -> Unit) : PinStep

    /** Ask for a brand-new PIN. */
    data object NewPin : PinStep
}

private val LIMIT_OPTIONS: List<Pair<String, Int?>> =
    listOf("Nessun limite" to null, "1 ora" to 60, "2 ore" to 120, "3 ore" to 180)

@Composable
fun ProfilesScreen(
    onBackClick: () -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val profiles by viewModel.profiles.collectAsState()
    val activeId by viewModel.activeProfileId.collectAsState()
    val hasPin by viewModel.hasPin.collectAsState()

    var pinStep by remember { mutableStateOf<PinStep?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }

    var newName by remember { mutableStateOf("") }
    var newIsChild by remember { mutableStateOf(false) }
    var newLimit by remember { mutableStateOf<Int?>(null) }

    BackHandler(onBack = onBackClick)

    val activeIsChild = profiles.firstOrNull { it.id == activeId }?.isChild == true

    fun requestPin(title: String, then: () -> Unit) {
        pinError = null
        if (hasPin) pinStep = PinStep.Verify(title, then) else then()
    }

    pinStep?.let { step ->
        PinDialog(
            title = when (step) {
                is PinStep.Verify -> step.title
                PinStep.NewPin -> "Scegli un nuovo PIN (4 cifre)"
            },
            errorMessage = pinError,
            onComplete = { pin ->
                when (step) {
                    is PinStep.Verify -> {
                        if (viewModel.verifyPin(pin)) {
                            pinStep = null
                            step.then()
                        } else {
                            pinError = "PIN errato"
                        }
                    }
                    PinStep.NewPin -> {
                        viewModel.setPin(pin)
                        pinStep = null
                    }
                }
            },
            onDismiss = { pinStep = null }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Text("Profili", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(16.dp))

        // ── Profile list ─────────────────────────────────────────────────
        LazyColumnInColumn(profiles) { profile ->
            ProfileRow(
                profile = profile,
                isActive = profile.id == activeId,
                canDelete = profiles.size > 1,
                onSelect = {
                    // Leaving a child profile is the moment a PIN protects the settings.
                    if (activeIsChild && !profile.isChild) {
                        requestPin("PIN genitori per cambiare profilo") { viewModel.select(profile.id) }
                    } else {
                        viewModel.select(profile.id)
                    }
                },
                onDelete = { requestPin("PIN genitori per eliminare il profilo") { viewModel.delete(profile.id) } }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── New profile ──────────────────────────────────────────────────
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .background(DarkSurface, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Text("Nuovo profilo", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)

            com.ammiratv.core.ui.component.TvTextField(
                value = newName,
                onValueChange = { newName = it },
                label = "Nome"
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryPill(name = "Adulto", isSelected = !newIsChild, onClick = { newIsChild = false })
                CategoryPill(name = "Bambino", isSelected = newIsChild, onClick = { newIsChild = true })
            }

            if (newIsChild) {
                Text("Tempo massimo al giorno", color = TextSecondary, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LIMIT_OPTIONS.forEach { (label, minutes) ->
                        CategoryPill(name = label, isSelected = newLimit == minutes, onClick = { newLimit = minutes })
                    }
                }
                Text(
                    "I profili bambino non vedono i gruppi per adulti e chiedono il PIN per uscire.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            PrimaryButton("Crea profilo") {
                viewModel.add(newName, newIsChild, if (newIsChild) newLimit else null)
                newName = ""
                newIsChild = false
                newLimit = null
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Parental PIN ─────────────────────────────────────────────────
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .background(DarkSurface, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Text("PIN genitori", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                text = if (hasPin) "PIN impostato" else "Nessun PIN: i profili bambino non sono protetti",
                color = if (hasPin) AmmiraTeal else TextSecondary,
                fontSize = 13.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(if (hasPin) "Cambia PIN" else "Imposta PIN") {
                    requestPin("Inserisci il PIN attuale") { pinStep = PinStep.NewPin }
                }
                if (hasPin) {
                    SecondaryButton("Rimuovi PIN") {
                        requestPin("Inserisci il PIN per rimuoverlo") { viewModel.clearPin() }
                    }
                }
            }
        }
    }
}

/** Plain column of rows: the whole screen scrolls, so a nested LazyColumn is not allowed. */
@Composable
private fun LazyColumnInColumn(profiles: List<Profile>, row: @Composable (Profile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        profiles.forEach { row(it) }
    }
}

@Composable
private fun ProfileRow(
    profile: Profile,
    isActive: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .background(DarkCard, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.name, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (isActive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.CheckCircle, contentDescription = "Attivo", tint = AmmiraTeal, modifier = Modifier.size(18.dp))
                }
            }
            val details = buildList {
                add(if (profile.isChild) "Bambino" else "Adulto")
                profile.dailyLimitMinutes?.let { add("max ${it / 60}h al giorno") }
            }.joinToString("  •  ")
            Text(details, color = TextSecondary, fontSize = 12.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isActive) PrimaryButton("Usa", onClick = onSelect)
            if (canDelete) SecondaryButton("Elimina", onClick = onDelete)
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = AmmiraTeal, contentColor = Color(0xFFFFFFFF)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp), onClick = onClick)
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A30), contentColor = StatusOffline),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp), onClick = onClick)
    ) { Text(text) }
}
