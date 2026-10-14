package com.lonrad.coachesassistant.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    // By number, then name; players without a number come last (SQLite would otherwise put them first).
    @Query("SELECT * FROM players ORDER BY jerseyNumber IS NULL, jerseyNumber, name COLLATE NOCASE")
    fun observeAll(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players ORDER BY jerseyNumber IS NULL, jerseyNumber, name COLLATE NOCASE")
    suspend fun getAll(): List<PlayerEntity>

    @Query("SELECT * FROM players WHERE sport = :sport ORDER BY jerseyNumber IS NULL, jerseyNumber, name COLLATE NOCASE")
    fun observeAllBySport(sport: String): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE sport = :sport ORDER BY jerseyNumber IS NULL, jerseyNumber, name COLLATE NOCASE")
    suspend fun getAllBySport(sport: String): List<PlayerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(player: PlayerEntity): Long

    /** For restoring a backup: keeps each row's id, and fails (rather than overwriting) on a clash. */
    @Insert
    suspend fun insertAll(players: List<PlayerEntity>)

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

    /** For restoring a backup: keeps each row's id, and fails (rather than overwriting) on a clash. */
    @Insert
    suspend fun insertAll(games: List<GameEntity>)

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

    @Query("SELECT * FROM game_events WHERE id = :eventId")
    suspend fun getById(eventId: Long): GameEventEntity?

    @Query("SELECT * FROM game_events WHERE gameId = :gameId ORDER BY timestamp ASC, id ASC")
    suspend fun getByGame(gameId: Long): List<GameEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: GameEventEntity): Long

    /** For restoring a backup: keeps each row's id, and fails (rather than overwriting) on a clash. */
    @Insert
    suspend fun insertAll(events: List<GameEventEntity>)

    @Query("SELECT * FROM game_events")
    suspend fun getAll(): List<GameEventEntity>

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

    @Query("SELECT * FROM game_players")
    suspend fun getAll(): List<GamePlayerEntity>

    @Query("DELETE FROM game_players WHERE gameId = :gameId")
    suspend fun deleteByGame(gameId: Long)
}

@Dao
interface CricketDao {
    @Query("SELECT * FROM cricket_scorecards WHERE gameId = :gameId")
    fun observeScorecards(gameId: Long): Flow<List<CricketScorecardEntity>>

    @Query("SELECT * FROM cricket_scorecards WHERE gameId = :gameId")
    suspend fun getScorecards(gameId: Long): List<CricketScorecardEntity>

    @Query(
        "SELECT c.* FROM cricket_scorecards c INNER JOIN games g ON g.id = c.gameId " +
            "WHERE g.sport = :sport AND g.isFinished = 1",
    )
    fun observeFinishedScorecards(sport: String): Flow<List<CricketScorecardEntity>>

    @Query("SELECT * FROM cricket_scorecards WHERE gameId = :gameId AND playerId = :playerId LIMIT 1")
    suspend fun getScorecard(gameId: Long, playerId: Long): CricketScorecardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScorecard(card: CricketScorecardEntity): Long

    @Query("DELETE FROM cricket_scorecards WHERE id = :id")
    suspend fun deleteScorecard(id: Long)

    @Query("SELECT * FROM cricket_scorecards")
    suspend fun getAllScorecards(): List<CricketScorecardEntity>

    /** For restoring a backup: keeps each row's id, and fails (rather than overwriting) on a clash. */
    @Insert
    suspend fun insertAllScorecards(cards: List<CricketScorecardEntity>)

    @Query("DELETE FROM cricket_scorecards WHERE gameId IN (SELECT id FROM games WHERE sport = :sport)")
    suspend fun deleteScorecardsBySport(sport: String)

    @Query("SELECT * FROM cricket_field_positions WHERE gameId = :gameId")
    fun observeField(gameId: Long): Flow<List<CricketFieldPositionEntity>>

    @Query("SELECT * FROM cricket_field_positions WHERE gameId = :gameId")
    suspend fun getField(gameId: Long): List<CricketFieldPositionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFieldPositions(rows: List<CricketFieldPositionEntity>)

    @Query("DELETE FROM cricket_field_positions WHERE gameId = :gameId AND (playerId = :playerId OR position = :position)")
    suspend fun clearFieldSpot(gameId: Long, playerId: Long, position: String)

    @Query("DELETE FROM cricket_field_positions WHERE gameId = :gameId AND position = :position")
    suspend fun clearFieldPosition(gameId: Long, position: String)

    @Query("DELETE FROM cricket_field_positions WHERE gameId = :gameId")
    suspend fun clearField(gameId: Long)

    @Query("SELECT * FROM cricket_field_positions")
    suspend fun getAllFieldPositions(): List<CricketFieldPositionEntity>

    @Query("DELETE FROM cricket_field_positions WHERE gameId IN (SELECT id FROM games WHERE sport = :sport)")
    suspend fun deleteFieldPositionsBySport(sport: String)
}
