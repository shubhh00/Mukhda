package com.mukhda.data.search

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Source of the bundled index files. An interface so tests can supply JSON directly. */
interface SearchIndexAsset {
    /** Highest bundled version, or null if none is bundled. */
    fun latestVersion(): Int?

    fun read(version: Int): String
}

/** Reads `assets/search_index/v<N>.json`, picking the highest N. */
class BundledSearchIndexAsset @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SearchIndexAsset {

    override fun latestVersion(): Int? =
        context.assets.list(DIR).orEmpty()
            .mapNotNull { FILE_NAME.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull()

    override fun read(version: Int): String =
        context.assets.open("$DIR/v$version.json").bufferedReader().use { it.readText() }

    private companion object {
        const val DIR = "search_index"
        val FILE_NAME = Regex("v(\\d+)\\.json")
    }
}
