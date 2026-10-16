package com.ammiratv.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChannelNameCleanerTest {

    @Test
    fun testCountryPrefixes() {
        assertThat(ChannelNameCleaner.clean("IT: Rai 1")).isEqualTo("Rai 1")
        assertThat(ChannelNameCleaner.clean("IT - Rai 2")).isEqualTo("Rai 2")
        assertThat(ChannelNameCleaner.clean("[IT] Canale 5")).isEqualTo("Canale 5")
        assertThat(ChannelNameCleaner.clean("|IT| Italia 1")).isEqualTo("Italia 1")
        assertThat(ChannelNameCleaner.clean("ITA: Rete 4")).isEqualTo("Rete 4")
        assertThat(ChannelNameCleaner.clean("UK: BBC One")).isEqualTo("BBC One")
        assertThat(ChannelNameCleaner.clean("US | CNN")).isEqualTo("CNN")
    }

    @Test
    fun testQualityTokensRemoval() {
        assertThat(ChannelNameCleaner.clean("Rai 1 HD")).isEqualTo("Rai 1")
        assertThat(ChannelNameCleaner.clean("Sky Cinema Uno FHD [1080p]")).isEqualTo("Sky Cinema Uno")
        assertThat(ChannelNameCleaner.clean("Eurosport 1 4K UHD")).isEqualTo("Eurosport 1")
        assertThat(ChannelNameCleaner.clean("Discovery Channel (50fps) [RAW]")).isEqualTo("Discovery Channel")
        assertThat(ChannelNameCleaner.clean("Action Movie HEVC H.265")).isEqualTo("Action Movie")
    }

    @Test
    fun testBackupTokensRemoval() {
        assertThat(ChannelNameCleaner.clean("Canale 5 HD (BACKUP)")).isEqualTo("Canale 5")
        assertThat(ChannelNameCleaner.clean("Sky Sport 1 [BKP]")).isEqualTo("Sky Sport 1")
    }

    @Test
    fun testExtractQuality() {
        assertThat(ChannelNameCleaner.extractQuality("Rai 4K UHD")).isEqualTo("4K")
        assertThat(ChannelNameCleaner.extractQuality("Sky Sport FHD")).isEqualTo("FHD")
        assertThat(ChannelNameCleaner.extractQuality("Rai 1 HD")).isEqualTo("HD")
        assertThat(ChannelNameCleaner.extractQuality("Canale 5 HEVC")).isEqualTo("HEVC")
        assertThat(ChannelNameCleaner.extractQuality("Normal Channel")).isNull()
    }

    @Test
    fun testPreservesAcronyms() {
        assertThat(ChannelNameCleaner.clean("IT: BBC TWO")).isEqualTo("BBC Two")
        assertThat(ChannelNameCleaner.clean("US: CNN NEWS")).isEqualTo("CNN News")
        assertThat(ChannelNameCleaner.clean("IT: TG 24")).isEqualTo("TG 24")
    }
}
