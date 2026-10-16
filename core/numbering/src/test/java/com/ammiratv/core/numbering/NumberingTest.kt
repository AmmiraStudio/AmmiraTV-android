package com.ammiratv.core.numbering

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

class NumberingTest {

    private val database: CanonicalDatabase by lazy {
        val text = File("src/main/assets/numbering/it.json").readText()
        val result = CanonicalDatabaseParser.parse(text)
        assertThat(result.errors).isEmpty()
        result.database!!
    }
    private val matcher by lazy { CanonicalMatcher(database) }

    private var nextId = 1L
    private fun input(
        name: String,
        group: String? = "EU | ITALY GENERALE",
        tvgId: String? = null,
        url: String = "http://provider.example/live/u/p/${nextId}.ts",
        live: Boolean = true
    ) = NumberingInput(
        id = nextId++, key = "name:" + name.lowercase(), rawName = name, tvgId = tvgId,
        tvgName = null, groupName = group, streamUrl = url, isLive = live
    )

    private fun numberOf(name: String, group: String? = "EU | ITALY GENERALE"): Int? {
        val i = input(name, group)
        return ChannelNumbering.assign(listOf(i), matcher).assignments.firstOrNull()?.number
    }

    // ── data file ──────────────────────────────────────────────────────────

    @Test
    fun bundledDatabaseIsValidAndHasNoNumberConflicts() {
        val text = File("src/main/assets/numbering/it.json").readText()
        val result = CanonicalDatabaseParser.parse(text)
        assertThat(result.isValid).isTrue()
        assertThat(result.numberConflicts).isEmpty()
    }

    @Test
    fun everyChannelCarriesASourceAndAVerificationDate() {
        database.channels.forEach {
            assertThat(it.source).isIn(database.sources.keys)
            assertThat(it.verifiedAt).isNotEmpty()
        }
    }

    @Test
    fun invalidFilesAreRejected() {
        fun errorsOf(json: String) = CanonicalDatabaseParser.parse(json).errors
        assertThat(errorsOf("not json")).isNotEmpty()
        val dupe = """{"version":1,"country":"IT","sources":{"s":{"url":"https://x"}},"channels":[
            {"id":"a","name":"A","number":1,"platform":"dtt","source":"s","verifiedAt":"2026-01-01"},
            {"id":"a","name":"B","number":2,"platform":"dtt","source":"s","verifiedAt":"2026-01-01"}]}"""
        assertThat(errorsOf(dupe).joinToString()).contains("duplicato")
        val link = """{"version":1,"country":"IT","sources":{"s":{"url":"https://x"}},"channels":[
            {"id":"a","name":"A","number":1,"platform":"dtt","aliases":["http://evil/stream.m3u8"],"source":"s","verifiedAt":"2026-01-01"}]}"""
        assertThat(errorsOf(link).joinToString()).contains("link")
        val zero = """{"version":1,"country":"IT","sources":{"s":{"url":"https://x"}},"channels":[
            {"id":"a","name":"A","number":0,"platform":"dtt","source":"s","verifiedAt":"2026-01-01"}]}"""
        assertThat(errorsOf(zero).joinToString()).contains("numero")
    }

    // ── acceptance: Rai 1 = 1, Sky Sport 24 = 200, three or more name formats each ─

    @Test
    fun rai1IsNumberOneInEveryFormat() {
        listOf("Rai 1", "IT: RAI 1 HD", "|IT| Rai Uno FHD", "Rai1", "✦●✦ RAI 1 4K ✦●✦", "RAI 1 (BACKUP)")
            .forEach { assertWithMessage(it).that(numberOf(it)).isEqualTo(1) }
    }

    @Test
    fun skySport24Is200InEveryFormat() {
        listOf("Sky Sport 24", "IT| SKY SPORT 24 FHD", "SKY SPORT24 HD", "[IT] Sky Sport 24 (BACKUP)", "Sky  Sport  24")
            .forEach { assertWithMessage(it).that(numberOf(it)).isEqualTo(200) }
    }

