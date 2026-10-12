package com.mukhda.domain.search

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class SongSearcherTest {

    private fun song(id: String, title: String, film: String, year: Int = 2010) =
        SearchSong(id, title, film.lowercase().replace(' ', '_'), film, year)

    private val songs = listOf(
        song("s1", "Sawaar Loon", "Lootera", 2013),
        song("s2", "Shikayatein", "Lootera", 2013),
        song("s3", "Manmarziyan", "Lootera", 2013),
        song("s4", "Zinda", "Lootera", 2013),
        song("s5", "Ankahee", "Lootera", 2013),
        song("s6", "Monta Re", "Lootera", 2013),
        song("s7", "Kal Ho Naa Ho", "Kal Ho Naa Ho", 2003),
        song("s8", "Kuch To Hua Hai", "Kal Ho Naa Ho", 2003),
        song("s9", "Kabhi Kabhi Aditi", "Jaane Tu Ya Jaane Na", 2008),
        song("s10", "Tum Hi Ho", "Aashiqui 2", 2013),
        song("s11", "Hua Main", "Animal", 2023),
    )
    private val searcher = SongSearcher(songs)

    private fun ids(query: String) = searcher.search(query).items.map { it.songId }

    @Test
    fun `fewer than two characters shows nothing`() {
        assertThat(searcher.search("k")).isEqualTo(SearchResults.EMPTY)
        assertThat(searcher.search("  k ")).isEqualTo(SearchResults.EMPTY)
    }

    @Test
    fun `combined film and title query`() {
        assertThat(ids("lootera shik")).containsExactly("s2")
        assertThat(ids("shik lootera")).containsExactly("s2")
    }

    @Test
    fun `title match ranks above film-only match`() {
        assertThat(ids("kal ho")).containsExactly("s7", "s8")
    }

    @Test
    fun `exact prefix beats word prefix`() {
        // "Hua Main" starts with "hua"; "Kuch To Hua Hai" only has it as a later word.
        assertThat(ids("hua")).containsExactly("s11", "s8")
        assertThat(ids("tum hi")).containsExactly("s10")
    }

    @Test
    fun `romanisation variants match`() {
        assertThat(ids("ankahi")).containsExactly("s5")
        assertThat(ids("jinda").first()).isEqualTo("s4")
        assertThat(searcher.search("lutera").totalMatches).isEqualTo(6)
    }

    @Test
    fun `joined query matches across spaces`() {
        assertThat(ids("kalhonaho").first()).isEqualTo("s7")
    }

    @Test
    fun `one typo still finds the song`() {
        assertThat(ids("manmarzian")).containsExactly("s3")
    }

    @Test
    fun `caps at five results and reports the total`() {
        val results = searcher.search("lootera")
        assertThat(results.items.size).isEqualTo(5)
        assertThat(results.totalMatches).isEqualTo(6)
        assertThat(results.isTruncated).isTrue()
    }

    @Test
    fun `artists are not searchable`() {
        // Arijit Singh sings "Tum Hi Ho"; the index has no artist field to match.
        assertThat(ids("arijit")).doesNotContain("s10")
    }

    @Test
    fun `edit distance`() {
        assertThat(SongSearcher.editDistance("abc", "abc")).isEqualTo(0)
        assertThat(SongSearcher.editDistance("abc", "abd")).isEqualTo(1)
        assertThat(SongSearcher.editDistance("abc", "ab")).isEqualTo(1)
        assertThat(SongSearcher.editDistance("", "abc")).isEqualTo(3)
    }
}
