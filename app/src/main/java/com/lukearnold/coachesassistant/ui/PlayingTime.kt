package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.data.EventType
import com.lukearnold.coachesassistant.data.GameEntity
import com.lukearnold.coachesassistant.data.GameEventEntity
import com.lukearnold.coachesassistant.data.capToPeriod
import kotlin.math.abs

/**
 * Equal-share playing time for one game: the on-court time, split evenly across the players
 * available to play (the roster minus anyone marked absent).
 */
data class FairShare(
    /** Each player's share of the whole game. */
    val fullGameMillis: Long,
    /** Each player's share of the time played so far: what they'd have if minutes were even. */
    val soFarMillis: Long,
) {
    /** How far [playedMillis] is behind the even share so far; negative when ahead. */
    fun behindMillis(playedMillis: Long): Long = soFarMillis - playedMillis
}

/**
 * [game]'s equal share for each of [availablePlayers] after [elapsedGameMillis] of play, or null
 * if there's no one to share it with or the periods have no length. A share never exceeds the time
 * itself, since no one can play more than all of it (when fewer players are available than court
 * spots).
 */
fun fairShare(game: GameEntity, availablePlayers: Int, elapsedGameMillis: Long): FairShare? {
    if (availablePlayers <= 0 || game.periodDurationMillis <= 0) return null
    fun share(millis: Long) = (millis * game.onCourtSize / availablePlayers).coerceAtMost(millis)
    return FairShare(
        fullGameMillis = share(game.periodCount * game.periodDurationMillis),
        soFarMillis = share(elapsedGameMillis),
    )
}

/**
 * Game time played so far: each completed period's length, as recorded when it ended (so a period
 * cut short counts only what was played), plus [currentPeriodMillis] on the current period's clock.
 * Each is capped at [periodDurationMillis] (if set), for games recorded before the clock stopped
 * itself (see capToPeriod).
 */
fun elapsedGameMillis(events: List<GameEventEntity>, currentPeriodMillis: Long, periodDurationMillis: Long = 0L): Long =
    events.filter { it.type == EventType.PERIOD_CHANGE.name }.sumOf { capToPeriod(it.gameTimeMillis, periodDurationMillis) } +
        capToPeriod(currentPeriodMillis, periodDurationMillis)

/**
 * Bench players in the order to bring them on: whoever is furthest behind their share first.
 * Everyone's share so far is the same, and a benched player's time doesn't change, so that's
 * simply least time played; ties go to whoever has sat longest. The order stays put while the
 * clock runs, rather than reshuffling every second.
 */
fun benchSubOrder(bench: List<PlayerGameStat>): List<PlayerGameStat> =
    bench.sortedWith(compareBy({ it.totalTimeMillis }, { it.benchStartGameTimeMillis ?: 0L }))

/** Within this of their share, a player counts as on pace rather than behind or ahead. */
private const val ON_PACE_MARGIN_MILLIS = 30_000L

/** A short label for [behindMillis]: "2:15 behind", "0:40 ahead", or "on pace". */
fun paceLabel(behindMillis: Long): String = when {
    abs(behindMillis) < ON_PACE_MARGIN_MILLIS -> "on pace"
    behindMillis > 0 -> "${formatDuration(behindMillis)} behind"
    else -> "${formatDuration(-behindMillis)} ahead"
}
