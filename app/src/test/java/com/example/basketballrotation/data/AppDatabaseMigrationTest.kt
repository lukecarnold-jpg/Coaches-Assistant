package com.example.basketballrotation.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs [AppDatabase]'s real auto-migrations against real (seeded) SQLite databases, using the
 * exported schema JSON under app/schemas (exposed to this test as an asset directory in
 * app/build.gradle.kts) to validate the resulting schema at each step. This is what actually
 * proves the migration chain works end to end, as opposed to
 * [BasketballRepositoryTest]'s in-memory database, which is always created fresh at the latest
 * version and never exercises a migration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate5To10_preservesExistingRowsAndBackfillsNewColumns() {
        val dbName = "migration-test-5-to-10"

        helper.createDatabase(dbName, 5).apply {
            execSQL("INSERT INTO players (id, name, jerseyNumber) VALUES (1, 'Alex', 23)")
            execSQL(
                """
                INSERT INTO games (
                    id, title, opponent, createdAt, teamScore, opponentScore, mvpVotesJson,
                    onCourtCsv, runningTimeMillis, timerLastStartedAt, isFinished, useQuarters,
                    currentPeriod, periodDurationMillis
                ) VALUES (
                    1, 'Season Opener', 'Rivals', 1000, 10, 8, '{}',
                    '1', 60000, NULL, 0, 0,
                    1, 600000
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO game_events (
                    id, gameId, timestamp, type, playerId, relatedPlayerId, points, note, gameTimeMillis
                ) VALUES (
                    1, 1, 1000, 'SCORE', 1, NULL, 2, '', 30000
                )
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 10, true)

        migrated.query("SELECT name, jerseyNumber, sport, position FROM players WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Alex", cursor.getString(0))
            assertEquals(23, cursor.getInt(1))
            assertEquals("BASKETBALL", cursor.getString(2))
            assertTrue(cursor.isNull(3))
        }

        migrated.query(
            "SELECT title, teamScore, absentPlayerIdsCsv, notes, finishedAt, sport, onCourtSize FROM games WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Season Opener", cursor.getString(0))
            assertEquals(10, cursor.getInt(1))
            assertEquals("", cursor.getString(2))
            assertEquals("", cursor.getString(3))
            assertTrue(cursor.isNull(4))
            assertEquals("BASKETBALL", cursor.getString(5))
            assertEquals(5, cursor.getInt(6))
        }

        migrated.query("SELECT gameId, type, points FROM game_events WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals("SCORE", cursor.getString(1))
            assertEquals(2, cursor.getInt(2))
        }
    }

    @Test
    fun migrate9To10_backfillsSportAndOnCourtSizeDefaults() {
        val dbName = "migration-test-9-to-10"

        helper.createDatabase(dbName, 9).apply {
            execSQL("INSERT INTO players (id, name, jerseyNumber) VALUES (1, 'Sam', NULL)")
            execSQL(
                """
                INSERT INTO games (
                    id, title, opponent, createdAt, teamScore, opponentScore, mvpVotesJson,
                    onCourtCsv, runningTimeMillis, timerLastStartedAt, isFinished, finishedAt,
                    useQuarters, currentPeriod, periodDurationMillis, absentPlayerIdsCsv, notes
                ) VALUES (
                    1, 'Playoff Game', 'Rivals', 2000, 0, 0, '{}',
                    '', 0, NULL, 0, NULL,
                    1, 1, 720000, '', ''
                )
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 10, true)

        migrated.query("SELECT sport, position FROM players WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("BASKETBALL", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }

        migrated.query("SELECT sport, onCourtSize FROM games WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("BASKETBALL", cursor.getString(0))
            assertEquals(5, cursor.getInt(1))
        }
    }
}
