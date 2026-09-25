package com.lukearnold.coachesassistant.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupIoTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var backupIo: BackupIo

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = GameRepository(database)
        backupIo = BackupIo(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** Two sports, a game with a lineup, attendance, scores, a sub, and a finished game. */
    private suspend fun seed(): List<Long> {
        val hoops = (1..6).map { repository.addPlayer(Sport.BASKETBALL, "Player $it", it) }
        val netball = repository.addPlayer(Sport.NETBALL, "Shooter", 9, position = "GS")
        val game = repository.createGame(Sport.BASKETBALL, "Live", "Rivals", periodCount = 4)
        repository.selectStartingLineup(game, hoops.take(5), maxSize = 5)
        repository.updateAbsentPlayers(game, listOf(hoops[5]))
        repository.addTeamScore(game, hoops[0], 2)
        repository.addOpponentScore(game, 3)
        repository.addMvpVote(game, hoops[1])
        val finished = repository.createGame(Sport.NETBALL, "Done", "Others", onCourtSize = 7)
        repository.addTeamScore(finished, netball, 1)
        repository.endGame(finished)
        return hoops + netball
    }

    @Test
    fun backupThenRestore_reproducesEveryTableExactly() = runBlocking {
        seed()
        val before = backupIo.readAll()
        val file = BackupFormat.encode(before, exportedAt = 0L)

        backupIo.replaceAll(BackupData(emptyList(), emptyList(), emptyList(), emptyList()))
        assertTrue(database.playerDao().getAll().isEmpty())

        backupIo.replaceAll(BackupFormat.decode(file))

        assertEquals(before, backupIo.readAll())
    }

    @Test
    fun restore_replacesWhateverWasThereBefore() = runBlocking {
        seed()
        val backup = backupIo.readAll()
        repository.addPlayer(Sport.SOCCER, "Added after the backup", 99)
        repository.createGame(Sport.SOCCER, "Also after", "Rivals")

        backupIo.replaceAll(backup)

        assertEquals(backup, backupIo.readAll())
        assertTrue(database.playerDao().getAll().none { it.sport == Sport.SOCCER.name })
    }

    @Test
    fun restore_ofADamagedBackup_changesNothing() = runBlocking {
        seed()
        val current = backupIo.readAll()
        val damaged = current.copy(gameEvents = current.gameEvents + current.gameEvents.first().copy(id = 999, gameId = 12345))

        try {
            backupIo.replaceAll(damaged)
            fail("Expected BackupFormatException")
        } catch (_: BackupFormatException) {
        }

        assertEquals(current, backupIo.readAll())
    }

    @Test
    fun ids_continueAfterRestoredRows() = runBlocking {
        val ids = seed()
        backupIo.replaceAll(BackupFormat.decode(BackupFormat.encode(backupIo.readAll(), 0L)))

        val next = repository.addPlayer(Sport.BASKETBALL, "New", 50)

        assertTrue(next > ids.max())
    }
}
