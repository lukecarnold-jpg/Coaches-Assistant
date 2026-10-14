package com.lonrad.coachesassistant.ui

import com.lonrad.coachesassistant.data.CricketScorecardEntity
import com.lonrad.coachesassistant.data.Dismissal
import com.lonrad.coachesassistant.data.GameEntity
import com.lonrad.coachesassistant.data.PlayerEntity
import com.lonrad.coachesassistant.data.formatOvers
import com.lonrad.coachesassistant.data.parseOvers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CricketStatsTest {

    private val sam = PlayerEntity(id = 1, name = "Sam", sport = "CRICKET")
    private val kit = PlayerEntity(id = 2, name = "Kit", sport = "CRICKET")

    private fun game(id: Long, us: Int, them: Int, finished: Boolean = true) = GameEntity(
        id = id, title = "G$id", opponent = "", createdAt = id, teamScore = us, opponentScore = them,
        isFinished = finished, sport = "CRICKET",
    )

    private fun card(gameId: Long, playerId: Long, dismissal: Dismissal, runs: Int = 0, balls: Int = 0) =
        CricketScorecardEntity(gameId = gameId, playerId = playerId, dismissal = dismissal.name, runs = runs, ballsFaced = balls)

    @Test
    fun overs_areWrittenAndReadAsOversDotBalls() {
        assertEquals("3.4", formatOvers(22))
        assertEquals("0.0", formatOvers(0))
        assertEquals(22, parseOvers("3.4"))
        assertEquals(24, parseOvers("4"))
        assertEquals(0, parseOvers(""))
        assertNull(parseOvers("3.6")) // only 0 to 5 balls after the dot
        assertNull(parseOvers("3.45"))
        assertNull(parseOvers("1.2.3"))
    }

    @Test
    fun battingAverage_dividesByDismissalsSoNotOutsDontLowerIt() {
        val season = calculateCricketSeason(
            players = listOf(sam),
            games = listOf(game(1, 100, 90), game(2, 80, 120), game(3, 50, 50)),
            scorecards = listOf(
                card(1, 1, Dismissal.CAUGHT, runs = 30, balls = 40),
                card(2, 1, Dismissal.NOT_OUT, runs = 25, balls = 20),
                card(3, 1, Dismissal.BOWLED, runs = 5, balls = 10),
            ),
        )
        val stat = season.players.single()

        assertEquals(3, stat.innings)
        assertEquals(1, stat.notOuts)
        assertEquals(60, stat.runs)
        assertEquals(30.0, stat.battingAverage!!, 0.001) // 60 runs, 2 dismissals
        assertEquals(85.714, stat.strikeRate!!, 0.001) // 60 runs from 70 balls
        assertEquals(30, stat.highScore)
        assertFalse(stat.highScoreNotOut)
        assertEquals(Triple(1, 1, 1), Triple(season.wins, season.losses, season.ties))
    }

    @Test
    fun aRetiredOrNotOutInnings_hasNoAverageUntilTheyreOut_andAnEqualNotOutTopScoreIsStarred() {
        val stat = calculateCricketSeason(
            players = listOf(sam),
            games = listOf(game(1, 0, 0), game(2, 0, 0)),
            scorecards = listOf(card(1, 1, Dismissal.CAUGHT, runs = 25), card(2, 1, Dismissal.RETIRED, runs = 25)),
        ).players.single()

        assertEquals(25, stat.highScore)
        assertTrue(stat.highScoreNotOut)
        assertEquals(50.0, stat.battingAverage!!, 0.001)

        val unbeaten = calculateCricketSeason(listOf(sam), listOf(game(1, 0, 0)), listOf(card(1, 1, Dismissal.NOT_OUT, runs = 12)))
        assertNull(unbeaten.players.single().battingAverage)
    }

    @Test
    fun bowling_addsBallsAcrossGames_andBestIsMostWicketsThenFewestRuns() {
        val stat = calculateCricketSeason(
            players = listOf(kit),
            games = listOf(game(1, 0, 0), game(2, 0, 0), game(3, 0, 0)),
            scorecards = listOf(
                CricketScorecardEntity(gameId = 1, playerId = 2, ballsBowled = 22, runsConceded = 20, wickets = 2),
                CricketScorecardEntity(gameId = 2, playerId = 2, ballsBowled = 14, runsConceded = 10, wickets = 2),
                CricketScorecardEntity(gameId = 3, playerId = 2, ballsBowled = 12, runsConceded = 30, wickets = 1),
            ),
        ).players.single()

        assertEquals(48, stat.ballsBowled) // 3.4 + 2.2 + 2.0 = 8 overs
        assertEquals(5, stat.wickets)
        assertEquals(2 to 10, stat.bestBowling)
        assertEquals(12.0, stat.bowlingAverage!!, 0.001) // 60 runs, 5 wickets
        assertEquals(7.5, stat.economy!!, 0.001) // 60 runs from 8 overs
        assertFalse(stat.hasBatted)
    }

    @Test
    fun onlyFinishedGamesCount_andEveryRosterPlayerIsListed() {
        val season = calculateCricketSeason(
            players = listOf(sam, kit),
            games = listOf(game(1, 10, 5), game(2, 99, 1, finished = false)),
            scorecards = listOf(card(1, 1, Dismissal.BOWLED, runs = 10), card(2, 1, Dismissal.BOWLED, runs = 99)),
        )

        assertEquals(1, season.games)
        assertEquals(10, season.players.first { it.player == sam }.runs)
        assertEquals(0, season.players.first { it.player == kit }.games)
    }
}
