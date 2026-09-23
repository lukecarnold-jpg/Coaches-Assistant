package com.example.basketballrotation.data

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CSV/TXT import and export for rosters, games, and coaching notes. Split out of
 * [BasketballRepository] so that class stays focused on transactional game-state changes, and so
 * the pure formatting/parsing helpers below can be unit tested without a database.
 */
class GameCsvIo(private val database: AppDatabase) {
    private val playerDao = database.playerDao()
    private val gameDao = database.gameDao()
    private val gameEventDao = database.gameEventDao()

    suspend fun exportPlayersCsv(contentResolver: ContentResolver, uri: Uri, sport: Sport) {
        withContext(Dispatchers.IO) {
            val players = playerDao.getAllBySport(sport.name)

            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.appendLine("id,name,jerseyNumber")

                players.forEach { player ->
                    writer.appendLine(
                        listOf(
                            player.id.toString(),
                            escapeCsv(player.name),
                            player.jerseyNumber?.toString().orEmpty(),
                        ).joinToString(","),
                    )
                }
            } ?: error("Unable to open export file")
        }
    }

    suspend fun importPlayersCsv(contentResolver: ContentResolver, uri: Uri, sport: Sport) {
        withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val lines = reader.readLines()
                if (lines.isEmpty()) return@use

                // Skip header if present
                val startIdx = if (lines.first().contains("name", ignoreCase = true)) 1 else 0

                database.withTransaction {
                    for (i in startIdx until lines.size) {
                        val line = lines[i]
                        if (line.isBlank()) continue

                        val parts = parseCsvLine(line)
                        if (parts.size >= 2) {
                            // Format: id, name, jerseyNumber (id is ignored for new inserts)
                            val name = parts.getOrNull(1)?.trim()?.removeSurrounding("\"") ?: ""
                            val jersey = parts.getOrNull(2)?.trim()?.removeSurrounding("\"")?.toIntOrNull()

                            if (name.isNotBlank()) {
                                playerDao.insert(
                                    PlayerEntity(
                                        name = name,
                                        jerseyNumber = jersey,
                                        sport = sport.name,
                                    ),
                                )
                            }
                        }
                    }
                }
            } ?: error("Unable to open import file")
        }
    }

    suspend fun exportGameCsv(contentResolver: ContentResolver, gameId: Long, uri: Uri) {
        withContext(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: error("Game not found")
            val players = playerDao.getAll().associateBy { it.id }
            val events = gameEventDao.getByGame(gameId)

            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.appendLine(
                    "gameId,gameTitle,opponent,createdAt,teamScoreAtExport,gameNotes,eventId,eventTimestamp,eventGameTime,eventType,playerName,playerNumber,relatedPlayerName,relatedPlayerNumber,points,note",
                )

                if (events.isEmpty()) {
                    writer.appendLine(
                        listOf(
                            game.id.toString(),
                            escapeCsv(game.title),
                            escapeCsv(game.opponent),
                            escapeCsv(formatDate(game.createdAt)),
                            game.teamScore.toString(),
                            escapeCsv(game.notes),
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                        ).joinToString(",")
                    )
                } else {
                    events.forEach { event ->
                        val player = event.playerId?.let(players::get)
                        val relatedPlayer = event.relatedPlayerId?.let(players::get)
                        val eventNote = when (EventType.fromRaw(event.type)) {
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
                                escapeCsv(game.notes),
                                event.id.toString(),
                                escapeCsv(formatDate(event.timestamp)),
                                formatDuration(event.gameTimeMillis),
                                escapeCsv(event.type),
                                escapeCsv(player?.name.orEmpty()),
                                player?.jerseyNumber?.toString().orEmpty(),
                                escapeCsv(relatedPlayer?.name.orEmpty()),
                                relatedPlayer?.jerseyNumber?.toString().orEmpty(),
                                if ((event.points == 0) && (EventType.fromRaw(event.type) != EventType.FOUL)) "" else event.points.toString(),
                                escapeCsv(eventNote)
                            ).joinToString(",")
                        )
                    }
                }
            } ?: error("Unable to open export file")
        }
    }

    suspend fun exportGameNotesTxt(contentResolver: ContentResolver, gameId: Long, uri: Uri) {
        withContext(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: error("Game not found")
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write("Game: ${game.title}\n")
                writer.write("Opponent: ${game.opponent}\n")
                writer.write("Date: ${formatDate(game.createdAt)}\n")
                writer.write("Score: Us ${game.teamScore} - ${game.opponentScore} ${game.opponent}\n")
                writer.write("\n--- COACHING NOTES ---\n\n")
                writer.write(game.notes)
            } ?: error("Unable to open export file")
        }
    }
}

internal fun lineupNoteToNames(note: String, players: Map<Long, PlayerEntity>): String {
    val names = note.split(',')
        .asSequence()
        .mapNotNull { raw -> raw.trim().toLongOrNull() }
        .mapNotNull(players::get)
        .map(PlayerEntity::displayLabel)

    return names.joinToString(" | ")
}

internal fun formatDate(timeMillis: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timeMillis))
}

internal fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

internal fun escapeCsv(value: String): String {
    val escaped = value.replace("\"", "\"\"")
    return "\"$escaped\""
}

internal fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    var current = StringBuilder()
    var inQuotes = false

    var index = 0
    while (index < line.length) {
        val char = line[index]
        when {
            (char == '"') && inQuotes && (index + 1 < line.length) && (line[index + 1] == '"') -> {
                current.append('"')
                index++ // Skip the escaped quote partner.
            }

            char == '"' -> inQuotes = !inQuotes

            (char == ',') && !inQuotes -> {
                result.add(current.toString())
                current = StringBuilder()
            }

            else -> current.append(char)
        }
        index++
    }

    result.add(current.toString())
    return result
}
