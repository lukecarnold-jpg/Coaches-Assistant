package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.data.GameEntity
import com.lukearnold.coachesassistant.data.GameEventEntity
import com.lukearnold.coachesassistant.data.GameLineup
import com.lukearnold.coachesassistant.data.PlayerEntity

/** One roster player's totals across the season's finished games. */
data class PlayerSeasonStat(
    val player: PlayerEntity,
    /**
     * Finished games in which they were on court (see PlayerGameStat.wasOnCourt), whether or not
     * the clock was used; [averageTimeMillis] is over these.
     */
    val gamesPlayed: Int,
    val totalTimeMillis: Long,
    val points: Int,
    val fouls: Int,
    val mvpVotes: Int,
) {
    val averageTimeMillis: Long get() = if (gamesPlayed == 0) 0L else totalTimeMillis / gamesPlayed
}

/** Team record and per-player totals across a sport's finished games (the "season"). */
data class SeasonSummary(
    val games: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val ties: Int = 0,
    val pointsFor: Int = 0,
    val pointsAgainst: Int = 0,
    val players: List<PlayerSeasonStat> = emptyList(),
)

/**
 * Adds up [players]' stats across every finished game in [games], using the same per-game
 * calculation as the Game screen ([calculatePlayerGameStats]) so the two always agree. [events]
 * and [lineups] may cover any games; each game only uses its own. Players are listed in roster
 * order, including anyone who hasn't played yet.
 *
 * A pure function (no Android/coroutine dependencies) so it can be unit tested directly.
 */
fun calculateSeasonStats(
    players: List<PlayerEntity>,
    games: List<GameEntity>,
    events: List<GameEventEntity>,
    lineups: Map<Long, GameLineup>,
): SeasonSummary {
    val finished = games.filter { it.isFinished }
    val eventsByGame = events.groupBy { it.gameId }
    val perGame = finished.flatMap { game ->
        calculatePlayerGameStats(players, game, lineups[game.id] ?: GameLineup(), eventsByGame[game.id].orEmpty())
    }.groupBy { it.player.id }

    return SeasonSummary(
        games = finished.size,
        wins = finished.count { it.teamScore > it.opponentScore },
        losses = finished.count { it.teamScore < it.opponentScore },
        ties = finished.count { it.teamScore == it.opponentScore },
        pointsFor = finished.sumOf { it.teamScore },
        pointsAgainst = finished.sumOf { it.opponentScore },
        players = players.map { player ->
            val stats = perGame[player.id].orEmpty()
            PlayerSeasonStat(
                player = player,
                gamesPlayed = stats.count { it.wasOnCourt || it.totalTimeMillis > 0 },
                totalTimeMillis = stats.sumOf { it.totalTimeMillis },
                points = stats.sumOf { it.points },
                fouls = stats.sumOf { it.fouls },
                mvpVotes = stats.sumOf { it.mvpVotes },
            )
        },
    )
}
