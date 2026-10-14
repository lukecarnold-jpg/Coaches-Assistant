package com.lonrad.coachesassistant.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** How a batter's innings ended, as cricket scorecards record it. */
enum class Dismissal {
    DID_NOT_BAT,
    NOT_OUT,

    /** Retired (e.g. at a junior competition's run limit); counts as not out. */
    RETIRED,
    BOWLED,
    CAUGHT,
    LBW,
    RUN_OUT,
    STUMPED,

    /** Hit wicket, obstructing the field, and the other rarer ways out. */
    OTHER_OUT,
    ;

    val batted: Boolean get() = this != DID_NOT_BAT

    /** Whether the batter was out, so the innings counts towards the batting average's divisor. */
    val isOut: Boolean get() = batted && this != NOT_OUT && this != RETIRED

    companion object {
        fun fromRaw(raw: String): Dismissal = entries.firstOrNull { it.name == raw } ?: DID_NOT_BAT
    }
}

/**
 * One player's batting, bowling, and fielding in one cricket game, as entered from the scorebook.
 * Bowling is kept in balls rather than overs so partial overs add up correctly (3.4 + 2.2 = 6.0).
 *
 * Like game events, a scorecard is a record of what happened, so deleting the player clears the
 * reference rather than the row (the game's totals still add up); deleting the game removes it.
 */
@Entity(
    tableName = "cricket_scorecards",
    indices = [Index(value = ["gameId", "playerId"], unique = true), Index("playerId")],
    foreignKeys = [
        ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["gameId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PlayerEntity::class, parentColumns = ["id"], childColumns = ["playerId"], onDelete = ForeignKey.SET_NULL),
    ],
)
data class CricketScorecardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    val playerId: Long?,
    /** A [Dismissal] name. */
    val dismissal: String = Dismissal.DID_NOT_BAT.name,
    val runs: Int = 0,
    val ballsFaced: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val ballsBowled: Int = 0,
    val maidens: Int = 0,
    val runsConceded: Int = 0,
    val wickets: Int = 0,
    val catches: Int = 0,
    val runOuts: Int = 0,
    val stumpings: Int = 0,
) {
    val howOut: Dismissal get() = Dismissal.fromRaw(dismissal)

    /** Whether anything has been entered, so an untouched card can be told apart from a duck. */
    val isEmpty: Boolean
        get() = !howOut.batted && ballsBowled == 0 && wickets == 0 && runsConceded == 0 && maidens == 0 &&
            catches == 0 && runOuts == 0 && stumpings == 0
}

/**
 * Where one player is fielding in a game's field plan, as a [CricketPosition] name. Each player has
 * at most one position and each position at most one player; the repository keeps it that way.
 * It's a plan rather than a record, so it goes with the player or game when either is deleted.
 */
@Entity(
    tableName = "cricket_field_positions",
    primaryKeys = ["gameId", "playerId"],
    indices = [Index("playerId")],
    foreignKeys = [
        ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["gameId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PlayerEntity::class, parentColumns = ["id"], childColumns = ["playerId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class CricketFieldPositionEntity(
    val gameId: Long,
    val playerId: Long,
    val position: String,
)

/** Whether [this] game is recorded with scorecards (see Sport.usesScorecards). */
val GameEntity.usesScorecards: Boolean get() = Sport.entries.firstOrNull { it.name == sport }?.usesScorecards == true

/** Overs as cricket writes them from a ball count: 22 balls is "3.4" (3 overs and 4 balls). */
fun formatOvers(balls: Int): String = "${balls / 6}.${balls % 6}"

/**
 * The ball count for overs written as cricket does ("3.4" is 3 overs and 4 balls), or null if
 * [text] isn't that: a whole number of overs, or overs then 0 to 5 balls after a dot.
 */
fun parseOvers(text: String): Int? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return 0
    val parts = trimmed.split('.')
    if (parts.size > 2) return null
    val overs = parts[0].ifEmpty { "0" }.toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val balls = if (parts.size == 2) parts[1].toIntOrNull()?.takeIf { it in 0..5 && parts[1].length == 1 } ?: return null else 0
    return overs * 6 + balls
}

/**
 * Our innings total for [game] from its [scorecards]: the batters' runs plus extras, and the
 * wickets that fell.
 */
fun cricketTotals(game: GameEntity, scorecards: List<CricketScorecardEntity>): Pair<Int, Int> =
    (scorecards.sumOf { it.runs } + game.extras) to scorecards.count { it.howOut.isOut }
