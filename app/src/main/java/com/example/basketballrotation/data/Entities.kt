package com.example.basketballrotation.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class EventType {
    START_LINEUP,
    SUBSTITUTION,
    SCORE,
    OPPONENT_SCORE,
    FOUL,
    MVP_VOTE,
    PERIOD_CHANGE,
    GAME_END;

    companion object {
        fun fromRaw(raw: String): EventType = entries.firstOrNull { it.name == raw } ?: SCORE
    }
}

@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val jerseyNumber: Int? = null,
    @ColumnInfo(defaultValue = "BASKETBALL")
    val sport: String = Sport.BASKETBALL.name,
    @ColumnInfo(defaultValue = "NULL")
    val position: String? = null,
)

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val opponent: String,
    val createdAt: Long,
    val teamScore: Int = 0,
    val opponentScore: Int = 0,
    val mvpVotesJson: String = "{}", // Map<Long, Int> serialized
    val onCourtCsv: String = "",
    val runningTimeMillis: Long = 0, // Elapsed time in current period
    val timerLastStartedAt: Long? = null,
    val isFinished: Boolean = false,
    val finishedAt: Long? = null,
    val currentPeriod: Int = 1,
    val periodDurationMillis: Long = 10 * 60 * 1000L, // Default 10 mins
    @ColumnInfo(defaultValue = "")
    val absentPlayerIdsCsv: String = "",
    @ColumnInfo(defaultValue = "")
    val notes: String = "",
    @ColumnInfo(defaultValue = "BASKETBALL")
    val sport: String = Sport.BASKETBALL.name,
    @ColumnInfo(defaultValue = "5")
    val onCourtSize: Int = 5,
    /** How many periods the game has, e.g. 2 halves, 3 periods (Ice Hockey), or 4 quarters. */
    @ColumnInfo(defaultValue = "2")
    val periodCount: Int = 2,
)

@Entity(
    tableName = "game_events",
    indices = [Index("gameId"), Index("playerId"), Index("relatedPlayerId")],
    foreignKeys = [
        // A game's events are meaningless without it, so they're deleted with it — this is what
        // GameRepository.deleteGame already did manually; the constraint just makes that
        // guarantee hold everywhere, not only through that one code path.
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
        // Events are historical game records, not player records, so deleting a player clears the
        // reference rather than the event (eventDescription() already renders a missing player as
        // a blank name, so this just makes that the only way a dangling id shows up, instead of a
        // stale id nothing points to).
        ForeignKey(
            entity = PlayerEntity::class,
            parentColumns = ["id"],
            childColumns = ["playerId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = PlayerEntity::class,
            parentColumns = ["id"],
            childColumns = ["relatedPlayerId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class GameEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    val timestamp: Long,
    val type: String,
    val playerId: Long? = null,
    val relatedPlayerId: Long? = null,
    val points: Int = 0,
    val note: String = "",
    val gameTimeMillis: Long = 0,
)

fun GameEntity.onCourtIds(): List<Long> =
    onCourtCsv.split(',')
        .mapNotNull { value -> value.trim().takeIf { it.isNotEmpty() }?.toLongOrNull() }

fun GameEntity.absentPlayerIds(): List<Long> =
    absentPlayerIdsCsv.split(',')
        .mapNotNull { value -> value.trim().takeIf { it.isNotEmpty() }?.toLongOrNull() }

fun idsToCsv(ids: Collection<Long>): String = ids.joinToString(",")

/**
 * Elapsed time in the current period as of [now], capped at [GameEntity.periodDurationMillis].
 * The cap is what keeps a clock nobody stopped (app backgrounded, coach distracted) from running
 * past the end of the period and crediting on-court players with minutes that were never played.
 */
fun GameEntity.currentGameTimeMillis(now: Long = System.currentTimeMillis()): Long {
    val elapsed = runningTimeMillis + (timerLastStartedAt?.let { now - it } ?: 0L)
    return if (periodDurationMillis > 0) elapsed.coerceIn(0L, periodDurationMillis) else maxOf(0L, elapsed)
}

/** Time until the period's clock runs out as of [now], or null if the period has no set length. */
fun GameEntity.remainingPeriodTimeMillis(now: Long = System.currentTimeMillis()): Long? =
    if (periodDurationMillis > 0) periodDurationMillis - currentGameTimeMillis(now) else null

/**
 * Strips [playerId] from this game's on-court and absent lists, e.g. when the player is deleted
 * from the roster. Without this, a deleted player's id lingers in [GameEntity.onCourtCsv] forever:
 * the on-court count still matches the sport's required size, so the game never re-prompts for a
 * starting lineup, but the player has no card in the UI (it's filtered by current roster) — leaving
 * a slot that looks occupied but can never be subbed into.
 */
fun GameEntity.withoutPlayer(playerId: Long): GameEntity {
    val onCourt = onCourtIds()
    val absent = absentPlayerIds()
    if (!onCourt.contains(playerId) && !absent.contains(playerId)) return this

    return copy(
        onCourtCsv = idsToCsv(onCourt - playerId),
        absentPlayerIdsCsv = idsToCsv(absent - playerId),
    )
}

/**
 * Recomputes the game's running totals (team score, opponent score, MVP tally) from its [events],
 * which are the source of truth; the totals are stored on the game only so lists and exports
 * don't need to load every event. Scores never drop below zero.
 */
fun GameEntity.withTotalsFrom(events: List<GameEventEntity>): GameEntity {
    fun sumOf(type: EventType) = events.filter { it.type == type.name }.sumOf { it.points }

    val mvpVotes = events
        .filter { it.type == EventType.MVP_VOTE.name }
        .mapNotNull { it.playerId }
        .groupingBy { it }
        .eachCount()

    return copy(
        teamScore = sumOf(EventType.SCORE).coerceAtLeast(0),
        opponentScore = sumOf(EventType.OPPONENT_SCORE).coerceAtLeast(0),
        mvpVotesJson = MvpVotes.serialize(mvpVotes),
    )
}

fun PlayerEntity.displayLabel(): String = if (jerseyNumber != null) "#$jerseyNumber $name" else name
