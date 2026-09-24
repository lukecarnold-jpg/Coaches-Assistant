package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.data.*
import java.text.SimpleDateFormat
import java.util.*

fun eventDescription(
    event: GameEventEntity,
    playersById: Map<Long, PlayerEntity>,
    opponentName: String = "",
    infractionLabel: String = "Foul",
): String {
    return when (EventType.fromRaw(event.type)) {
        EventType.START_LINEUP -> {
            val names = event.note.split(',')
                .asSequence()
                .mapNotNull { raw -> raw.trim().toLongOrNull() }
                .mapNotNull(playersById::get)
                .joinToString(" | ") { it.displayLabel() }

            "Starting lineup: $names"
        }

        EventType.SUBSTITUTION -> {
            val playerIn = event.playerId?.let(playersById::get)?.displayLabel().orEmpty()
            val playerOut = event.relatedPlayerId?.let(playersById::get)?.displayLabel().orEmpty()
            "$playerIn in for $playerOut"
        }

        EventType.SCORE -> {
            val playerName = event.playerId?.let(playersById::get)?.displayLabel().orEmpty()
            "$playerName +${event.points}"
        }

        EventType.OPPONENT_SCORE -> {
            val prefix = if (event.points >= 0) "+" else ""
            val label = opponentName.ifBlank { "Opponent" }
            "$label $prefix${event.points}"
        }

        EventType.FOUL -> {
            val playerName = event.playerId?.let(playersById::get)?.displayLabel().orEmpty()
            "$playerName ${infractionLabel.lowercase()}"
        }

        EventType.MVP_VOTE -> {
            val playerName = event.playerId?.let(playersById::get)?.displayLabel().orEmpty()
            "$playerName MVP vote"
        }

        EventType.PERIOD_CHANGE -> event.note
        EventType.GAME_END -> "Game Ended"
    }
}

fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

/** The period counts a new game can be set up with, and how each is labelled. */
val PERIOD_COUNT_OPTIONS = listOf(2 to "2 halves", 3 to "3 periods", 4 to "4 quarters")

/** Short label for the game's current period: "H1" for halves, "Q3" for quarters, "P2" otherwise. */
fun GameEntity.periodLabel(): String {
    val prefix = when (periodCount) {
        2 -> "H"
        4 -> "Q"
        else -> "P"
    }
    return "$prefix$currentPeriod"
}
