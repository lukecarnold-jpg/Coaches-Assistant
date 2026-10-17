package com.lonrad.coachesassistant.data

import androidx.annotation.StringRes
import com.lonrad.coachesassistant.shared.R
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val newer = JSONObject(BackupFormat.encode(data, 0L)).put("formatVersion", BackupFormat.ENCRYPTED_FORMAT_VERSION + 1)
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

    @Test
    fun encrypted_roundTripsWithTheRightPassword() {
        val file = BackupFormat.encode(data, 0L, password = "correct horse")

        assertTrue(BackupFormat.isEncrypted(file))
        assertEquals(data, BackupFormat.decode(file, password = "correct horse"))
    }

    @Test
    fun encrypted_doesNotContainThePlainText() {
        val file = BackupFormat.encode(data, 0L, password = "pw-123456")

        assertFalse(file.contains("Alex"))
        assertFalse(file.contains("Rivals"))
    }

    @Test
    fun encrypted_withoutAPassword_asksForOne() {
        val file = BackupFormat.encode(data, 0L, password = "pw-123456")

        try {
            BackupFormat.decode(file)
            fail("Expected BackupPasswordRequiredException")
        } catch (e: BackupPasswordRequiredException) {
            assertEquals(R.string.backup_password_needed, e.messageRes)
        }
    }

    @Test
    fun encrypted_withAWrongPassword_isRefusedWithoutChangingAnything() {
        val file = BackupFormat.encode(data, 0L, password = "pw-123456")

        try {
            BackupFormat.decode(file, password = "pw-654321")
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatException) {
            assertEquals(R.string.backup_password_wrong, e.messageRes)
        }
    }

    @Test
    fun encrypted_changedHeaderOrCiphertextIsDetected() {
        val file = BackupFormat.encode(data, 0L, password = "pw-123456")
        val root = JSONObject(file)

        // The iteration count is bound to the ciphertext: a different count fails to open.
        val otherCount = JSONObject(file).put("iterations", root.getInt("iterations") + 1)
        assertRejected(otherCount.toString(), R.string.backup_password_wrong, password = "pw-123456")

        // Flipping a byte of the ciphertext fails the GCM tag check.
        val bytes = java.util.Base64.getDecoder().decode(root.getString("data"))
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        val tampered = JSONObject(file).put("data", java.util.Base64.getEncoder().encodeToString(bytes))
        assertRejected(tampered.toString(), R.string.backup_password_wrong, password = "pw-123456")
    }

    @Test
    fun encrypted_anAbsurdIterationCountIsRefused() {
        val file = BackupFormat.encode(data, 0L, password = "pw-123456")
        val huge = JSONObject(file).put("iterations", Int.MAX_VALUE)

        assertRejected(huge.toString(), R.string.backup_damaged, password = "pw-123456")
    }

    @Test
    fun aPlainBackup_isNotEncrypted_andDecodesWithoutAPassword() {
        val file = BackupFormat.encode(data, 0L)

        assertFalse(BackupFormat.isEncrypted(file))
        assertEquals(data, BackupFormat.decode(file))
    }

    @Test
    fun blankPassword_meansAPlainBackup() {
        assertFalse(BackupFormat.isEncrypted(BackupFormat.encode(data, 0L, password = "")))
    }

    private fun assertRejected(text: String, @StringRes expectedMessage: Int, password: String? = null) {
        try {
            BackupFormat.decode(text, password)
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatException) {
            assertEquals(expectedMessage, e.messageRes)
        }
    }
}
