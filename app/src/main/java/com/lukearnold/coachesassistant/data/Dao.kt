package com.lukearnold.coachesassistant.data

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

    @Query("SELECT * FROM players WHERE sport = :sport ORDER BY jerseyNumber, name COLLATE NOCASE")
    fun observeAllBySport(sport: String): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE sport = :sport ORDER BY jerseyNumber, name COLLATE NOCASE")
    suspend fun getAllBySport(sport: String): List<PlayerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(player: PlayerEntity): Long

    @Update
    suspend fun update(player: PlayerEntity)

    @Query("DELETE FROM players WHERE id = :playerId")
    suspend fun deleteById(playerId: Long)

    @Query("DELETE FROM players")
    suspend fun deleteAll()

    @Query("DELETE FROM players WHERE sport = :sport")
    suspend fun deleteAllBySport(sport: String)
}

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games")
    suspend fun getAll(): List<GameEntity>

    @Query("SELECT * FROM games WHERE sport = :sport ORDER BY createdAt DESC")
    fun observeAllBySport(sport: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE sport = :sport")
    suspend fun getAllBySport(sport: String): List<GameEntity>

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

    @Query("DELETE FROM games WHERE sport = :sport")
    suspend fun deleteAllBySport(sport: String)
}

@Dao
interface GameEventDao {
    @Query("SELECT * FROM game_events WHERE gameId = :gameId ORDER BY timestamp DESC, id DESC")
    fun observeByGame(gameId: Long): Flow<List<GameEventEntity>>

    @Query(
        "SELECT e.* FROM game_events e INNER JOIN games g ON g.id = e.gameId " +
            "WHERE g.sport = :sport AND g.isFinished = 1",
    )
    fun observeForFinishedGames(sport: String): Flow<List<GameEventEntity>>

    @Query("SELECT * FROM game_events WHERE gameId = :gameId ORDER BY timestamp ASC, id ASC")
    suspend fun getByGame(gameId: Long): List<GameEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: GameEventEntity): Long

    @Update
    suspend fun update(event: GameEventEntity)

    @Query("DELETE FROM game_events WHERE id = :eventId")
    suspend fun deleteById(eventId: Long)

    @Query("DELETE FROM game_events WHERE gameId IN (SELECT id FROM games WHERE sport = :sport)")
    suspend fun deleteAllBySport(sport: String)
}

@Dao
interface GamePlayerDao {
    @Query("SELECT * FROM game_players WHERE gameId = :gameId")
    fun observeByGame(gameId: Long): Flow<List<GamePlayerEntity>>

    @Query("SELECT * FROM game_players WHERE gameId = :gameId")
    suspend fun getByGame(gameId: Long): List<GamePlayerEntity>

    @Query(
        "SELECT gp.* FROM game_players gp INNER JOIN games g ON g.id = gp.gameId " +
            "WHERE g.sport = :sport AND g.isFinished = 1",
    )
    fun observeForFinishedGames(sport: String): Flow<List<GamePlayerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<GamePlayerEntity>)

    @Query("DELETE FROM game_players WHERE gameId = :gameId")
    suspend fun deleteByGame(gameId: Long)
}
