package com.mukhda.domain.search

import java.text.Normalizer
import java.util.Locale

/**
 * Normalisation shared by the search index and the query, so that
 * romanisation variants meet: "Kabhee" ~ "Kabhi", "Phir" ~ "Fir", "Zara" ~ "Jara".
 */
object SearchNormalizer {
    private val combiningMarks = Regex("\\p{Mn}+")
    private val apostrophes = Regex("['’`\"]")
    private val nonAlphanumeric = Regex("[^a-z0-9]+")
    private val repeatedLetters = Regex("([a-z])\\1+")

    // Applied in order, before collapsing repeats, so "ee"/"oo" still exist to be mapped.
    private val phonetic = listOf("ee" to "i", "oo" to "u", "ph" to "f", "w" to "v", "z" to "j")

    fun normalize(text: String): String {
        var s = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)
            .replace(apostrophes, "")
            .replace(nonAlphanumeric, " ")
            .trim()
        for ((from, to) in phonetic) s = s.replace(from, to)
        return s.replace(repeatedLetters, "$1")
    }

    fun tokens(normalized: String): List<String> =
        if (normalized.isEmpty()) emptyList() else normalized.split(' ')

    /** Second key with spaces removed, so "kalho" finds "Kal Ho Naa Ho". */
    fun joined(normalized: String): String = normalized.replace(" ", "")
}
