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
    fun migrate5To11_preservesExistingRowsAndBackfillsNewColumns() {
        val dbName = "migration-test-5-to-11"

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

        val migrated = helper.runMigrationsAndValidate(dbName, 11, true)

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

    @Test
    fun migrate10To11_preservesDataAndAddsGameEventsForeignKeys() {
        val dbName = "migration-test-10-to-11"

        helper.createDatabase(dbName, 10).apply {
            execSQL(
                "INSERT INTO players (id, name, jerseyNumber, sport, position) VALUES (1, 'Sam', 7, 'BASKETBALL', NULL)",
            )
            execSQL(
                """
                INSERT INTO games (
                    id, title, opponent, createdAt, teamScore, opponentScore, mvpVotesJson,
                    onCourtCsv, runningTimeMillis, timerLastStartedAt, isFinished, finishedAt,
                    useQuarters, currentPeriod, periodDurationMillis, absentPlayerIdsCsv, notes,
                    sport, onCourtSize
                ) VALUES (
                    1, 'Championship', 'Rivals', 3000, 2, 0, '{}',
                    '1', 0, NULL, 0, NULL,
                    1, 1, 720000, '', '', 'BASKETBALL', 5
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO game_events (
                    id, gameId, timestamp, type, playerId, relatedPlayerId, points, note, gameTimeMillis
                ) VALUES (
                    1, 1, 3000, 'SCORE', 1, NULL, 2, '', 15000
                )
                """.trimIndent(),
            )
            close()
        }

        // runMigrationsAndValidate compares the migrated table's real schema (including its
        // foreign key definitions) against app/schemas' 11.json, so this alone proves the
        // gameId/playerId/relatedPlayerId foreign keys were added as declared.
        val migrated = helper.runMigrationsAndValidate(dbName, 11, true)

        migrated.query("SELECT gameId, playerId, points FROM game_events WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(2, cursor.getInt(2))
        }
        // The connection here is the raw migrated database, without Room's `PRAGMA
        // foreign_keys=ON` (that's only enabled once a RoomDatabase opens the file normally), so
        // this doesn't re-prove the constraints are *enforced* — BasketballRepositoryTest's
        // deleteGame/deletePlayer tests already do that against a normally-opened database.
    }

    @Test
    fun migrate11To12_replacesUseQuartersWithPeriodCountAndKeepsEvents() {
        val dbName = "migration-test-11-to-12"

        helper.createDatabase(dbName, 11).apply {
            execSQL(
                "INSERT INTO players (id, name, jerseyNumber, sport, position) VALUES (1, 'Sam', 7, 'BASKETBALL', NULL)",
            )
            listOf(1 to 1, 2 to 0).forEach { (id, useQuarters) ->
                execSQL(
                    """
                    INSERT INTO games (
                        id, title, opponent, createdAt, teamScore, opponentScore, mvpVotesJson,
                        onCourtCsv, runningTimeMillis, timerLastStartedAt, isFinished, finishedAt,
                        useQuarters, currentPeriod, periodDurationMillis, absentPlayerIdsCsv, notes,
                        sport, onCourtSize
                    ) VALUES (
                        $id, 'Game $id', 'Rivals', 3000, 2, 0, '{}',
                        '1', 0, NULL, 0, NULL,
                        $useQuarters, 1, 720000, '', 'Keep me', 'BASKETBALL', 5
                    )
                    """.trimIndent(),
                )
            }
            execSQL(
                """
                INSERT INTO game_events (
                    id, gameId, timestamp, type, playerId, relatedPlayerId, points, note, gameTimeMillis
                ) VALUES (
                    1, 1, 3000, 'SCORE', 1, NULL, 2, '', 15000
                )
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 12, true, MIGRATION_11_12)

        migrated.query("SELECT id, periodCount, notes, teamScore FROM games ORDER BY id").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(4, cursor.getInt(1)) // useQuarters = 1
            assertEquals("Keep me", cursor.getString(2))
            assertEquals(2, cursor.getInt(3))
            assertTrue(cursor.moveToNext())
            assertEquals(2, cursor.getInt(0))
            assertEquals(2, cursor.getInt(1)) // useQuarters = 0
        }

        migrated.query("SELECT gameId, points FROM game_events WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(2, cursor.getInt(1))
        }
    }
}
