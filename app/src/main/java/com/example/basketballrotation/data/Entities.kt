package com.example.basketballrotation.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class EventType {
    START_LINEUP,
    SUBSTITUTION,
    SCORE,
    FOUL;

    companion object {
        fun fromRaw(raw: String): EventType = entries.firstOrNull { it.name == raw } ?: SCORE
    }
}

@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val jerseyNumber: Int? = null
)

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val opponent: String,
    val createdAt: Long,
    val teamScore: Int = 0,
    val opponentScore: Int = 0,
    val onCourtCsv: String = "",
    val runningTimeMillis: Long = 0,
    val timerLastStartedAt: Long? = null
)

@Entity(
    tableName = "game_events",
    indices = [Index("gameId"), Index("playerId"), Index("relatedPlayerId")]
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
    val gameTimeMillis: Long = 0
)

fun GameEntity.onCourtIds(): List<Long> =
    onCourtCsv.split(',')
        .mapNotNull { value -> value.trim().takeIf { it.isNotEmpty() }?.toLongOrNull() }

fun idsToCsv(ids: Collection<Long>): String = ids.joinToString(",")

fun PlayerEntity.displayLabel(): String =
    if (jerseyNumber != null) "#$jerseyNumber $name" else name