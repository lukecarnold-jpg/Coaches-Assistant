package com.lukearnold.coachesassistant.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.lukearnold.coachesassistant.data.EventType
import com.lukearnold.coachesassistant.data.GameEntity
import com.lukearnold.coachesassistant.data.GameEventEntity
import com.lukearnold.coachesassistant.data.PlayerEntity
import com.lukearnold.coachesassistant.data.Sport
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Screen-level behaviour that was previously only checked by hand on the emulator: confirmation
 * dialogs, toggling rows by their label, and the clock editor. Runs on Robolectric, hosted in a
 * ComponentActivity so screens with file pickers have an activity-result registry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenBehaviourTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val finishedGame = GameEntity(
        id = 3,
        title = "Rnd 3",
        opponent = "Rivals",
        createdAt = 0L,
        teamScore = 4,
        opponentScore = 2,
        isFinished = true,
        finishedAt = 1L,
    )
    private val tommy = PlayerEntity(id = 7, name = "Tommy", jerseyNumber = 2)

    /** The dialog's copy of a button label, as opposed to the screen button that opened it. */
    private fun dialogButton(label: String) = compose.onNode(hasText(label) and hasAnyAncestor(isDialog()))

    @Test
    fun historyDelete_asksFirst_andOnlyDeletesOnConfirm() {
        val deleted = mutableListOf<Long>()
        compose.setContent {
            GameHistoryScreen(
                games = listOf(finishedGame),
                season = SeasonSummary(),
                infraction = Infraction.FOUL,
                onViewGame = {},
                onDeleteGame = { deleted += it },
                onExportGame = { _, _ -> },
            )
        }

        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete \"Rnd 3\" (Us 4 – 2 Rivals)", substring = true).assertExists()
        dialogButton("Cancel").performClick()
        assertEquals(emptyList<Long>(), deleted)

        compose.onNodeWithText("Delete").performClick()
        dialogButton("Delete").performClick()
        assertEquals(listOf(3L), deleted)
    }

    @Test
    fun rosterRemove_asksFirst_andOnlyRemovesOnConfirm() {
        val removed = mutableListOf<Long>()
        compose.setContent {
            RosterScreen(
                isExpanded = false,
                sport = Sport.BASKETBALL,
                players = listOf(tommy),
                onAddPlayer = { _, _, _ -> },
                onUpdatePlayer = { _, _, _, _ -> },
                onDeletePlayer = { removed += it },
                onExportPlayers = {},
                onImportPlayers = {},
            )
        }

        // The player list starts below the add-player form, off the small test screen: scroll first.
        compose.onNodeWithText("Remove").performScrollTo().performClick()
        compose.onNodeWithText("Remove #2 Tommy from the roster?", substring = true).assertExists()
        dialogButton("Cancel").performClick()
        assertEquals(emptyList<Long>(), removed)

        compose.onNodeWithText("Remove").performScrollTo().performClick()
        dialogButton("Remove").performClick()
        assertEquals(listOf(7L), removed)
    }

    @Test
    fun attendance_tappingAPlayersName_togglesThem() {
        var saved: List<Long>? = null
        compose.setContent {
            AttendanceDialog(players = listOf(tommy), absentPlayerIds = emptyList(), onDismiss = {}, onConfirm = { saved = it })
        }

        val row = compose.onNode(hasText("#2 Tommy") and hasAnyAncestor(isDialog()), useUnmergedTree = false)
        row.assertIsOff()
        row.performClick() // the name, not the checkbox
        row.assertIsOn()
        dialogButton("Save").performClick()
        assertEquals(listOf(7L), saved)
    }

    @Test
    fun clockEditor_worksInTimeRemaining() {
        val set = mutableListOf<Long>()
        val game = GameEntity(title = "G", opponent = "", createdAt = 0L, periodDurationMillis = 600_000L, runningTimeMillis = 180_000L)
        compose.setContent {
            GameClock(game = game, gameTimeMillis = { 180_000L }, onToggleTimer = {}, onResetTimer = {}, onSetGameTime = { set += it })
        }

        compose.onNodeWithText("07:00").assertExists() // 10:00 period, 3:00 played
        compose.onNodeWithContentDescription("Edit time").performClick()
        compose.onNodeWithText("7").assertExists() // opens on the time remaining, not elapsed
        compose.onNodeWithText("7").performTextReplacement("5")
        compose.onNodeWithText("Set").performClick()
        assertEquals(listOf(300_000L), set) // 5:00 left = 5:00 elapsed
    }

    @Test
    fun clockReset_asksFirst() {
        var resets = 0
        val game = GameEntity(title = "G", opponent = "", createdAt = 0L, periodDurationMillis = 600_000L)
        compose.setContent {
            GameClock(game = game, gameTimeMillis = { 0L }, onToggleTimer = {}, onResetTimer = { resets++ }, onSetGameTime = {})
        }

        compose.onNodeWithContentDescription("Reset clock").performClick()
        dialogButton("Cancel").performClick()
        assertEquals(0, resets)

        compose.onNodeWithContentDescription("Reset clock").performClick()
        dialogButton("Reset").performClick()
        assertEquals(1, resets)
    }

    @Test
    fun infractionButtons_areLabelledWithTheSportsInfraction() {
        val adjustments = mutableListOf<Int>()
        val stat = PlayerGameStat(
            player = tommy,
            points = 0,
            fouls = 1,
            mvpVotes = 0,
            timesSubbedOff = 0,
            onCourt = true,
            currentShiftTimeMillis = 0L,
            currentBenchTimeMillis = 0L,
            shiftStartGameTimeMillis = 0L,
            totalTimeMillis = 0L,
        )
        compose.setContent {
            PlayerActionCard(
                stat = stat,
                sportConfig = Sport.NETBALL.config,
                onAddScore = { _, _ -> },
                onAddFoul = { _, adjustment -> adjustments += adjustment },
                onAddMvpVote = {},
                gameTimeMillis = { 0L },
                fairShareAt = { null },
            )
        }

        compose.onNodeWithContentDescription("Add penalty").performClick()
        compose.onNodeWithContentDescription("Remove penalty").performClick()
        assertEquals(listOf(1, -1), adjustments)
    }

    @Test
    fun eventLog_offersEditAndRemoveOnlyForEventsThatCanChangeOnTheirOwn() {
        val removed = mutableListOf<Long>()
        val lineup = GameEventEntity(id = 1, gameId = 1, timestamp = 1, type = EventType.START_LINEUP.name, note = "7")
        val score = GameEventEntity(id = 2, gameId = 1, timestamp = 2, type = EventType.SCORE.name, playerId = 7, points = 2)
        compose.setContent {
            // Scrollable, as on the Game screen, since the log is below the game list.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                GameStatsPanel(
                    events = listOf(score, lineup),
                    playerById = mapOf(7L to tommy),
                    onDeleteEvent = { removed += it.id },
                )
            }
        }

        // One of each button: the score's, not the lineup's.
        compose.onAllNodesWithContentDescription("Edit Event").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Remove event").assertCountEquals(1)
        compose.onNodeWithContentDescription("Remove event").performScrollTo().performClick()
        assertEquals(listOf(2L), removed)
    }
}
