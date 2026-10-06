package com.lukearnold.coachesassistant.data

import androidx.annotation.StringRes
import com.lukearnold.coachesassistant.R
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
            GameEntity(
                id = 6, title = "Round 1", opponent = "Hawks", createdAt = 2_000L, teamScore = 145, opponentScore = 140,
                sport = "CRICKET", onCourtSize = 11, periodCount = 1, periodDurationMillis = 0L,
                extras = 12, teamWickets = 6, opponentWickets = 9,
            ),
        ),
        gameEvents = listOf(
            GameEventEntity(id = 7, gameId = 5, timestamp = 2_000L, type = "SUBSTITUTION", playerId = 1, relatedPlayerId = null, points = 0, note = "", gameTimeMillis = 12_000L),
        ),
        gamePlayers = listOf(GamePlayerEntity(gameId = 5, playerId = 1, onCourt = true, absent = false)),
        cricketScorecards = listOf(
            CricketScorecardEntity(
                id = 3, gameId = 6, playerId = 2, dismissal = "CAUGHT", runs = 32, ballsFaced = 28, fours = 4, sixes = 1,
                ballsBowled = 22, maidens = 1, runsConceded = 15, wickets = 2, catches = 1, runOuts = 1, stumpings = 0,
            ),
            CricketScorecardEntity(id = 4, gameId = 6, playerId = null, dismissal = "NOT_OUT", runs = 8),
        ),
        cricketFieldPositions = listOf(CricketFieldPositionEntity(gameId = 6, playerId = 2, position = "FIRST_SLIP")),
    )

    @Test
    fun encodeThenDecode_roundTripsEveryTableIncludingNulls() {
        assertEquals(data, BackupFormat.decode(BackupFormat.encode(data, exportedAt = 123L)))
    }

    @Test
    fun decode_readsBackupsFromBeforeCricket() {
        val old = JSONObject(BackupFormat.encode(data, 0L)).apply {
            remove("cricketScorecards")
            remove("cricketFieldPositions")
            getJSONArray("games").getJSONObject(0).apply {
                remove("extras")
                remove("teamWickets")
                remove("opponentWickets")
            }
        }
        val decoded = BackupFormat.decode(old.toString())

        assertTrue(decoded.cricketScorecards.isEmpty() && decoded.cricketFieldPositions.isEmpty())
        assertEquals(data.games.first(), decoded.games.first())
    }

    @Test
    fun decode_rejectsFilesThatArentBackups() {
        assertRejected("not json at all", R.string.backup_not_a_backup)
        assertRejected("""{"some":"other json"}""", R.string.backup_not_a_backup)
    }

    @Test
    fun decode_rejectsBackupsFromANewerVersion() {
        val newer = JSONObject(BackupFormat.encode(data, 0L)).put("formatVersion", BackupFormat.FORMAT_VERSION + 1)
        assertRejected(newer.toString(), R.string.backup_from_newer_app)
    }

    @Test
    fun decode_rejectsDamagedBackups() {
        val missingTable = JSONObject(BackupFormat.encode(data, 0L)).apply { remove("games") }
        assertRejected(missingTable.toString(), R.string.backup_damaged)
    }

    @Test
    fun stopRunningClocks_keepsElapsedTimeAndLeavesTheClockStopped() {
        val running = data.games.first().copy(isFinished = false, runningTimeMillis = 60_000L, timerLastStartedAt = 100_000L)
        val stopped = data.games.first()

        val result = stopRunningClocks(listOf(running, stopped), now = 130_000L)

        assertEquals(90_000L, result[0].runningTimeMillis)
        assertEquals(null, result[0].timerLastStartedAt)
        assertSame(stopped, result[1])
    }

    private fun assertRejected(text: String, @StringRes expectedMessage: Int) {
        try {
            BackupFormat.decode(text)
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatException) {
            assertEquals(expectedMessage, e.messageRes)
        }
    }
}
