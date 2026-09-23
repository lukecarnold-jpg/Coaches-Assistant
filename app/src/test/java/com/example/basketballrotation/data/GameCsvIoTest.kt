package com.example.basketballrotation.data

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/**
 * Exercises [GameCsvIo] end to end: real export/import through a ContentResolver against real
 * (temp) files, backed by a real in-memory Room database. Previously this code had zero test
 * coverage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameCsvIoTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: BasketballRepository
    private lateinit var csvIo: GameCsvIo
    private lateinit var tempDir: File
    private val contentResolver by lazy {
        ApplicationProvider.getApplicationContext<Application>().contentResolver
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = BasketballRepository(database)
        csvIo = GameCsvIo(database)
        tempDir = Files.createTempDirectory("game-csv-io-test").toFile()
    }

    @After
    fun tearDown() {
        database.close()
        tempDir.deleteRecursively()
    }

    private fun fileUri(name: String): Uri = Uri.fromFile(File(tempDir, name))

    @Test
    fun exportThenImportPlayersCsv_roundTripsTheRoster() = runBlocking {
        repository.addPlayer(Sport.BASKETBALL, "Alex", 23)
        repository.addPlayer(Sport.BASKETBALL, "Sam", null)
        val uri = fileUri("roster.csv")

        csvIo.exportPlayersCsv(contentResolver, uri, Sport.BASKETBALL)
        // Imported into a different sport's roster so the result can't be confused with the
        // players already sitting in Sport.BASKETBALL.
        csvIo.importPlayersCsv(contentResolver, uri, Sport.NETBALL)

        val imported = database.playerDao().getAllBySport(Sport.NETBALL.name)
        assertEquals(
            setOf("Alex" to 23, "Sam" to null),
            imported.map { it.name to it.jerseyNumber }.toSet(),
        )
    }

    @Test
    fun importPlayersCsv_survivesAQuotedNameContainingACommaAndSkipsBlankLines() = runBlocking {
        val uri = fileUri("roster.csv")
        File(uri.path!!).writeText(
            """
            id,name,jerseyNumber
            ,"Smith, Alex",7

            ,Sam,
            """.trimIndent(),
        )

        csvIo.importPlayersCsv(contentResolver, uri, Sport.BASKETBALL)

        val imported = database.playerDao().getAllBySport(Sport.BASKETBALL.name)
        assertEquals(
            setOf("Smith, Alex" to 7, "Sam" to null),
            imported.map { it.name to it.jerseyNumber }.toSet(),
        )
    }

    @Test
    fun exportGameCsv_writesOneRowPerEventWithResolvedPlayerNames() = runBlocking {
        val playerId = repository.addPlayer(Sport.BASKETBALL, "Alex", 23)
        val gameId = repository.createGame(sport = Sport.BASKETBALL, title = "Test Game", opponent = "Rivals")
        repository.addScore(gameId, playerId, 2)
        val uri = fileUri("game.csv")

        csvIo.exportGameCsv(contentResolver, gameId, uri)

        val lines = File(uri.path!!).readLines()
        assertEquals(2, lines.size) // header + the one SCORE event
        assertTrue(lines[0].startsWith("gameId,gameTitle,"))
        assertTrue(lines[1].contains("\"Alex\""))
        assertTrue(lines[1].contains("SCORE"))
    }

    @Test
    fun exportGameCsv_writesASingleSummaryRowWhenThereAreNoEvents() = runBlocking {
        val gameId = repository.createGame(sport = Sport.BASKETBALL, title = "Test Game", opponent = "Rivals")
        val uri = fileUri("game.csv")

        csvIo.exportGameCsv(contentResolver, gameId, uri)

        val lines = File(uri.path!!).readLines()
        assertEquals(2, lines.size) // header + one summary-only row
        assertTrue(lines[1].startsWith("${gameId},\"Test Game\",\"Rivals\","))
    }

    @Test
    fun exportGameNotesTxt_includesTheGameSummaryAndTheFullNotesText() = runBlocking {
        val gameId = repository.createGame(sport = Sport.BASKETBALL, title = "Test Game", opponent = "Rivals")
        repository.updateGameNotes(gameId, "Play zone defense in Q4.")
        val uri = fileUri("notes.txt")

        csvIo.exportGameNotesTxt(contentResolver, gameId, uri)

        val text = File(uri.path!!).readText()
        assertTrue(text.contains("Game: Test Game"))
        assertTrue(text.contains("Opponent: Rivals"))
        assertTrue(text.contains("Play zone defense in Q4."))
    }
}
