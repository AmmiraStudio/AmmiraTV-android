package com.ammiratv.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ItalianCategoriesTest {

    @Test
    fun recognisesCommonLanguageTags() {
        listOf(
            "[IT] COMMEDIA", "IT| DRAMMA", "IT | FILM", "ITA - Azione", "(IT) Serie",
            "EU | ITALY SPORTS", "EU | IT SPORTS", "Italia Calcio", "FILM ITALIANI", "Serie Italiana"
        ).forEach { assertThat(ItalianCategories.isItalian(it)).isTrue() }
    }

    @Test
    fun rejectsOtherLanguagesAndBareIt() {
        listOf(
            "[EN] BRITISH", "[EXYU] MESOVITI ZANROVI", "[EN] THE IT CROWD", "FR | CINEMA",
            "AR | BEIN SPORTS", "DE | SPORT", "Citizens", "[EN] WORLD CUP 2026"
        ).forEach { assertThat(ItalianCategories.isItalian(it)).isFalse() }
    }

    @Test
    fun ignoresAccentsAndCase() {
        assertThat(ItalianCategories.isItalian("itàlia | sport")).isTrue()
    }

    @Test
    fun mergesAllItalianSportsSectionsWithPlainSportsFirst() {
        val categories = listOf(
            Category(id = 1, name = "EU | ITALY DAZN SERIE A"),
            Category(id = 2, name = "EU | ITALY SPORTS"),
            Category(id = 3, name = "EU | ITALY PPV-LIVE EVENTS"),
            Category(id = 4, name = "EU | ITALY CINEMA"),
            Category(id = 5, name = "AR | BEIN SPORTS"),
            Category(id = 6, name = "EU | ITALY DAZN SERIE B"),
            Category(id = 7, name = "EU | ITALY DAZN EVENTO")
        )
        val ids = ItalianCategories.sportsSections(categories).map { it.id }
        // ITALY SPORTS leads; DAZN groups follow; PPV, cinema and non-Italian groups stay out
        assertThat(ids.first()).isEqualTo(2)
        assertThat(ids).containsExactly(2L, 1L, 6L, 7L)
    }

    @Test
    fun daznAloneStillCountsAsFootball() {
        val categories = listOf(
            Category(id = 1, name = "IT | DAZN SERIE A"),
            Category(id = 2, name = "IT | CINEMA")
        )
        assertThat(ItalianCategories.sportsSections(categories).map { it.id }).containsExactly(1L)
    }

    @Test
    fun noItalianSportsMeansNoPinnedFolder() {
        val categories = listOf(
            Category(id = 1, name = "EN | SPORTS"),
            Category(id = 2, name = "IT | CINEMA"),
            Category(id = 3, name = "IT | PPV EVENTI")
        )
        assertThat(ItalianCategories.sportsSections(categories)).isEmpty()
    }
}
