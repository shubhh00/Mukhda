package com.mukhda.data.show

import com.mukhda.domain.audio.Preview
import com.mukhda.domain.model.RoundStatus
import com.mukhda.domain.model.Tier
import com.mukhda.domain.model.TryResult
import com.mukhda.domain.model.TryType
import com.mukhda.domain.round.RoundState
import com.mukhda.domain.round.SongReveal
import com.mukhda.domain.round.TryRecord
import com.mukhda.domain.show.NextShow
import com.mukhda.domain.show.Show
import java.time.Instant
import java.time.LocalDate

/** Tiers the app doesn't know (a server ahead of an old app) are left out rather than failing. */
fun ShowDto.toShow(): Show {
    val tiers = tiers.mapNotNull { (name, tier) -> Tier.entries.firstOrNull { it.name == name }?.let { it to tier } }.toMap()
    return Show(
        date = LocalDate.parse(date),
        showNo = showNo,
        endsAt = Instant.parse(endsAt),
        rounds = tiers.mapValues { (tier, dto) -> dto.toRoundState(tier) },
        previews = tiers.mapValues { (_, dto) -> Preview(dto.previewUrl, dto.startOffsetMs) },
        serverTime = serverTime?.let(Instant::parse),
        next = next?.let { next ->
            NextShow(LocalDate.parse(next.date), next.previews.knownTiers().mapValues { (_, p) -> Preview(p.previewUrl, p.startOffsetMs) })
        },
    )
}

private fun <T> Map<String, T>.knownTiers(): Map<Tier, T> =
    mapNotNull { (name, value) -> Tier.entries.firstOrNull { it.name == name }?.let { it to value } }.toMap()

fun TierDto.toRoundState(tier: Tier): RoundState {
    val records = tries.map { it.toTryRecord() }
    return RoundState(
        tier = tier,
        tries = records,
        status = RoundStatus.valueOf(status),
        // The step a round was won at is the position of its correct try.
        solvedAtStep = records.indexOfFirst { it.result == TryResult.CORRECT }.takeIf { it >= 0 },
        reveal = reveal?.toSongReveal(),
    )
}

fun TryDto.toTryRecord(): TryRecord = TryRecord(
    requestId = requestId,
    type = TryType.valueOf(type),
    guessedSongId = songId,
    result = TryResult.valueOf(result),
)

fun RevealDto.toSongReveal(): SongReveal = SongReveal(
    songId = songId,
    title = title,
    filmTitle = filmTitle,
    year = year,
    artworkUrl = artworkUrl,
    appleMusicUrl = appleMusicUrl,
    composer = composer,
    topPercent = topPercent,
)
