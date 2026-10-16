package com.ammiratv.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ammiratv.feature.epg.EpgGridScreen
import com.ammiratv.feature.live.LiveTvScreen
import com.ammiratv.feature.multiview.MultiViewScreen
import com.ammiratv.feature.search.SearchScreen
import com.ammiratv.feature.search.TonightScreen
import com.ammiratv.feature.settings.AddPlaylistScreen
import com.ammiratv.feature.settings.DiagnosticsScreen
import com.ammiratv.feature.settings.NumberingScreen
import com.ammiratv.feature.settings.PreferencesScreen
import com.ammiratv.feature.settings.RecordingsScreen
import com.ammiratv.feature.settings.ProfilesScreen
import com.ammiratv.feature.settings.SettingsEntry
import com.ammiratv.feature.settings.SettingsHubScreen
import com.ammiratv.feature.settings.SourcesScreen
import com.ammiratv.feature.vod.VodDetailScreen
import com.ammiratv.feature.vod.VodHomeScreen
import com.ammiratv.feature.vod.VodPlayerScreen

object AmmiraTvDestinations {
    const val LIVE = "live"
    const val EPG = "epg"
    const val SEARCH = "search"
    const val SOURCES = "sources"
    const val TONIGHT = "tonight"
    const val MULTIVIEW = "multiview"
    const val SETTINGS = "settings"
    const val PREFERENCES = "preferences"
    const val NUMBERING = "numbering"
    const val TEXT_TEST = "text_test"
    const val VPN = "vpn"
    const val RECORDINGS = "recordings"
    const val DIAGNOSTICS = "diagnostics"
    const val PROFILES = "profiles"
    const val ADD_SOURCE = "add_source"
    const val VOD = "vod"
    const val VOD_DETAIL = "vod_detail/{kind}/{playlistId}/{itemId}"
    const val VOD_PLAYER = "vod_player/{key}"

    fun vodDetail(kind: String, playlistId: Long, itemId: Long) = "vod_detail/$kind/$playlistId/$itemId"

    /** The content key contains ':' so it is URI-encoded into one path segment. */
    fun vodPlayer(contentKey: String) = "vod_player/${Uri.encode(contentKey)}"
}

/** Key under which other screens hand a channel id back to the Live screen. */
private const val PLAY_CHANNEL_KEY = "play_channel_id"
private const val NO_CHANNEL = -1L

/**
 * Root navigation graph for AmmiraTv.
 *
 * Live TV owns the player, so EPG and Search never start playback themselves: they leave the
 * requested channel id on the Live entry's SavedStateHandle and pop back to it.
 */
