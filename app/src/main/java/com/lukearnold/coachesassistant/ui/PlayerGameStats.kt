package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.data.EventType
import com.lukearnold.coachesassistant.data.GameEntity
import com.lukearnold.coachesassistant.data.GameEventEntity
import com.lukearnold.coachesassistant.data.GameLineup
import com.lukearnold.coachesassistant.data.MvpVotes
import com.lukearnold.coachesassistant.data.PlayerEntity
import com.lukearnold.coachesassistant.data.capToPeriod

data class PlayerGameStat(
    val player: PlayerEntity,
    val points: Int,
    val fouls: Int,
    val mvpVotes: Int,
    val timesSubbedOff: Int,
    val onCourt: Boolean,
    val currentShiftTimeMillis: Long,
    val currentBenchTimeMillis: Long,
    val shiftStartGameTimeMillis: Long?,
    val totalTimeMillis: Long,
    /** Game time the player last went to the bench this period (0 if never on court), or null on court. */
    val benchStartGameTimeMillis: Long? = null,
    /** Whether they were on court at any point: in a starting lineup, subbed on, or on now. */
    val wasOnCourt: Boolean = false,
) {
    // The *TimeMillis fields above are as of the game's stored running time, which only advances
    // when the clock is stopped. These give the same figures as of [gameTimeMillis], the live
    // clock, so on-screen times keep moving while the clock runs.

    fun shiftTimeAt(gameTimeMillis: Long): Long =
        shiftStartGameTimeMillis?.let { maxOf(0L, gameTimeMillis - it) } ?: 0L

    fun benchTimeAt(gameTimeMillis: Long): Long =
        benchStartGameTimeMillis?.let { maxOf(0L, gameTimeMillis - it) } ?: 0L

    fun totalTimeAt(gameTimeMillis: Long): Long =
        totalTimeMillis - currentShiftTimeMillis + shiftTimeAt(gameTimeMillis)
}

/**
 * Replays [events] in chronological order to derive each roster player's current on-court/bench
 * status (from [lineup]), shift and bench durations, and per-game totals as of [game]'s current
 * running time.
 *
 * A pure function (no Android/coroutine dependencies) so it can be unit tested directly.
 */
fun calculatePlayerGameStats(
    playerList: List<PlayerEntity>,
    game: GameEntity?,
    lineup: GameLineup,
    events: List<GameEventEntity>,
): List<PlayerGameStat> {
    // Times are read capped at the period length: games recorded before the clock stopped itself
    // can have events stamped hours past the end of a period (see capToPeriod).
    val periodLength = game?.periodDurationMillis ?: 0L
    fun GameEventEntity.at(): Long = capToPeriod(gameTimeMillis, periodLength)
    val runningTime = capToPeriod(game?.runningTimeMillis ?: 0L, periodLength)

    val onCourt = lineup.onCourtIds
    val absent = lineup.absentIds

    val totalTimeByPlayer = mutableMapOf<Long, Long>()
    val lastEnteredAt = mutableMapOf<Long, Long>()
    val lastBenchedAt = mutableMapOf<Long, Long>()
    val pointsByPlayer = mutableMapOf<Long, Int>()
    val foulsByPlayer = mutableMapOf<Long, Int>()
    val timesSubbedOffByPlayer = mutableMapOf<Long, Int>()
    val everOnCourt = onCourt.toMutableSet()

    // Process events chronologically to calculate stats and times
    val sortedEvents = events.sortedWith(compareBy<GameEventEntity>({ it.timestamp }, { it.id }))

    sortedEvents.forEach { event ->
        when (EventType.fromRaw(event.type)) {
            EventType.START_LINEUP -> {
                // note contains the player IDs
                event.note.split(',')
                    .mapNotNull { it.trim().toLongOrNull() }
                    .forEach { id ->
                        everOnCourt += id
                        lastEnteredAt[id] = event.at()
                        lastBenchedAt.remove(id)
                    }
            }

            EventType.SUBSTITUTION -> {
                event.playerId?.let { playerInId ->
                    everOnCourt += playerInId
                    lastEnteredAt[playerInId] = event.at()
                    lastBenchedAt.remove(playerInId)
                }
                event.relatedPlayerId?.let { playerOutId ->
                    timesSubbedOffByPlayer[playerOutId] = (timesSubbedOffByPlayer[playerOutId] ?: 0) + 1
                    val entered = lastEnteredAt.remove(playerOutId)
                    if (entered != null) {
                        val duration = maxOf(0L, event.at() - entered)
                        totalTimeByPlayer[playerOutId] = (totalTimeByPlayer[playerOutId] ?: 0L) + duration
                    }
                    lastBenchedAt[playerOutId] = event.at()
                }
            }

            EventType.SCORE -> event.playerId?.let { playerId ->
                pointsByPlayer[playerId] = (pointsByPlayer[playerId] ?: 0) + event.points
            }

            EventType.FOUL -> event.playerId?.let { playerId ->
                foulsByPlayer[playerId] = (foulsByPlayer[playerId] ?: 0) + event.points
            }

            EventType.PERIOD_CHANGE -> {
                // Close all active shifts using the event's gameTimeMillis (which is end of period)
                lastEnteredAt.forEach { (id, entered) ->
                    val duration = maxOf(0L, event.at() - entered)
                    totalTimeByPlayer[id] = (totalTimeByPlayer[id] ?: 0L) + duration
                }
                lastEnteredAt.clear()
                // The clock restarts at 0 each period, so a sub-off time from an earlier period
                // can't be compared against the new period's clock; bench time counts from the
                // start of the current period instead, just as shift time does.
                lastBenchedAt.clear()
            }

            EventType.GAME_END -> {
                lastEnteredAt.forEach { (id, entered) ->
                    val duration = maxOf(0L, event.at() - entered)
                    totalTimeByPlayer[id] = (totalTimeByPlayer[id] ?: 0L) + duration
                }
                lastEnteredAt.clear()
            }

            EventType.OPPONENT_SCORE,
            EventType.MVP_VOTE -> {}
        }
    }

    val mvpVotesMap = MvpVotes.parse(game?.mvpVotesJson.orEmpty())

    return playerList
        .asSequence()
        .filter { !absent.contains(it.id) }
        .map { player ->
            val isOnCourt = onCourt.contains(player.id)
            val enteredAt = lastEnteredAt[player.id]
                ?: runningTime

            val currentShift = if (isOnCourt) {
                maxOf(0L, runningTime - enteredAt)
            } else {
                0L
            }

            val benchedAt = lastBenchedAt[player.id] ?: 0L
            val currentBench = if (isOnCourt) {
                0L
            } else {
                maxOf(0L, runningTime - benchedAt)
            }

            val totalTime = (totalTimeByPlayer[player.id] ?: 0L) + currentShift

            PlayerGameStat(
                player = player,
                points = pointsByPlayer[player.id] ?: 0,
                // Never below zero, even if undoing a foul leaves more removals than additions.
                fouls = (foulsByPlayer[player.id] ?: 0).coerceAtLeast(0),
                mvpVotes = mvpVotesMap[player.id] ?: 0,
                timesSubbedOff = timesSubbedOffByPlayer[player.id] ?: 0,
                onCourt = isOnCourt,
                currentShiftTimeMillis = currentShift,
                currentBenchTimeMillis = currentBench,
                shiftStartGameTimeMillis = if (isOnCourt) enteredAt else null,
                totalTimeMillis = totalTime,
                benchStartGameTimeMillis = if (isOnCourt) null else benchedAt,
                wasOnCourt = player.id in everOnCourt,
            )
        }
        .toList()
}
