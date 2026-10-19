package com.mukhda.data.show

import kotlinx.serialization.Serializable

/** `getShow()`'s answer (AGENTS.md §7). Enums arrive as their names. */
@Serializable
data class ShowDto(
    val date: String,
    val showNo: Int,
    val endsAt: String,
    val tiers: Map<String, TierDto>,
)

@Serializable
data class TierDto(
    val previewUrl: String,
    val startOffsetMs: Long = 0,
    val status: String,
    val triesUsed: Int,
    val currentStep: Int,
    val tries: List<TryDto> = emptyList(),
    /** Only for a round that's over. */
    val reveal: RevealDto? = null,
)

@Serializable
data class TryDto(
    val requestId: String,
    val type: String,
    val songId: String? = null,
    val result: String,
)

@Serializable
data class RevealDto(
    val songId: String,
    val title: String,
    val filmTitle: String,
    val year: Int,
    val composer: String? = null,
    val artworkUrl: String? = null,
    val appleMusicUrl: String? = null,
    val topPercent: Int? = null,
)

/** `submitTry()`'s answer: how the try was judged and where the round stands now. */
@Serializable
data class SubmitTryDto(
    val result: String,
    val triesUsed: Int,
    val currentStep: Int,
    val status: String,
    val reveal: RevealDto? = null,
)
