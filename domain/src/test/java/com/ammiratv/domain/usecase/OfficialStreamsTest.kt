package com.ammiratv.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialStreamsTest {

    @Test fun `rai channels are recognised through prefixes and quality tags`() {
        listOf("Rai 1", "RAI 1 HD", "IT: Rai 1 FHD", "Rai1").forEach {
            val source = OfficialStreams.forChannel(it)
            assertNotNull(it, source)
            assertEquals(OfficialProvider.RAIPLAY, source!!.provider)
            assertTrue(source.playUrl.contains("cont=2606803"))
        }
    }

    @Test fun `each rai channel points to its own id`() {
        assertTrue(OfficialStreams.forChannel("Rai 3")!!.playUrl.contains("cont=308709"))
        assertTrue(OfficialStreams.forChannel("Rai News 24")!!.playUrl.contains("cont=1&"))
        assertTrue(OfficialStreams.forChannel("Rai Movie HD")!!.playUrl.contains("cont=747002"))
    }

    @Test fun `regional editions and unrelated channels do not match`() {
        assertNull(OfficialStreams.forChannel("Rai 1 Sicilia"))
        assertNull(OfficialStreams.forChannel("Rai Italia"))
        assertNull(OfficialStreams.forChannel("Sky Sport 24"))
        assertNull(OfficialStreams.forChannel("La7"))
    }

    @Test fun `mediaset channels have no official source`() {
        listOf("Canale 5", "Italia 1", "Rete 4", "Iris").forEach { assertNull(it, OfficialStreams.forChannel(it)) }
    }
}