    @Test
    fun otherWellKnownChannelsFollowTheirOfficialNumbers() {
        assertThat(numberOf("Canale 5 HD")).isEqualTo(5)
        assertThat(numberOf("IT: La7")).isEqualTo(7)
        assertThat(numberOf("Nove")).isEqualTo(9)
        assertThat(numberOf("Sky Cinema Uno HD")).isEqualTo(301)
        assertThat(numberOf("Sky Atlantic")).isEqualTo(111)
    }

    // ── false positives that must not happen ───────────────────────────────

    @Test
    fun similarNamesDoNotBorrowAnotherChannelsNumber() {
        assertThat(numberOf("Rai 1 Sport")).isNull()
        assertThat(numberOf("Sky Sport 247")).isNull()
        assertThat(numberOf("Sky Sport 2")).isNull()
        assertThat(numberOf("Rai 11")).isNull()
        assertThat(numberOf("Canale 50")).isNull()
        assertThat(numberOf("Rai Sport")).isEqualTo(58) // the real one still matches exactly
    }

    @Test
    fun aSingleTypoStillMatchesWhenTheDigitsAreIdentical() {
        assertThat(numberOf("Sky Atlantc")).isEqualTo(111)
        assertThat(numberOf("Sky Sport 25")).isNull() // different digits: never fuzzy
    }

    // ── only Italian live channels ─────────────────────────────────────────

    @Test
    fun moviesAndSeriesNeverGetANumber() {
        val a = input("Rai 1", url = "http://p.example/movie/u/p/1.mp4")
        val b = input("Rai 1", url = "http://p.example/series/u/p/2.mp4")
        val c = input("Rai 1", group = "FILM ITALIANI")
        val d = input("Rai 1", group = "Serie TV Italiane")
        val e = input("Rai 1", live = false)
        val result = ChannelNumbering.assign(listOf(a, b, c, d, e), matcher)
        assertThat(result.assignments).isEmpty()
    }

    @Test
    fun serieAIsFootballNotASeriesGroup() {
        assertThat(ChannelNumbering.looksLikeVod("EU | ITALY DAZN SERIE A", "http://x/live/1")).isFalse()
        assertThat(ChannelNumbering.looksLikeVod("IT | SERIE B", "http://x/live/1")).isFalse()
        assertThat(ChannelNumbering.looksLikeVod("[IT] SERIE TV", "http://x/live/1")).isTrue()
        assertThat(ChannelNumbering.looksLikeVod("[IT] SERIE ITALIANA", "http://x/live/1")).isTrue()
    }

    @Test
    fun foreignChannelsWithAnItalianNameStayUnnumbered() {
        assertThat(numberOf("Sky Sport 24", group = "DE | SPORT")).isNull()
        assertThat(numberOf("Rai 1", group = "[UK] ENTERTAINMENT")).isNull()
        // no country information at all: not recognised with certainty, so no number
        assertThat(numberOf("Sky Sport 24", group = "SPORT")).isNull()
        assertThat(numberOf("Sky Sport 24", group = null)).isNull()
    }

    @Test
    fun tvgIdFromTheCatalogueIsEnoughOnItsOwn() {
        val custom = database.copy(
            channels = database.channels.map { if (it.id == "rai1") it.copy(tvgIds = listOf("Rai1.it")) else it }
        )
        val i = input("Canale Strano", group = "SPORT", tvgId = "RAI1.IT")
        val result = ChannelNumbering.assign(listOf(i), CanonicalMatcher(custom))
        assertThat(result.assignments.single().number).isEqualTo(1)
    }

    // ── different providers, same numbers ──────────────────────────────────

    @Test
    fun differentProvidersGetTheSameNumbers() {
        val providerA = listOf("IT: RAI 1 HD", "IT: CANALE 5 FHD", "IT: SKY SPORT 24 HD", "IT: LA7")
        val providerB = listOf("|IT| Rai Uno", "|IT| Canale5", "|IT| SkySport24", "|IT| La 7 4K")
        val providerC = listOf("✦●✦ ITA | Rai 1 ✦●✦", "ITA | Canale 5", "ITA | Sky Sport 24", "ITA | La7")
        val expected = listOf(1, 5, 200, 7)
        for (names in listOf(providerA, providerB, providerC)) {
            val inputs = names.map { input(it, group = "ITALIA TV") }
            val result = ChannelNumbering.assign(inputs, matcher)
            val byId = result.assignments.associate { it.channelId to it.number }
            assertThat(inputs.map { byId[it.id] }).isEqualTo(expected)
        }
    }

