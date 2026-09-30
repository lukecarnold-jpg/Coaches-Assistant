package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.R
import com.lukearnold.coachesassistant.data.EventType
import com.lukearnold.coachesassistant.data.GameEntity
import com.lukearnold.coachesassistant.data.GameEventEntity
import com.lukearnold.coachesassistant.data.PlayerEntity
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
fun paceLabel(behindMillis: Long): UiText = when {
    abs(behindMillis) < ON_PACE_MARGIN_MILLIS -> uiText(R.string.pace_on_pace)
    behindMillis > 0 -> uiText(R.string.pace_behind, formatDuration(behindMillis))
    else -> uiText(R.string.pace_ahead, formatDuration(-behindMillis))
}

/** How far ahead of and behind their share players must be before a substitution is suggested. */
const val SUB_SUGGESTION_MARGIN_MILLIS = 120_000L

/**
 * A suggested substitution: [playerOff] is [offAheadMillis] ahead of their share and [playerOn]
 * is [onBehindMillis] behind it.
 */
data class SubSuggestion(
    val playerOff: PlayerGameStat,
    val playerOn: PlayerGameStat,
    val offAheadMillis: Long,
    val onBehindMillis: Long,
) {
    /** Identifies the pair, e.g. for "Later" to hide just this suggestion. */
    val key: Pair<Long, Long> get() = playerOff.player.id to playerOn.player.id
}

/**
 * The substitution that would even out playing time most, as of [gameTimeMillis]: the on-court
 * player furthest ahead of their [share] off, and the bench player furthest behind on, when each
 * is at least [SUB_SUGGESTION_MARGIN_MILLIS] from it. Null when minutes are even enough, or
 * there's no share to compare with.
 */
fun subSuggestion(
    onCourt: List<PlayerGameStat>,
    bench: List<PlayerGameStat>,
    share: FairShare?,
    gameTimeMillis: Long,
    /** Players never suggested, on or off (see subSuggestionExclusions). */
    excluded: Set<Long> = emptySet(),
): SubSuggestion? {
    if (share == null) return null
    val off = onCourt.filter { it.player.id !in excluded }
        .maxByOrNull { -share.behindMillis(it.totalTimeAt(gameTimeMillis)) } ?: return null
    val on = benchSubOrder(bench.filter { it.player.id !in excluded }).firstOrNull() ?: return null
    val offAhead = -share.behindMillis(off.totalTimeAt(gameTimeMillis))
    val onBehind = share.behindMillis(on.totalTimeAt(gameTimeMillis))
    if (offAhead < SUB_SUGGESTION_MARGIN_MILLIS || onBehind < SUB_SUGGESTION_MARGIN_MILLIS) return null
    return SubSuggestion(off, on, offAhead, onBehind)
}

/**
 * Players substitution suggestions leave out: the keeper, in sports where the keeper plays through
 * (their position is [config]'s keeperPosition), since they'd otherwise always look "ahead"; and
 * anyone taken off without a replacement (e.g. injured), who'd otherwise soon be suggested back
 * on, until the coach brings them back.
 */
fun subSuggestionExclusions(players: List<PlayerEntity>, config: SportConfig, events: List<GameEventEntity>): Set<Long> {
    val keepers = config.keeperPosition?.let { keeper -> players.filter { it.position == keeper }.map { it.id } }.orEmpty()
    return keepers.toSet() + offWithoutReplacement(events)
}

/**
 * Players whose last move in [events] was going off without a replacement: out until they come
 * back on (a substitution, joining without a swap, or a starting lineup).
 */
fun offWithoutReplacement(events: List<GameEventEntity>): Set<Long> {
    val out = mutableSetOf<Long>()
    events.sortedWith(compareBy({ it.timestamp }, { it.id })).forEach { event ->
        when (EventType.fromRaw(event.type)) {
            EventType.SUBSTITUTION -> {
                event.playerId?.let { out -= it }
                if (event.playerId == null) event.relatedPlayerId?.let { out += it }
            }
            EventType.START_LINEUP -> out -= event.note.split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()
            else -> Unit
        }
    }
    return out
}

