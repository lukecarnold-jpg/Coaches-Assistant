package com.lonrad.coachesassistant.ui

import com.lonrad.coachesassistant.R
import com.lonrad.coachesassistant.data.*
import java.text.DateFormat
import java.util.*

/**
 * The event log's one-line description of [event]. Player names and numbers are shown as they
 * are; the words around them are translated.
 */
fun eventDescription(
    event: GameEventEntity,
    playersById: Map<Long, PlayerEntity>,
    opponentName: String = "",
    infraction: Infraction = Infraction.FOUL,
): UiText {
    fun playerLabel(id: Long?) = id?.let(playersById::get)?.displayLabel().orEmpty()
    fun signed(points: Int) = if (points >= 0) "+$points" else points.toString()

    return when (EventType.fromRaw(event.type)) {
        EventType.START_LINEUP -> {
            val names = event.note.split(',')
                .asSequence()
                .mapNotNull { raw -> raw.trim().toLongOrNull() }
                .mapNotNull(playersById::get)
                .joinToString(" | ") { it.displayLabel() }

            uiText(R.string.event_starting_lineup, names)
        }

        EventType.SUBSTITUTION -> when {
            event.playerId != null && event.relatedPlayerId != null ->
                uiText(R.string.event_substitution, playerLabel(event.playerId), playerLabel(event.relatedPlayerId))

            // One side missing: joined without a swap, went off without a replacement, or the
            // other player has since been removed from the roster.
            event.playerId != null -> uiText(R.string.event_comes_on, playerLabel(event.playerId))

            event.relatedPlayerId != null -> uiText(R.string.event_goes_off, playerLabel(event.relatedPlayerId))

            else -> uiText(R.string.event_substitution, "", "")
        }

        EventType.SCORE -> {
            val label = when {
                event.playerId != null -> UiText.Plain(playerLabel(event.playerId))

                event.note == TEAM_ADJUSTMENT_NOTE -> uiText(R.string.team_adjustment)

                // playerId is nulled when the player is deleted from the roster.
                else -> uiText(R.string.removed_player)
            }
            uiText(R.string.event_score, label, signed(event.points))
        }

        EventType.OPPONENT_SCORE -> {
            val label = opponentName.ifBlank { null }?.let { UiText.Plain(it) } ?: uiText(R.string.opponent)
            uiText(R.string.event_score, label, signed(event.points))
        }

        EventType.FOUL -> uiText(infraction.event, playerLabel(event.playerId))

        EventType.MVP_VOTE -> uiText(R.string.event_mvp_vote, playerLabel(event.playerId))

        // The stored note ("Finished Period 2") is English; show the period in the current language.
        EventType.PERIOD_CHANGE -> FINISHED_PERIOD_NOTE.matchEntire(event.note)
            ?.let { uiText(R.string.event_finished_period, it.groupValues[1].toInt()) }
            ?: UiText.Plain(event.note)

        EventType.GAME_END -> uiText(R.string.event_game_ended)
    }
}

private val FINISHED_PERIOD_NOTE = Regex("""Finished Period (\d+)""")

/** A date and time, e.g. when a game finished, in the language's usual format. */
fun formatTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(Date(timestamp))

/**
 * Which period each of [events] happened in, by event id: one more than the periods finished
 * before it. A period's own end-of-period event counts as in that period.
 */
fun eventPeriods(events: List<GameEventEntity>): Map<Long, Int> {
    var period = 1
    return events.sortedWith(compareBy({ it.timestamp }, { it.id })).associate { event ->
        (event.id to period).also { if (event.type == EventType.PERIOD_CHANGE.name) period++ }
    }
}

/**
 * How the game clock read [elapsedMillis] into a period: the time left, since the clock counts
 * down, or the time played if periods have no set length.
 */
fun clockReading(elapsedMillis: Long, periodDurationMillis: Long): String =
    if (periodDurationMillis > 0) {
        formatDuration(periodDurationMillis - capToPeriod(elapsedMillis, periodDurationMillis))
    } else {
        formatDuration(maxOf(0L, elapsedMillis))
    }

fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

/** The period counts a new game can be set up with, and how each is labelled. */
val PERIOD_COUNT_OPTIONS = listOf(
    2 to R.string.period_count_halves,
    3 to R.string.period_count_periods,
    4 to R.string.period_count_quarters,
)

/** Short label for the game's current period: "H1" for halves, "Q3" for quarters, "P2" otherwise. */
fun GameEntity.periodLabel(): UiText = periodLabel(currentPeriod)

/** Short label for period [period] of this game. */
fun GameEntity.periodLabel(period: Int): UiText = uiText(
    when (periodCount) {
        2 -> R.string.period_label_half
        4 -> R.string.period_label_quarter
        else -> R.string.period_label_other
    },
    period,
)
