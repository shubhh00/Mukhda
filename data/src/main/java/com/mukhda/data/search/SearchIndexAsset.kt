package com.mukhda.data.search

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The bundled search index files. An interface so tests don't need the app's assets. */
interface SearchIndexAsset {
    /** Highest bundled version, or null if none is bundled. */
    fun latestVersion(): Int?

    /** The asset path of [version]'s ready-built database. */
    fun path(version: Int): String
}

/** `assets/search_index/v<N>.db`, picking the highest N. */
class BundledSearchIndexAsset @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SearchIndexAsset {

    override fun latestVersion(): Int? =
        context.assets.list(DIR).orEmpty()
            .mapNotNull { FILE_NAME.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull()

    override fun path(version: Int): String = "$DIR/v$version.db"

    private companion object {
        const val DIR = "search_index"
        val FILE_NAME = Regex("v(\\d+)\\.db")
    }
}
