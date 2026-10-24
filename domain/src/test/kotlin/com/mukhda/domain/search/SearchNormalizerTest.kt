package com.mukhda.domain.search

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class SearchNormalizerTest {

    private fun n(s: String) = SearchNormalizer.normalize(s)

    /**
     * The curation tool builds the app's search database with its own normalize.ts, and its tests
     * check these same cases: a drift between the two would make the index miss what players type.
     */
    @Test
    fun `agrees with the curation tool on the shared cases`() {
        val cases = javaClass.getResource("/search-normalizer-cases.tsv")!!.readText().lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split('\t') }
        assertThat(cases.size).isEqualTo(22)
        for ((input, expected) in cases) assertThat(n(input)).isEqualTo(expected)
    }

    @Test
    fun `lowercases and strips punctuation`() {
        assertThat(n("Tujh Mein Rab Dikhta Hai!")).isEqualTo("tujh mein rab dikhta hai")
        assertThat(n("Dil-E-Nadaan")).isEqualTo("dil e nadan")
        assertThat(n("It's The Time To Disco")).isEqualTo("its the time to disco")
    }

    @Test
    fun `strips diacritics`() {
        assertThat(n("Café")).isEqualTo("cafe")
        assertThat(n("Sájna")).isEqualTo("sajna")
    }

    @Test
    fun `romanisation variants meet`() {
        assertThat(n("Kabhee")).isEqualTo(n("Kabhi"))
        assertThat(n("Lootera")).isEqualTo(n("Lutera"))
        assertThat(n("Phir")).isEqualTo(n("Fir"))
        assertThat(n("Zara")).isEqualTo(n("Jara"))
        assertThat(n("Wada")).isEqualTo(n("Vada"))
        assertThat(n("Baazigar")).isEqualTo(n("Bajigar"))
    }

    @Test
    fun `collapses repeated letters`() {
        assertThat(n("Tumm Hii Ho")).isEqualTo("tum hi ho")
        assertThat(n("Kal Ho Naa Ho")).isEqualTo("kal ho na ho")
    }

    @Test
    fun `joined key removes spaces`() {
        assertThat(SearchNormalizer.joined(n("Kal Ho Naa Ho"))).isEqualTo("kalhonaho")
    }

    @Test
    fun `blank input gives no tokens`() {
        assertThat(SearchNormalizer.tokens(n("  ?! "))).isEmpty()
    }
}
