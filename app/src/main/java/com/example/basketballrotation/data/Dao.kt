package com.example.basketballrotation.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players ORDER BY COALESCE(jerseyNumber, 9999), name COLLATE NOCASE")
    fun observeAll(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players ORDER BY COALESCE(jerseyNumber, 9999), name COLLATE NOCASE")
    suspend fun getAll(): List<PlayerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(player: PlayerEntity): Long
}

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :gameId LIMIT 1")
    suspend fun getById(gameId: Long): GameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(game: GameEntity): Long

    @Update
    suspend fun update(game: GameEntity)
}

@Dao
interface GameEventDao {
    @Query("SELECT * FROM game_events WHERE gameId = :gameId ORDER BY timestamp DESC, id DESC")
    fun observeByGame(gameId: Long): Flow<List<GameEventEntity>>

    @Query("SELECT * FROM game_events WHERE gameId = :gameId ORDER BY timestamp ASC, id ASC")
    suspend fun getByGame(gameId: Long): List<GameEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: GameEventEntity): Long
}