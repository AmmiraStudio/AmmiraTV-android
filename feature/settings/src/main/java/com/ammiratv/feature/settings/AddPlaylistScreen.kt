package com.ammiratv.feature.settings

import com.ammiratv.core.ui.component.tvFieldNav
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.ammiratv.core.ui.R as UiR
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.ammiratv.core.ui.component.TvTextField
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ammiratv.core.model.PlaylistType
import com.ammiratv.core.ui.component.CategoryPill
import com.ammiratv.core.ui.component.dpadFocusable
import com.ammiratv.core.ui.theme.DarkBackground
import com.ammiratv.core.ui.theme.DarkCard
import com.ammiratv.core.ui.theme.DarkSurface
import com.ammiratv.core.ui.theme.AmmiraTeal
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary
import com.ammiratv.domain.usecase.ImportProgress

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AddPlaylistScreen(
    onImportSuccess: () -> Unit,
    viewModel: AddPlaylistViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val device = com.ammiratv.core.ui.device.LocalDeviceProfile.current
    val compact = com.ammiratv.core.ui.device.LocalWidthClass.current == com.ammiratv.core.ui.device.WidthClass.COMPACT
    // Narrow screens use the full width and tighter spacing so the form is visible without scrolling
    val formWidth = if (compact) 1f else 0.7f
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = if (compact) 16.dp else 32.dp, vertical = if (compact) 12.dp else 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = UiR.drawable.app_logo),
                    contentDescription = "AmmiraTv Logo",
                    modifier = Modifier
                        .size(if (compact) 36.dp else 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "Aggiungi Sorgente",
                    fontSize = if (compact) 22.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(if (compact) 12.dp else 24.dp))

            // Source Type Selector (wraps on narrow screens instead of running off the side)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryPill(
                    name = "Playlist M3U / M3U8",
                    isSelected = state.selectedType == PlaylistType.M3U,
                    onClick = { viewModel.setType(PlaylistType.M3U) }
                )
                CategoryPill(
                    name = "Xtream Codes API",
                    isSelected = state.selectedType == PlaylistType.XTREAM,
                    onClick = { viewModel.setType(PlaylistType.XTREAM) }
                )
                // The QR code is for a TV: scanned with a phone. On a phone it makes no sense.
                if (device.isTv) {
                    CategoryPill(
                        name = "Importa dal telefono (QR)",
                        isSelected = false,
                        onClick = { viewModel.startPhoneImport() }
                    )
                } else {
                    CategoryPill(
                        name = "Incolla dagli appunti",
                        isSelected = false,
                        onClick = {
                            val clip = (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as? android.content.ClipboardManager)
                                ?.primaryClip?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                            if (clip.isNotEmpty()) {
                                if (state.selectedType == PlaylistType.M3U) viewModel.setM3uUrl(clip)
                                else viewModel.setXtreamServer(clip)
                            }
                        }
                    )
                }
            }

            state.phoneImportError?.let { message ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = message, color = Color(0xFFFFCDD2), fontSize = 13.sp)
            }

            state.phoneImportUrl?.let { url ->
                Dialog(onDismissRequest = { viewModel.stopPhoneImport() }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkSurface)
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "Scansiona con il telefono",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        QrCode(content = url, size = 240.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = url, color = AmmiraTeal, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Telefono e TV devono essere sulla stessa rete Wi-Fi.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.stopPhoneImport() },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkCard, contentColor = TextPrimary),
                            modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp)) {
                                viewModel.stopPhoneImport()
                            }
                        ) { Text("Chiudi") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (compact) 12.dp else 28.dp))

            // Form container
            Column(
                modifier = Modifier
                    .fillMaxWidth(formWidth)
                    .background(DarkSurface, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Fields: on TV they open the keyboard only when OK is pressed on them
                TvTextField(
                    value = state.name,
                    onValueChange = { viewModel.setName(it) },
                    label = "Nome Sorgente (opzionale)"
                )

                if (state.selectedType == PlaylistType.M3U) {
                    TvTextField(
                        value = state.m3uUrl,
                        onValueChange = { viewModel.setM3uUrl(it) },
                        label = "URL Playlist M3U / M3U8 (anche piu' indirizzi separati da spazio)",
                        placeholder = "https://example.com/playlist.m3u",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri
                    )
                } else {
                    TvTextField(
                        value = state.xtreamServerUrl,
                        onValueChange = { viewModel.setXtreamServer(it) },
                        label = "Server Xtream (anche piu' DNS separati da spazio: scelgo il migliore)",
                        placeholder = "http://server.example.com:8080",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri
                    )
                    TvTextField(
                        value = state.xtreamUsername,
                        onValueChange = { viewModel.setXtreamUsername(it) },
                        label = "Username"
                    )
                    TvTextField(
                        value = state.xtreamPassword,
                        onValueChange = { viewModel.setXtreamPassword(it) },
                        label = "Password",
                        isPassword = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Connect button
                val isButtonEnabled = state.progress !is ImportProgress.Loading && state.progress !is ImportProgress.Parsing
                Button(
                    onClick = { viewModel.importPlaylist() },
                    enabled = isButtonEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmmiraTeal,
                        contentColor = Color(0xFFFFFFFF)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .dpadFocusable(shape = RoundedCornerShape(12.dp)) {
                            if (isButtonEnabled) viewModel.importPlaylist()
                        }
                ) {
                    Text(
                        text = "Connetti e Importa",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Progress Banner
            when (val progress = state.progress) {
                is ImportProgress.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth(formWidth)
                            .background(DarkCard, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = AmmiraTeal,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = progress.message,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                    }
                }
                is ImportProgress.Parsing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth(formWidth)
                            .background(DarkCard, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = AmmiraTeal,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Importazione canali: ${progress.count}",
                            color = AmmiraTeal,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                is ImportProgress.Success -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth(formWidth)
                            .background(Color(0xFF143026), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AmmiraTeal,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Completato! ${progress.totalChannels} canali importati con successo.",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onImportSuccess,
                            colors = ButtonDefaults.buttonColors(containerColor = AmmiraTeal, contentColor = Color(0xFFFFFFFF)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp), onClick = onImportSuccess)
                        ) {
                            Text("Vai alla Live TV", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                is ImportProgress.Error -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth(formWidth)
                            .background(Color(0xFF331414), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = progress.message,
                            color = Color(0xFFFFCDD2),
                            fontSize = 14.sp
                        )
                    }
                }
                null -> {}
            }
        }
    }
}
