package com.example.basketballrotation.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players ORDER BY jerseyNumber, name COLLATE NOCASE")
    fun observeAll(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players ORDER BY jerseyNumber, name COLLATE NOCASE")
    suspend fun getAll(): List<PlayerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(player: PlayerEntity): Long

    @Update
    suspend fun update(player: PlayerEntity)

    @Query("DELETE FROM players WHERE id = :playerId")
    suspend fun deleteById(playerId: Long)

    @Query("DELETE FROM players")
    suspend fun deleteAll()
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

    @Query("DELETE FROM games WHERE id = :gameId")
    suspend fun deleteById(gameId: Long)

    @Query("DELETE FROM games")
    suspend fun deleteAll()
}

@Dao
interface GameEventDao {
    @Query("SELECT * FROM game_events WHERE gameId = :gameId ORDER BY timestamp DESC, id DESC")
    fun observeByGame(gameId: Long): Flow<List<GameEventEntity>>

    @Query("SELECT * FROM game_events WHERE gameId = :gameId ORDER BY timestamp ASC, id ASC")
    suspend fun getByGame(gameId: Long): List<GameEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: GameEventEntity): Long

    @Update
    suspend fun update(event: GameEventEntity)

    @Query("DELETE FROM game_events WHERE id = :eventId")
    suspend fun deleteById(eventId: Long)

    @Query("DELETE FROM game_events WHERE gameId = :gameId")
    suspend fun deleteByGame(gameId: Long)

    @Query("DELETE FROM game_events")
    suspend fun deleteAll()
}
