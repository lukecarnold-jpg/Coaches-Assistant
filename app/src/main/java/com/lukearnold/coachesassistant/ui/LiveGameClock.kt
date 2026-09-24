package com.lukearnold.coachesassistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.lukearnold.coachesassistant.data.GameEntity
import com.lukearnold.coachesassistant.data.currentGameTimeMillis
import kotlinx.coroutines.delay

/**
 * The single live game clock for the Game screen: [game]'s elapsed time in the current period,
 * updated while its clock runs. The game clock, player cards, and stats all read this one value
 * instead of each running its own loop, so they tick together.
 *
 * Callers should pass it down as `{ liveTime.value }` and read it only where a time is displayed,
 * so each tick recomposes those labels rather than the whole screen.
 */
@Composable
fun rememberLiveGameTime(game: GameEntity?): State<Long> =
    produceState(
        initialValue = game?.currentGameTimeMillis() ?: 0L,
        game?.id,
        game?.runningTimeMillis,
        game?.timerLastStartedAt,
        game?.periodDurationMillis,
    ) {
        if (game == null) {
            value = 0L
            return@produceState
        }
        value = game.currentGameTimeMillis()
        if (game.timerLastStartedAt == null) return@produceState

        // currentGameTimeMillis stops at the period length, so once there the value just stops
        // changing (AppViewModel stops the clock itself).
        while (true) {
            delay(millisUntilNextSecond(value))
            value = game.currentGameTimeMillis()
        }
    }

/**
 * How long until [elapsedMillis] next crosses a whole second, when every displayed time (which
 * shows whole seconds) changes. Ticking then, rather than on a fixed short interval, keeps the
 * display exact with one update per second.
 */
fun millisUntilNextSecond(elapsedMillis: Long): Long = 1000L - Math.floorMod(elapsedMillis, 1000L)
