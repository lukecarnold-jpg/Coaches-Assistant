package com.example.basketballrotation.data

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BasketballRepository(
    private val database: AppDatabase
) {
    private val playerDao = database.playerDao()
    private val gameDao = database.gameDao()
    private val gameEventDao = database.gameEventDao()

    val players: Flow<List<PlayerEntity>> = playerDao.observeAll()
    val games: Flow<List<GameEntity>> = gameDao.observeAll()

    fun observeEvents(gameId: Long): Flow<List<GameEventEntity>> =
        gameEventDao.observeByGame(gameId)

    suspend fun addPlayer(name: String, jerseyNumber: Int): Long {
        return playerDao.insert(
            PlayerEntity(
                name = name.trim(),
                jerseyNumber = jerseyNumber
            )
        )
    }

    suspend fun updatePlayer(player: PlayerEntity) {
        playerDao.update(player)
    }

    suspend fun deletePlayer(playerId: Long) {
        playerDao.deleteById(playerId)
    }

    suspend fun createGame(title: String, opponent: String): Long {
        return gameDao.insert(
            GameEntity(
                title = title.trim(),
                opponent = opponent.trim(),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun selectStartingLineup(gameId: Long, playerIds: List<Long>) {
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction
            val normalizedIds = playerIds.distinct().take(5)

            gameDao.update(game.copy(onCourtCsv = idsToCsv(normalizedIds)))
            gameEventDao.insert(
                GameEventEntity(
                    gameId = gameId,
                    timestamp = System.currentTimeMillis(),
                    type = EventType.START_LINEUP.name,
                    note = idsToCsv(normalizedIds),
                    gameTimeMillis = calculateCurrentGameTime(game)
                )
            )
        }
    }

    suspend fun substitute(gameId: Long, playerInId: Long, playerOutId: Long) {
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction
            val onCourt = game.onCourtIds().toMutableList()

            if (!onCourt.contains(playerOutId) || onCourt.contains(playerInId)) {
                return@withTransaction
            }

            val updated = onCourt.map { currentId ->
                if (currentId == playerOutId) playerInId else currentId
            }

            gameDao.update(game.copy(onCourtCsv = idsToCsv(updated)))
            gameEventDao.insert(
                GameEventEntity(
                    gameId = gameId,
                    timestamp = System.currentTimeMillis(),
                    type = EventType.SUBSTITUTION.name,
                    playerId = playerInId,
                    relatedPlayerId = playerOutId,
                    gameTimeMillis = calculateCurrentGameTime(game)
                )
            )
        }
    }

    suspend fun addScore(gameId: Long, playerId: Long?, points: Int) {
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction

            if (playerId == null) {
                // Adjusting opponent score
                gameDao.update(game.copy(opponentScore = (game.opponentScore + points).coerceAtLeast(0)))
            } else {
                gameDao.update(game.copy(teamScore = (game.teamScore + points).coerceAtLeast(0)))
                gameEventDao.insert(
                    GameEventEntity(
                        gameId = gameId,
                        timestamp = System.currentTimeMillis(),
                        type = EventType.SCORE.name,
                        playerId = playerId,
                        points = points,
                        gameTimeMillis = calculateCurrentGameTime(game)
                    )
                )
            }
        }
    }

    suspend fun addFoul(gameId: Long, playerId: Long, adjustment: Int = 1) {
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction
            gameEventDao.insert(
                GameEventEntity(
                    gameId = gameId,
                    timestamp = System.currentTimeMillis(),
                    type = EventType.FOUL.name,
                    playerId = playerId,
                    points = adjustment,
                    gameTimeMillis = calculateCurrentGameTime(game)
                )
            )
        }
    }

    suspend fun toggleTimer(gameId: Long) {
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction
            val now = System.currentTimeMillis()

            if (game.timerLastStartedAt == null) {
                // Start
                gameDao.update(game.copy(timerLastStartedAt = now))
            } else {
                // Stop
                val elapsed = now - game.timerLastStartedAt
                gameDao.update(
                    game.copy(
                        runningTimeMillis = game.runningTimeMillis + elapsed,
                        timerLastStartedAt = null
                    )
                )
            }
        }
    }

    suspend fun setGameTime(gameId: Long, timeMillis: Long) {
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction
            gameDao.update(game.copy(runningTimeMillis = timeMillis, timerLastStartedAt = null))
        }
    }

    suspend fun clearAllData() {
        database.withTransaction {
            gameEventDao.deleteAll()
            gameDao.deleteAll()
            playerDao.deleteAll()
        }
    }

    suspend fun resetTimer(gameId: Long) {
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction
            gameDao.update(game.copy(runningTimeMillis = 0L, timerLastStartedAt = null))
        }
    }

    suspend fun addMvpVote(gameId: Long, playerId: Long) {
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction
            val currentVotes = try {
                val map = mutableMapOf<Long, Int>()
                val json = game.mvpVotesJson.removeSurrounding("{", "}")
                if (json.isNotEmpty()) {
                    json.split(",").forEach { pair ->
                        val parts = pair.split(":")
                        if (parts.size == 2) {
                            map[parts[0].trim().toLong()] = parts[1].trim().toInt()
                        }
                    }
                }
                map
            } catch (e: Exception) {
                mutableMapOf<Long, Int>()
            }

            currentVotes[playerId] = (currentVotes[playerId] ?: 0) + 1
            
            val newJson = "{" + currentVotes.entries.joinToString(",") { "${it.key}:${it.value}" } + "}"
            gameDao.update(game.copy(mvpVotesJson = newJson))

            gameEventDao.insert(
                GameEventEntity(
                    gameId = gameId,
                    timestamp = System.currentTimeMillis(),
                    type = EventType.MVP_VOTE.name,
                    playerId = playerId,
                    gameTimeMillis = calculateCurrentGameTime(game)
                )
            )
        }
    }

    private fun calculateCurrentGameTime(game: GameEntity): Long {
        val base = game.runningTimeMillis
        val start = game.timerLastStartedAt ?: return base
        return base + (System.currentTimeMillis() - start)
    }

    suspend fun exportPlayersCsv(contentResolver: ContentResolver, uri: Uri) {
        withContext(Dispatchers.IO) {
            val players = playerDao.getAll()

            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.appendLine("id,name,jerseyNumber")

                players.forEach { player ->
                    writer.appendLine(
                        listOf(
                            player.id.toString(),
                            escapeCsv(player.name),
                            player.jerseyNumber.toString()
                        ).joinToString(",")
                    )
                }
            } ?: error("Unable to open export file")
        }
    }

    suspend fun exportGameCsv(contentResolver: ContentResolver, gameId: Long, uri: Uri) {
        withContext(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: error("Game not found")
            val players = playerDao.getAll().associateBy { it.id }
            val events = gameEventDao.getByGame(gameId)

            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.appendLine(
                    "gameId,gameTitle,opponent,createdAt,teamScoreAtExport,eventId,eventTimestamp,eventGameTime,eventType,playerName,playerNumber,relatedPlayerName,relatedPlayerNumber,points,note"
                )

                if (events.isEmpty()) {
                    writer.appendLine(
                        listOf(
                            game.id.toString(),
                            escapeCsv(game.title),
                            escapeCsv(game.opponent),
                            escapeCsv(formatDate(game.createdAt)),
                            game.teamScore.toString(),
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            ""
                        ).joinToString(",")
                    )
                } else {
                    events.forEach { event ->
                        val player = event.playerId?.let(players::get)
                        val relatedPlayer = event.relatedPlayerId?.let(players::get)
                        val note = when (EventType.fromRaw(event.type)) {
                            EventType.START_LINEUP -> lineupNoteToNames(event.note, players)
                            else -> event.note
                        }

                        writer.appendLine(
                            listOf(
                                game.id.toString(),
                                escapeCsv(game.title),
                                escapeCsv(game.opponent),
                                escapeCsv(formatDate(game.createdAt)),
                                game.teamScore.toString(),
                                event.id.toString(),
                                escapeCsv(formatDate(event.timestamp)),
                                formatDuration(event.gameTimeMillis),
                                escapeCsv(event.type),
                                escapeCsv(player?.name.orEmpty()),
                                player?.jerseyNumber?.toString().orEmpty(),
                                escapeCsv(relatedPlayer?.name.orEmpty()),
                                relatedPlayer?.jerseyNumber?.toString().orEmpty(),
                                if (event.points == 0 && EventType.fromRaw(event.type) != EventType.FOUL) "" else event.points.toString(),
                                escapeCsv(note)
                            ).joinToString(",")
                        )
                    }
                }
            } ?: error("Unable to open export file")
        }
    }

    private fun lineupNoteToNames(note: String, players: Map<Long, PlayerEntity>): String {
        val names = note.split(',')
            .mapNotNull { raw -> raw.trim().toLongOrNull() }
            .mapNotNull(players::get)
            .map(PlayerEntity::displayLabel)

        return names.joinToString(" | ")
    }

    private fun formatDate(timeMillis: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return formatter.format(Date(timeMillis))
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}