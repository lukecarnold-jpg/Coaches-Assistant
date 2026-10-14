package com.lukearnold.coachesassistant.data

import android.content.ContentResolver
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.room.withTransaction
import com.lukearnold.coachesassistant.shared.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How much a backup or restore covered, for the confirmation message. */
data class BackupCounts(val players: Int, val games: Int)

/**
 * Whole-app backup and restore (every sport's players, games, events, and lineups) to and from a
 * JSON file the user picks. See [BackupFormat] for the file format. API keys and settings aren't
 * included: keys are secrets encrypted with a key that can't leave the device.
 */
class BackupIo(private val database: AppDatabase) {
    private val playerDao = database.playerDao()
    private val gameDao = database.gameDao()
    private val gameEventDao = database.gameEventDao()
    private val gamePlayerDao = database.gamePlayerDao()
    private val cricketDao = database.cricketDao()

    suspend fun readAll(): BackupData = database.withTransaction {
        BackupData(
            players = playerDao.getAll(),
            games = stopRunningClocks(gameDao.getAll(), System.currentTimeMillis()),
            gameEvents = gameEventDao.getAll(),
            gamePlayers = gamePlayerDao.getAll(),
            cricketScorecards = cricketDao.getAllScorecards(),
            cricketFieldPositions = cricketDao.getAllFieldPositions(),
        )
    }

    /**
     * Replaces every table with [data] in one transaction. If anything in it doesn't fit (e.g. an
     * event pointing at a game that isn't there), nothing changes and this throws
     * [BackupFormatException].
     */
    suspend fun replaceAll(data: BackupData) {
        try {
            database.withTransaction {
                gameDao.deleteAll() // game_events, game_players, and the cricket tables cascade
                playerDao.deleteAll()
                playerDao.insertAll(data.players)
                gameDao.insertAll(data.games)
                gameEventDao.insertAll(data.gameEvents)
                gamePlayerDao.insertAll(data.gamePlayers)
                cricketDao.insertAllScorecards(data.cricketScorecards)
                cricketDao.insertFieldPositions(data.cricketFieldPositions)
            }
        } catch (_: SQLiteConstraintException) {
            throw BackupFormatException(R.string.backup_damaged_nothing_changed)
        }
    }

    suspend fun export(contentResolver: ContentResolver, uri: Uri): BackupCounts {
        val data = readAll()
        withContext(Dispatchers.IO) {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(BackupFormat.encode(data, exportedAt = System.currentTimeMillis()))
            } ?: throw UserFacingException(R.string.backup_file_unopenable)
        }
        return BackupCounts(players = data.players.size, games = data.games.size)
    }

    /** Reads and checks the whole file before touching the database. */
    suspend fun restore(contentResolver: ContentResolver, uri: Uri): BackupCounts {
        val text = withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw UserFacingException(R.string.backup_file_unopenable)
        }
        val data = BackupFormat.decode(text)
        replaceAll(data)
        return BackupCounts(players = data.players.size, games = data.games.size)
    }
}
