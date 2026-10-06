package com.lukearnold.coachesassistant.data

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
    GAME_END,
    ;

    /** Whether the event log can correct this event's points: scores and infraction adjustments. */
    val hasEditablePoints: Boolean
        get() = this == SCORE || this == OPPONENT_SCORE || this == FOUL

    /**
     * Whether this event can be removed (or undone) on its own. A substitution is removed by
     * swapping its players back. Lineup, period, and game-end events change more than the event
     * itself (the whole lineup, the period, the clock), and playing time is counted from them, so
     * they stay.
     */
    val isRemovable: Boolean
        get() = this == SUBSTITUTION || this == SCORE || this == OPPONENT_SCORE || this == FOUL || this == MVP_VOTE

    companion object {
        fun fromRaw(raw: String): EventType = entries.firstOrNull { it.name == raw } ?: SCORE
    }
}

// Indexed by sport: every roster screen and query reads one sport's players.
@Entity(tableName = "players", indices = [Index("sport")])
data class PlayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val jerseyNumber: Int? = null,
    @ColumnInfo(defaultValue = "BASKETBALL")
    val sport: String = Sport.BASKETBALL.name,
    @ColumnInfo(defaultValue = "NULL")
    val position: String? = null,
)

// Indexed by sport: every game list and season total reads one sport's games.
@Entity(tableName = "games", indices = [Index("sport")])
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val opponent: String,
    val createdAt: Long,
    val teamScore: Int = 0,
    val opponentScore: Int = 0,
    val mvpVotesJson: String = "{}", // Map<Long, Int> serialized
    val runningTimeMillis: Long = 0, // Elapsed time in current period
    val timerLastStartedAt: Long? = null,
    val isFinished: Boolean = false,
    val finishedAt: Long? = null,
    val currentPeriod: Int = 1,
    val periodDurationMillis: Long = 10 * 60 * 1000L, // Default 10 mins
    @ColumnInfo(defaultValue = "")
    val notes: String = "",
    @ColumnInfo(defaultValue = "BASKETBALL")
    val sport: String = Sport.BASKETBALL.name,
    @ColumnInfo(defaultValue = "5")
    val onCourtSize: Int = 5,
    /** How many periods the game has, e.g. 2 halves, 3 periods (Ice Hockey), or 4 quarters. */
    @ColumnInfo(defaultValue = "2")
    val periodCount: Int = 2,
    /**
     * Cricket: runs our innings got from extras (wides, no-balls, byes, leg byes), on top of the
     * batters' runs. teamScore is the batters' runs plus these; opponentScore is the other team's runs.
     */
    @ColumnInfo(defaultValue = "0")
    val extras: Int = 0,
    /** Cricket: wickets we lost, counted from the scorecards' dismissals (like teamScore, kept for lists). */
    @ColumnInfo(defaultValue = "0")
    val teamWickets: Int = 0,
    /** Cricket: wickets the other team lost. */
    @ColumnInfo(defaultValue = "0")
    val opponentWickets: Int = 0,
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

/**
 * A player's status in one game: on court, marked absent, or (in principle) both. Players with
 * neither status have no row. The foreign keys mean deleting a game or a player removes their rows,
 * so a deleted player can never be left occupying an on-court slot.
 */
@Entity(
    tableName = "game_players",
    primaryKeys = ["gameId", "playerId"],
    indices = [Index("playerId")],
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlayerEntity::class,
            parentColumns = ["id"],
            childColumns = ["playerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class GamePlayerEntity(
    val gameId: Long,
    val playerId: Long,
    val onCourt: Boolean = false,
    val absent: Boolean = false,
)

/** Who is on court and who is absent in one game, built from its [GamePlayerEntity] rows. */
data class GameLineup(
    val onCourtIds: Set<Long> = emptySet(),
    val absentIds: Set<Long> = emptySet(),
) {
    fun toEntities(gameId: Long): List<GamePlayerEntity> =
        (onCourtIds + absentIds).map { playerId ->
            GamePlayerEntity(gameId, playerId, onCourt = playerId in onCourtIds, absent = playerId in absentIds)
        }

    companion object {
        fun from(rows: List<GamePlayerEntity>) = GameLineup(
            onCourtIds = rows.filter { it.onCourt }.map { it.playerId }.toSet(),
            absentIds = rows.filter { it.absent }.map { it.playerId }.toSet(),
        )
    }
}

/**
 * The note on a [EventType.SCORE] event that adjusts the team's score without crediting a player.
 * It tells such an event apart from a player's score whose player was later deleted, since both
 * have a null playerId.
 */
const val TEAM_ADJUSTMENT_NOTE = "Team adjustment"

fun idsToCsv(ids: Collection<Long>): String = ids.joinToString(",")

/**
 * Elapsed time in the current period as of [now], capped at [GameEntity.periodDurationMillis].
 * The cap is what keeps a clock nobody stopped (app backgrounded, coach distracted) from running
 * past the end of the period and crediting on-court players with minutes that were never played.
 */
fun GameEntity.currentGameTimeMillis(now: Long = System.currentTimeMillis()): Long =
    capToPeriod(runningTimeMillis + (timerLastStartedAt?.let { now - it } ?: 0L), periodDurationMillis)

/**
 * The latest clock time recorded by an event in the current period, given a game's [events] in
 * the order they happened (0 if none). The clock can't be moved earlier than this: events would
 * then sit "in the future", and shifts that started at them would count as zero.
 */
fun latestEventTimeThisPeriod(events: List<GameEventEntity>, periodDurationMillis: Long): Long =
    events.takeLastWhile { it.type != EventType.PERIOD_CHANGE.name }
        .maxOfOrNull { capToPeriod(it.gameTimeMillis, periodDurationMillis) } ?: 0L

/**
 * [timeMillis] kept within a period of [periodDurationMillis] (or just non-negative if the period
 * has no set length). New games can't record time past the end of a period, but games from before
 * the clock stopped itself can: a clock left running for hours stamped its events with that time.
 */
fun capToPeriod(timeMillis: Long, periodDurationMillis: Long): Long =
    if (periodDurationMillis > 0) timeMillis.coerceIn(0L, periodDurationMillis) else maxOf(0L, timeMillis)

/** Time until the period's clock runs out as of [now], or null if the period has no set length. */
fun GameEntity.remainingPeriodTimeMillis(now: Long = System.currentTimeMillis()): Long? =
    if (periodDurationMillis > 0) periodDurationMillis - currentGameTimeMillis(now) else null

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
