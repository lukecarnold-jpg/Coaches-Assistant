package com.lonrad.coachesassistant.data

import android.content.ContentResolver
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.room.withTransaction
import com.lonrad.coachesassistant.shared.R
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

    /** Writes the backup to [uri], encrypted with [password] when it isn't blank. */
    suspend fun export(contentResolver: ContentResolver, uri: Uri, password: String? = null): BackupCounts {
        val data = readAll()
        withContext(Dispatchers.IO) {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(BackupFormat.encode(data, exportedAt = System.currentTimeMillis(), password = password))
            } ?: throw UserFacingException(R.string.backup_file_unopenable)
        }
        return BackupCounts(players = data.players.size, games = data.games.size)
    }

    /** The backup file's text, or a [UserFacingException] if it's too large to be a backup. */
    suspend fun readFile(contentResolver: ContentResolver, uri: Uri): String = withContext(Dispatchers.IO) {
        contentResolver.openInputStream(uri)?.use { it.readTextUpTo(MAX_BACKUP_BYTES) }
            ?: throw UserFacingException(R.string.backup_file_unopenable)
    }

    /**
     * Checks the whole backup in [text] before touching the database. An encrypted one needs
     * [password] (see [BackupFormat.decode]).
     */
    suspend fun restore(text: String, password: String? = null): BackupCounts {
        val data = BackupFormat.decode(text, password)
        replaceAll(data)
        return BackupCounts(players = data.players.size, games = data.games.size)
    }

    companion object {
        /** Far more than a season's backup needs; keeps a wrong file from exhausting memory. */
        const val MAX_BACKUP_BYTES = 32 * 1024 * 1024
    }
}