    @Test
    fun qualityVariantsOfOneChannelShareTheNumber() {
        val inputs = listOf("IT: Rai 1 SD", "IT: Rai 1 HD", "IT: Rai 1 FHD", "IT: Rai 1 4K").map { input(it) }
        val numbers = ChannelNumbering.assign(inputs, matcher).assignments.map { it.number }.toSet()
        assertThat(numbers).containsExactly(1)
    }

    @Test
    fun bestVariantPrefersQualityThenFavouriteThenReliability() {
        data class V(val name: String, val fav: Boolean, val rel: Int, val id: Long)
        val sd = V("Rai 1 SD", true, 9, 1)
        val hd = V("Rai 1 HD", false, 0, 2)
        val fhdLow = V("Rai 1 FHD", false, 1, 3)
        val fhdHigh = V("Rai 1 FHD", false, 5, 4)
        fun pick(vararg v: V) = ChannelNumbering.bestVariant(v.toList(), { it.name }, { it.fav }, { it.rel }, { it.id })
        assertThat(pick(sd, hd)).isEqualTo(hd)
        assertThat(pick(sd, hd, fhdLow)).isEqualTo(fhdLow)
        assertThat(pick(fhdLow, fhdHigh)).isEqualTo(fhdHigh)
    }

    // ── manual choices and the unofficial range ────────────────────────────

    @Test
    fun manualNumbersAlwaysWin() {
        val i = input("Rai 1")
        val result = ChannelNumbering.assign(listOf(i), matcher, manual = mapOf(i.key to 777))
        val a = result.assignments.single()
        assertThat(a.number).isEqualTo(777)
        assertThat(a.origin).isEqualTo(NumberOrigin.MANUAL)
    }

    @Test
    fun unmatchedItalianChannelsAreListedAndOnlyNumberedOnRequest() {
        val local = input("Telepadova Canale", group = "EU | ITALY LOCALI")
        val off = ChannelNumbering.assign(listOf(local), matcher, includeUnofficial = false)
        assertThat(off.assignments).isEmpty()
        assertThat(off.toVerify).containsExactly(local.id)

        val a = input("Zeta Local", group = "EU | ITALY LOCALI")
        val b = input("Alfa Local", group = "EU | ITALY LOCALI")
        val on = ChannelNumbering.assign(listOf(a, b), matcher, includeUnofficial = true)
        val byId = on.assignments.associate { it.channelId to it.number }
        assertThat(byId[b.id]).isEqualTo(ChannelNumbering.UNOFFICIAL_START)
        assertThat(byId[a.id]).isEqualTo(ChannelNumbering.UNOFFICIAL_START + 1)
        assertThat(on.assignments.all { it.origin == NumberOrigin.UNOFFICIAL }).isTrue()
    }

    @Test
    fun nonItalianChannelsAreNeverOffered() {
        val de = input("Das Erste", group = "DE | NACHRICHTEN")
        val result = ChannelNumbering.assign(listOf(de), matcher, includeUnofficial = true)
        assertThat(result.assignments).isEmpty()
        assertThat(result.toVerify).isEmpty()
    }

    @Test
    fun performanceStaysFineOnHugePlaylists() {
        val names = listOf("IT: Rai 1 HD", "UK| BBC One", "DE| Das Erste", "IT: Sky Sport 24 FHD", "FR| TF1")
        val inputs = (0 until 60_000).map { i -> input(names[i % names.size] + " #$i", group = "GROUP ${i % 50}") }
        val start = System.nanoTime()
        ChannelNumbering.assign(inputs, matcher)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertThat(ms).isLessThan(5_000)
    }
}