@Composable
fun AmmiraTvNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = AmmiraTvDestinations.LIVE,
    modifier: Modifier = Modifier
) {
    fun playOnLive(channelId: Long) {
        navController.previousBackStackEntry?.savedStateHandle?.set(PLAY_CHANNEL_KEY, channelId)
        navController.popBackStack()
    }

    AdaptiveNavigation(navController) { shellModifier ->
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier.then(shellModifier)
    ) {
        composable(AmmiraTvDestinations.LIVE) { entry ->
            val handle = entry.savedStateHandle
            val pendingChannelId by handle.getStateFlow(PLAY_CHANNEL_KEY, NO_CHANNEL).collectAsState()

            LiveTvScreen(
                onNavigateToSettings = { navController.navigate(AmmiraTvDestinations.SETTINGS) },
                onNavigateToSources = { navController.navigate(AmmiraTvDestinations.SOURCES) },
                onNavigateToEpg = { navController.navigate(AmmiraTvDestinations.EPG) },
                onNavigateToSearch = { navController.navigate(AmmiraTvDestinations.SEARCH) },
                onNavigateToProfiles = { navController.navigate(AmmiraTvDestinations.PROFILES) },
                onNavigateToVod = { navController.navigate(AmmiraTvDestinations.VOD) },
                pendingChannelId = pendingChannelId,
                onPendingChannelConsumed = { handle[PLAY_CHANNEL_KEY] = NO_CHANNEL }
            )
        }

        composable(AmmiraTvDestinations.EPG) {
            EpgGridScreen(
                onBackClick = { navController.popBackStack() },
                onPlayChannel = { channel -> playOnLive(channel.id) },
                onPlayCatchup = { key -> navController.navigate(AmmiraTvDestinations.vodPlayer(key)) }
            )
        }

        composable(AmmiraTvDestinations.SEARCH) {
            SearchScreen(
                onBackClick = { navController.popBackStack() },
                onPlayChannel = { channel -> playOnLive(channel.id) },
                onOpenTonight = { navController.navigate(AmmiraTvDestinations.TONIGHT) }
            )
        }

        composable(AmmiraTvDestinations.MULTIVIEW) {
            MultiViewScreen(onBackClick = { navController.popBackStack() })
        }

        composable(AmmiraTvDestinations.TONIGHT) {
            TonightScreen(
                onBackClick = { navController.popBackStack() },
                // Tonight sits on top of Search which sits on top of Live: pop both
                onWatchLive = { channel ->
                    navController.getBackStackEntry(AmmiraTvDestinations.LIVE)
                        .savedStateHandle[PLAY_CHANNEL_KEY] = channel.id
                    navController.popBackStack(AmmiraTvDestinations.LIVE, inclusive = false)
                }
            )
        }

        composable(AmmiraTvDestinations.SOURCES) {
            SourcesScreen(
                onBackClick = { navController.popBackStack() },
                onAddSource = { navController.navigate(AmmiraTvDestinations.ADD_SOURCE) }
            )
        }

        composable(AmmiraTvDestinations.SETTINGS) {
            SettingsHubScreen(
                entries = listOf(
                    SettingsEntry("Sorgenti", "Playlist M3U e account Xtream, scadenza e connessioni") {
                        navController.navigate(AmmiraTvDestinations.SOURCES)
                    },
                    SettingsEntry("Profili e PIN genitori", "Preferiti separati, profili bambino, limiti orari") {
                        navController.navigate(AmmiraTvDestinations.PROFILES)
                    },
                    SettingsEntry("Preferenze", "Lingue automatiche, buffer, modalità cinema") {
                        navController.navigate(AmmiraTvDestinations.PREFERENCES)
                    },
                    SettingsEntry("Numerazione canali", "Numeri della TV (Rai 1 = 1), digitazione da telecomando, correzioni") {
                        navController.navigate(AmmiraTvDestinations.NUMBERING)
                    },
                    SettingsEntry("VPN", "Proteggi il traffico di AmmiraTv con la tua VPN (WireGuard)") {
                        navController.navigate(AmmiraTvDestinations.VPN)
                    },
                    SettingsEntry("Test testi lunghi", "Verifica scorrimento e lettura completa dei testi lunghi") {
                        navController.navigate(AmmiraTvDestinations.TEXT_TEST)
                    },
                    SettingsEntry("Multi-view", "Fino a 4 canali insieme, layout salvabili") {
                        navController.navigate(AmmiraTvDestinations.MULTIVIEW)
                    },
                    SettingsEntry("Registrazioni", "Programmi registrati e pianificati") {
                        navController.navigate(AmmiraTvDestinations.RECORDINGS)
                    },
                    SettingsEntry("Diagnostica e pagella provider", "Affidabilità dei canali, rete e buffering") {
                        navController.navigate(AmmiraTvDestinations.DIAGNOSTICS)
                    }
                ),
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(AmmiraTvDestinations.RECORDINGS) {
            RecordingsScreen(
                onBackClick = { navController.popBackStack() },
                onPlay = { key -> navController.navigate(AmmiraTvDestinations.vodPlayer(key)) }
            )
        }

        composable(AmmiraTvDestinations.DIAGNOSTICS) {
            DiagnosticsScreen(onBackClick = { navController.popBackStack() })
        }

        composable(AmmiraTvDestinations.PREFERENCES) {
            PreferencesScreen(onBackClick = { navController.popBackStack() })
        }

        composable(AmmiraTvDestinations.VPN) {
            com.ammiratv.feature.settings.VpnScreen(onBackClick = { navController.popBackStack() })
        }

        composable(AmmiraTvDestinations.TEXT_TEST) {
            com.ammiratv.feature.settings.LongTextFixtureScreen(onBackClick = { navController.popBackStack() })
        }

        composable(AmmiraTvDestinations.NUMBERING) {
            NumberingScreen(onBackClick = { navController.popBackStack() })
        }

        composable(AmmiraTvDestinations.PROFILES) {
            ProfilesScreen(onBackClick = { navController.popBackStack() })
        }

        composable(AmmiraTvDestinations.ADD_SOURCE) {
            AddPlaylistScreen(
                onImportSuccess = {
                    // Back to the existing Live entry instead of stacking a second one on top
                    navController.popBackStack(AmmiraTvDestinations.LIVE, inclusive = false)
                }
            )
        }

        composable(AmmiraTvDestinations.VOD) {
            VodHomeScreen(
                onBackClick = { navController.popBackStack() },
                onOpenItem = { kind, playlistId, itemId ->
                    navController.navigate(AmmiraTvDestinations.vodDetail(kind.name, playlistId, itemId))
                },
                onResume = { key -> navController.navigate(AmmiraTvDestinations.vodPlayer(key)) }
            )
        }

        composable(
            route = AmmiraTvDestinations.VOD_DETAIL,
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType },
                navArgument("playlistId") { type = NavType.LongType },
                navArgument("itemId") { type = NavType.LongType }
            )
        ) {
            VodDetailScreen(
                onBackClick = { navController.popBackStack() },
                onPlay = { key -> navController.navigate(AmmiraTvDestinations.vodPlayer(key)) }
            )
        }

        composable(
            route = AmmiraTvDestinations.VOD_PLAYER,
            arguments = listOf(navArgument("key") { type = NavType.StringType })
        ) {
            VodPlayerScreen(onBackClick = { navController.popBackStack() })
        }
    }
    }
}
