package com.example.basketballrotation.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class EventType {
    START_LINEUP,
    SUBSTITUTION,
    SCORE,
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
    val useQuarters: Boolean = false,
    val currentPeriod: Int = 1,
    val periodDurationMillis: Long = 10 * 60 * 1000L, // Default 10 mins
    @ColumnInfo(defaultValue = "")
    val absentPlayerIdsCsv: String = "",
)

@Entity(
    tableName = "game_events",
    indices = [Index("gameId"), Index("playerId"), Index("relatedPlayerId")],
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

fun PlayerEntity.displayLabel(): String = if (jerseyNumber != null) "#$jerseyNumber $name" else name
