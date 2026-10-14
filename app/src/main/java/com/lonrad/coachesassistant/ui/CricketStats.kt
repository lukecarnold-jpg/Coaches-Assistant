package com.lonrad.coachesassistant.ui

import com.lonrad.coachesassistant.data.CricketScorecardEntity
import com.lonrad.coachesassistant.data.GameEntity
import com.lonrad.coachesassistant.data.PlayerEntity

/** One player's cricket totals across the season's finished games. */
data class CricketSeasonStat(
    val player: PlayerEntity,
    /** Finished games with anything on their scorecard. */
    val games: Int = 0,
    val innings: Int = 0,
    val notOuts: Int = 0,
    val runs: Int = 0,
    val ballsFaced: Int = 0,
    val highScore: Int = 0,
    /** Whether the [highScore] innings was not out (written "45*"). */
    val highScoreNotOut: Boolean = false,
    val fours: Int = 0,
    val sixes: Int = 0,
    val ballsBowled: Int = 0,
    val maidens: Int = 0,
    val runsConceded: Int = 0,
    val wickets: Int = 0,
    /** Best bowling in a game: most wickets, then fewest runs (written "3/12"); null if they haven't bowled. */
    val bestBowling: Pair<Int, Int>? = null,
    val catches: Int = 0,
    val runOuts: Int = 0,
    val stumpings: Int = 0,
) {
    /** Runs per dismissal; null until they've been out (an average needs a dismissal to divide by). */
    val battingAverage: Double? get() = (innings - notOuts).takeIf { it > 0 }?.let { runs.toDouble() / it }

    /** Runs per 100 balls faced; null if they haven't faced a ball. */
    val strikeRate: Double? get() = ballsFaced.takeIf { it > 0 }?.let { runs * 100.0 / it }

    /** Runs conceded per wicket; null until they've taken one. */
    val bowlingAverage: Double? get() = wickets.takeIf { it > 0 }?.let { runsConceded.toDouble() / it }

    /** Runs conceded per over (6 balls); null if they haven't bowled. */
    val economy: Double? get() = ballsBowled.takeIf { it > 0 }?.let { runsConceded * 6.0 / it }

    val hasBatted: Boolean get() = innings > 0
    val hasBowled: Boolean get() = ballsBowled > 0
    val hasFielded: Boolean get() = catches + runOuts + stumpings > 0
}

/** Team record and per-player totals across a cricket team's finished games. */
data class CricketSeasonSummary(
    val games: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val ties: Int = 0,
    val players: List<CricketSeasonStat> = emptyList(),
)

/**
 * Adds up [players]' scorecards across every finished game in [games]. [scorecards] may cover any
 * games; only finished games' count. A game is won on runs: teamScore (our runs) against
 * opponentScore (theirs). Players are listed in roster order, including anyone yet to play.
 */
fun calculateCricketSeason(
    players: List<PlayerEntity>,
    games: List<GameEntity>,
    scorecards: List<CricketScorecardEntity>,
): CricketSeasonSummary {
    val finished = games.filter { it.isFinished }
    val finishedIds = finished.map { it.id }.toSet()
    val cardsByPlayer = scorecards.filter { it.gameId in finishedIds && !it.isEmpty }.groupBy { it.playerId }

    return CricketSeasonSummary(
        games = finished.size,
        wins = finished.count { it.teamScore > it.opponentScore },
        losses = finished.count { it.teamScore < it.opponentScore },
        ties = finished.count { it.teamScore == it.opponentScore },
        players = players.map { player -> seasonStat(player, cardsByPlayer[player.id].orEmpty()) },
    )
}

private fun seasonStat(player: PlayerEntity, cards: List<CricketScorecardEntity>): CricketSeasonStat {
    val innings = cards.filter { it.howOut.batted }
    // The highest score, preferring a not-out one when two are equal.
    val best = innings.maxWithOrNull(compareBy({ it.runs }, { !it.howOut.isOut }))
    val bowled = cards.filter { it.ballsBowled > 0 }
    val bestBowling = bowled.maxWithOrNull(compareBy({ it.wickets }, { -it.runsConceded }))
    return CricketSeasonStat(
        player = player,
        games = cards.map { it.gameId }.distinct().size,
        innings = innings.size,
        notOuts = innings.count { !it.howOut.isOut },
        runs = innings.sumOf { it.runs },
        ballsFaced = innings.sumOf { it.ballsFaced },
        highScore = best?.runs ?: 0,
        highScoreNotOut = best?.howOut?.isOut == false,
        fours = innings.sumOf { it.fours },
        sixes = innings.sumOf { it.sixes },
        ballsBowled = cards.sumOf { it.ballsBowled },
        maidens = cards.sumOf { it.maidens },
        runsConceded = cards.sumOf { it.runsConceded },
        wickets = cards.sumOf { it.wickets },
        bestBowling = bestBowling?.let { it.wickets to it.runsConceded },
        catches = cards.sumOf { it.catches },
        runOuts = cards.sumOf { it.runOuts },
        stumpings = cards.sumOf { it.stumpings },
    )
}
