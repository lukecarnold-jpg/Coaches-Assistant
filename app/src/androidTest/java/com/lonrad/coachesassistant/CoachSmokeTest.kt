package com.lonrad.coachesassistant

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One game from start to finish in the real app on a device: pick a sport, add players, create a
 * game, set the lineup, make a substitution, end the game, and find it in History. The unit tests
 * (on Robolectric) cover the logic; this catches what only shows up on a real Android build.
 *
 * It starts from an empty app, so it deletes the app's data first, and only runs on an emulator
 * to make sure that's never a phone with real games on it.
 */
@RunWith(AndroidJUnit4::class)
class CoachSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun text(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    @Before
    fun startFromAnEmptyApp() {
        assumeTrue("Deletes the app's data, so it only runs on an emulator", Build.HARDWARE in setOf("ranchu", "goldfish"))
        context.deleteDatabase("basketball_rotation.db")
        for (prefs in listOf("sport_prefs", "display_prefs", "fielding_prefs", "game_day_prefs")) {
            context.deleteSharedPreferences(prefs)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun close() {
        scenario?.close()
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Waits for the snackbar saying [message] to close, since it covers the bottom of the screen. */
    private fun waitForSnackbar(@StringRes message: Int) {
        waitFor(hasText(text(message)))
        compose.waitUntil(timeoutMillis = 15_000) { compose.onAllNodes(hasText(text(message))).fetchSemanticsNodes().isEmpty() }
    }

    private fun openTab(@StringRes label: Int) {
        compose.onNode(hasText(text(label)) and hasClickAction()).performClick()
    }

    @Test
    fun aWholeGame_fromAnEmptyApp_toHistory() {
        compose.onNodeWithText("Basketball").performClick()

        val players = (1..6).map { "Player $it" }
        waitFor(hasText(text(R.string.player_name)) and hasSetTextAction())
        for (player in players) {
            compose.onNode(hasText(text(R.string.player_name)) and hasSetTextAction()).performTextInput(player)
            compose.onNodeWithText(text(R.string.add_player)).performClick()
            waitFor(hasText(player))
        }

        openTab(R.string.game)
        compose.onNodeWithText(text(R.string.create_game)).performScrollTo().performClick()
        waitForSnackbar(R.string.msg_game_created)

        // Five of the six start; Player 6 is on the bench.
        waitFor(hasText(players[0]) and isToggleable())
        for (player in players.take(5)) {
            compose.onNode(hasText(player) and isToggleable()).performScrollTo().performClick()
        }
        compose.onNodeWithText(text(R.string.save_starting_lineup)).performScrollTo().performClick()
        waitForSnackbar(R.string.msg_lineup_saved)

        // Player 1 off, Player 6 on.
        waitFor(hasText(players[5]) and isSelectable())
        compose.onNode(hasText(players[0]) and isSelectable()).performScrollTo().performClick()
        compose.onNode(hasText(players[5]) and isSelectable()).performScrollTo().performClick()
        val confirm = context.resources.getQuantityString(R.plurals.confirm_rotation, 1, 1)
        compose.onNodeWithText(confirm).performScrollTo().performClick()
        // Player 1 is now on the bench, so they're offered as coming back on.
        waitFor(hasText(players[0]) and isSelectable())

        compose.onNodeWithText(text(R.string.end_game_title)).performScrollTo().performClick()
        compose.onNode(hasText(text(R.string.end_game)) and hasClickAction()).performClick()

        openTab(R.string.history)
        waitFor(hasText(text(R.string.default_game_title, 1)))
    }
}
