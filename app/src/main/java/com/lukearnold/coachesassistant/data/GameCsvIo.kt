package com.lukearnold.coachesassistant.data

import android.content.ContentResolver
import android.content.res.Resources
import android.net.Uri
import androidx.room.withTransaction
import com.lukearnold.coachesassistant.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How a roster CSV import went: players added, and players skipped as already on the roster. */
data class RosterImportResult(val added: Int, val skipped: Int)

/**
 * CSV/TXT import and export for rosters, games, and coaching notes. Split out of
 * [GameRepository] so that class stays focused on transactional game-state changes, and so
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
                writer.appendLine("id,name,jerseyNumber,position")

                players.forEach { player ->
                    writer.appendLine(
                        listOf(
                            player.id.toString(),
                            escapeCsv(player.name),
                            player.jerseyNumber?.toString().orEmpty(),
                            escapeCsv(player.position.orEmpty()),
                        ).joinToString(","),
                    )
                }
            } ?: throw UserFacingException(R.string.export_file_unopenable)
        }
    }

    /**
     * Adds the players in a roster CSV (`id,name,jerseyNumber[,position]`; the id is ignored) to
     * [sport]'s roster. Players already on the roster (same name, ignoring case, and number) are
     * skipped, as are repeats within the file, so re-importing a file doesn't duplicate anyone. A
     * position is kept only if it's one of [allowedPositions] (the sport's squad positions), since a
     * CSV may come from another sport. Files from before the position column still import.
     */
    suspend fun importPlayersCsv(
        contentResolver: ContentResolver,
        uri: Uri,
        sport: Sport,
        allowedPositions: Collection<String> = emptyList(),
    ): RosterImportResult = withContext(Dispatchers.IO) {
        val lines = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLines() }
            ?: throw UserFacingException(R.string.import_file_unopenable)
        // Skip header if present
        val rows = lines.drop(if (lines.firstOrNull()?.contains("name", ignoreCase = true) == true) 1 else 0)

        database.withTransaction {
            val onRoster = playerDao.getAllBySport(sport.name).map { rosterKey(it.name, it.jerseyNumber) }.toMutableSet()
            var added = 0
            var skipped = 0
            for (line in rows) {
                if (line.isBlank()) continue
                val parts = parseCsvLine(line).map { it.trim().removeSurrounding("\"") }
                val name = parts.getOrNull(1).orEmpty()
                if (name.isBlank()) continue
                val jersey = parts.getOrNull(2)?.toIntOrNull()
                if (!onRoster.add(rosterKey(name, jersey))) {
                    skipped++
                    continue
                }
                playerDao.insert(
                    PlayerEntity(
                        name = name,
                        jerseyNumber = jersey,
                        sport = sport.name,
                        position = parts.getOrNull(3)?.takeIf { it in allowedPositions },
                    ),
                )
                added++
            }
            RosterImportResult(added = added, skipped = skipped)
        }
    }

    private fun rosterKey(name: String, jerseyNumber: Int?) = name.trim().lowercase() to jerseyNumber

    suspend fun exportGameCsv(contentResolver: ContentResolver, gameId: Long, uri: Uri) {
        withContext(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: throw UserFacingException(R.string.game_not_found)
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
                        ).joinToString(","),
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
                                escapeCsv(eventNote),
                            ).joinToString(","),
                        )
                    }
                }
            } ?: throw UserFacingException(R.string.export_file_unopenable)
        }
    }

    /** Writes the game's coaching notes under a short summary, labelled in the app's language from [resources]. */
    suspend fun exportGameNotesTxt(contentResolver: ContentResolver, resources: Resources, gameId: Long, uri: Uri) {
        withContext(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: throw UserFacingException(R.string.game_not_found)
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(resources.getString(R.string.notes_file_game, game.title) + "\n")
                writer.write(resources.getString(R.string.notes_file_opponent, game.opponent) + "\n")
                writer.write(resources.getString(R.string.notes_file_date, formatDate(game.createdAt)) + "\n")
                writer.write(resources.getString(R.string.notes_file_score, game.teamScore, game.opponentScore, game.opponent) + "\n")
                writer.write("\n" + resources.getString(R.string.notes_file_heading) + "\n\n")
                writer.write(game.notes)
            } ?: throw UserFacingException(R.string.export_file_unopenable)
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
