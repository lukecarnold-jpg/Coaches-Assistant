package com.lukearnold.coachesassistant.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupFormatTest {

    private val data = BackupData(
        players = listOf(
            PlayerEntity(id = 1, name = "Alex \"AJ\" Jones", jerseyNumber = 11, sport = "BASKETBALL", position = null),
            PlayerEntity(id = 2, name = "Sam", jerseyNumber = null, sport = "NETBALL", position = "GS"),
        ),
        games = listOf(
            GameEntity(
                id = 5, title = "Final", opponent = "Rivals", createdAt = 1_000L, teamScore = 4, opponentScore = 2,
                mvpVotesJson = "{\"1\":1}", runningTimeMillis = 30_000L, timerLastStartedAt = null, isFinished = true,
                finishedAt = 9_000L, currentPeriod = 3, periodDurationMillis = 600_000L, notes = "Line 1\nLine 2",
                sport = "ICE_HOCKEY", onCourtSize = 6, periodCount = 3,
            ),
        ),
        gameEvents = listOf(
            GameEventEntity(id = 7, gameId = 5, timestamp = 2_000L, type = "SUBSTITUTION", playerId = 1, relatedPlayerId = null, points = 0, note = "", gameTimeMillis = 12_000L),
        ),
        gamePlayers = listOf(GamePlayerEntity(gameId = 5, playerId = 1, onCourt = true, absent = false)),
    )

    @Test
    fun encodeThenDecode_roundTripsEveryTableIncludingNulls() {
        assertEquals(data, BackupFormat.decode(BackupFormat.encode(data, exportedAt = 123L)))
    }

    @Test
    fun decode_rejectsFilesThatArentBackups() {
        assertRejected("not json at all", "isn't a Coaches Assistant backup")
        assertRejected("""{"some":"other json"}""", "isn't a Coaches Assistant backup")
    }

    @Test
    fun decode_rejectsBackupsFromANewerVersion() {
        val newer = JSONObject(BackupFormat.encode(data, 0L)).put("formatVersion", BackupFormat.FORMAT_VERSION + 1)
        assertRejected(newer.toString(), "newer version")
    }

    @Test
    fun decode_rejectsDamagedBackups() {
        val missingTable = JSONObject(BackupFormat.encode(data, 0L)).apply { remove("games") }
        assertRejected(missingTable.toString(), "damaged")
    }

    @Test
    fun stopRunningClocks_keepsElapsedTimeAndLeavesTheClockStopped() {
        val running = data.games.single().copy(isFinished = false, runningTimeMillis = 60_000L, timerLastStartedAt = 100_000L)
        val stopped = data.games.single()

        val result = stopRunningClocks(listOf(running, stopped), now = 130_000L)

        assertEquals(90_000L, result[0].runningTimeMillis)
        assertEquals(null, result[0].timerLastStartedAt)
        assertSame(stopped, result[1])
    }

    private fun assertRejected(text: String, messagePart: String) {
        try {
            BackupFormat.decode(text)
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains(messagePart))
        }
    }
}
