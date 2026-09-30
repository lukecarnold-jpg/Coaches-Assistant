package com.lukearnold.coachesassistant.ui

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.lukearnold.coachesassistant.R
import com.lukearnold.coachesassistant.data.Sport

/** A scoring action available while tracking a game, e.g. "Try" for +5. */
data class ScoreOption(val label: UiText, val points: Int)

/**
 * What a sport calls the infraction the app counts per player, in each form the screens need:
 * the name ("Penalty"), the stat heading ("Penalties"), a count ("3 penalties"), an event
 * ("#4 Sam penalty", from the player's label), and the +/- buttons' labels ("Add penalty").
 */
enum class Infraction(
    @param:StringRes val label: Int,
    @param:StringRes val plural: Int,
    @param:PluralsRes val count: Int,
    @param:StringRes val event: Int,
    @param:StringRes val add: Int,
    @param:StringRes val remove: Int,
) {
    FOUL(
        R.string.infraction_foul, R.string.infraction_fouls, R.plurals.infraction_foul_count, R.string.infraction_foul_event,
        R.string.infraction_foul_add, R.string.infraction_foul_remove,
    ),
    PENALTY(
        R.string.infraction_penalty, R.string.infraction_penalties, R.plurals.infraction_penalty_count, R.string.infraction_penalty_event,
        R.string.infraction_penalty_add, R.string.infraction_penalty_remove,
    ),
    CARD(
        R.string.infraction_card, R.string.infraction_cards, R.plurals.infraction_card_count, R.string.infraction_card_event,
        R.string.infraction_card_add, R.string.infraction_card_remove,
    ),
    FREE_KICK(
        R.string.infraction_free_kick, R.string.infraction_free_kicks, R.plurals.infraction_free_kick_count, R.string.infraction_free_kick_event,
        R.string.infraction_free_kick_add, R.string.infraction_free_kick_remove,
    ),
    EXCLUSION(
        R.string.infraction_exclusion, R.string.infraction_exclusions, R.plurals.infraction_exclusion_count, R.string.infraction_exclusion_event,
        R.string.infraction_exclusion_add, R.string.infraction_exclusion_remove,
    ),
}

/** The rotation/scoring rules a sport's game screen is configured with. */
data class SportConfig(
    val onCourtSize: Int,
    /**
     * The fewest players a team can take the field with, from the sport's rules where they set one
     * (otherwise a common competition rule, noted beside it). Coaches can change it in Settings.
     */
    val minimumOnCourt: Int,
    /** Position codes, as stored on players and in roster CSVs; see [positionLabel] for display. */
    val squadPositions: List<String>,
    /** Default number of periods: 2 halves, 3 periods, or 4 quarters. */
    val defaultPeriodCount: Int,
    val defaultPeriodMinutes: Int,
    val scoringOptions: List<ScoreOption>,
    val infraction: Infraction,
    /**
     * The keeper's position code, in sports where the keeper usually plays the whole game, so
     * substitution suggestions leave them out. Null where everyone rotates (netball's GK is a
     * defender who rotates like the rest).
     */
    val keeperPosition: String? = null,
)

/**
 * How to show a stored position [code]: its translation, or the code itself for one the app
 * doesn't know (players keep whatever position they were saved with).
 */
fun positionLabel(code: String): UiText =
    POSITION_LABELS[code]?.let { uiText(it) } ?: UiText.Plain(code)

private val POSITION_LABELS: Map<String, Int> = mapOf(
    "PG" to R.string.position_pg,
    "SG" to R.string.position_sg,
    "SF" to R.string.position_sf,
    "PF" to R.string.position_pf,
    "C" to R.string.position_c,
    "GS" to R.string.position_gs,
    "GA" to R.string.position_ga,
    "WA" to R.string.position_wa,
    "WD" to R.string.position_wd,
    "GD" to R.string.position_gd,
    "GK" to R.string.position_gk,
    "DEF" to R.string.position_def,
    "MID" to R.string.position_mid,
    "FWD" to R.string.position_fwd,
    "Full Forward" to R.string.position_full_forward,
    "Half Forward" to R.string.position_half_forward,
    "Midfield" to R.string.position_midfield,
    "Half Back" to R.string.position_half_back,
    "Full Back" to R.string.position_full_back,
    "Ruck" to R.string.position_ruck,
    "Interchange" to R.string.position_interchange,
    "Prop" to R.string.position_prop,
    "Hooker" to R.string.position_hooker,
    "Lock" to R.string.position_lock,
    "Flanker" to R.string.position_flanker,
    "No.8" to R.string.position_no_8,
    "Scrum-half" to R.string.position_scrum_half,
    "Fly-half" to R.string.position_fly_half,
    "Centre" to R.string.position_centre,
    "Wing" to R.string.position_wing,
    "Fullback" to R.string.position_fullback,
    "Second Row" to R.string.position_second_row,
    "Halfback" to R.string.position_halfback,
    "Five-Eighth" to R.string.position_five_eighth,
    "FIX" to R.string.position_fix,
    "ALA" to R.string.position_ala,
    "PIVO" to R.string.position_pivo,
    "G" to R.string.position_g,
    "LD" to R.string.position_ld,
    "RD" to R.string.position_rd,
    "LW" to R.string.position_lw,
    "RW" to R.string.position_rw,
    "Link" to R.string.position_link,
    "Middle" to R.string.position_middle,
    "Driver" to R.string.position_driver,
    "Hole Set" to R.string.position_hole_set,
    "Point" to R.string.position_point,
    "Left Wing" to R.string.position_left_wing,
    "Left Back" to R.string.position_left_back,
    "Centre Back" to R.string.position_centre_back,
    "Right Back" to R.string.position_right_back,
    "Right Wing" to R.string.position_right_wing,
    "Pivot" to R.string.position_pivot,
    "Defense" to R.string.position_defense,
    "Attack" to R.string.position_attack,
    "QB" to R.string.position_qb,
    "WR" to R.string.position_wr,
    "RB" to R.string.position_rb,
    "DB" to R.string.position_db,
    "Rusher" to R.string.position_rusher,
    "Handler" to R.string.position_handler,
    "Cutter" to R.string.position_cutter,
    "Deep" to R.string.position_deep,
)

val Sport.config: SportConfig
    get() = when (this) {
        Sport.BASKETBALL -> SportConfig(
            onCourtSize = 5,
            minimumOnCourt = 5, // FIBA: five to start.
            squadPositions = listOf("PG", "SG", "SF", "PF", "C"),
            defaultPeriodCount = 4,
            defaultPeriodMinutes = 10,
            scoringOptions = listOf(
                ScoreOption(UiText.Plain("+1"), 1),
                ScoreOption(UiText.Plain("+2"), 2),
                ScoreOption(UiText.Plain("+3"), 3),
            ),
            infraction = Infraction.FOUL,
        )

        Sport.NETBALL -> SportConfig(
            onCourtSize = 7,
            minimumOnCourt = 5, // A team may take the court with five.
            squadPositions = listOf("GS", "GA", "WA", "C", "WD", "GD", "GK"),
            defaultPeriodCount = 4,
            defaultPeriodMinutes = 15,
            scoringOptions = listOf(
                ScoreOption(uiText(R.string.score_goal), 1),
            ),
            infraction = Infraction.PENALTY,
        )

        Sport.SOCCER -> SportConfig(
            onCourtSize = 11,
            minimumOnCourt = 7, // IFAB Law 3.
            squadPositions = listOf("GK", "DEF", "MID", "FWD"),
            defaultPeriodCount = 2,
            defaultPeriodMinutes = 45,
            scoringOptions = listOf(
                ScoreOption(uiText(R.string.score_goal), 1),
            ),
            infraction = Infraction.CARD,
            keeperPosition = "GK",
        )

        Sport.AFL -> SportConfig(
            onCourtSize = 18,
            minimumOnCourt = 14, // No universal law; AFL Queensland community competitions start with 14.
            squadPositions = listOf(
                "Full Forward", "Half Forward", "Midfield", "Half Back", "Full Back", "Ruck", "Interchange",
            ),
            defaultPeriodCount = 4,
            defaultPeriodMinutes = 20,
            scoringOptions = listOf(
                ScoreOption(uiText(R.string.score_goal_6), 6),
                ScoreOption(uiText(R.string.score_behind_1), 1),
            ),
            infraction = Infraction.FREE_KICK,
        )

        Sport.RUGBY_UNION -> SportConfig(
            onCourtSize = 15,
            minimumOnCourt = 12, // World Rugby Law 3 leaves it to the match organiser.
            squadPositions = listOf(
                "Prop", "Hooker", "Lock", "Flanker", "No.8", "Scrum-half", "Fly-half", "Centre", "Wing", "Fullback",
            ),
            defaultPeriodCount = 2,
            defaultPeriodMinutes = 40,
            scoringOptions = listOf(
                ScoreOption(uiText(R.string.score_try), 5),
                ScoreOption(uiText(R.string.score_conversion), 2),
                ScoreOption(uiText(R.string.score_penalty), 3),
                ScoreOption(uiText(R.string.score_drop_goal), 3),
            ),
            infraction = Infraction.PENALTY,
        )

        Sport.RUGBY_LEAGUE -> SportConfig(
            onCourtSize = 13,
            minimumOnCourt = 9, // Rugby league laws: nine to start a match.
            squadPositions = listOf(
                "Prop", "Hooker", "Second Row", "Lock", "Halfback", "Five-Eighth", "Centre", "Wing", "Fullback",
            ),
            defaultPeriodCount = 2,
            defaultPeriodMinutes = 40,
            scoringOptions = listOf(
                ScoreOption(uiText(R.string.score_try), 4),
                ScoreOption(uiText(R.string.score_conversion), 2),
                ScoreOption(uiText(R.string.score_penalty), 2),
                ScoreOption(uiText(R.string.score_field_goal), 1),
            ),
            infraction = Infraction.PENALTY,
        )

        Sport.FUTSAL -> SportConfig(
            onCourtSize = 5,
            minimumOnCourt = 3, // Futsal Law 3 (some competitions require four).
            squadPositions = listOf("GK", "FIX", "ALA", "PIVO"),
            defaultPeriodCount = 2,
            defaultPeriodMinutes = 20,
            scoringOptions = listOf(
                ScoreOption(uiText(R.string.score_goal), 1),
            ),
            infraction = Infraction.FOUL,
            keeperPosition = "GK",
        )

        Sport.FIELD_HOCKEY -> SportConfig(
            onCourtSize = 11,
            minimumOnCourt = 7, // The FIH rules set only a maximum; seven is a common competition minimum.
            squadPositions = listOf("GK", "DEF", "MID", "FWD"),
            defaultPeriodCount = 4,
            defaultPeriodMinutes = 15,
            scoringOptions = listOf(
                ScoreOption(uiText(R.string.score_goal), 1),
            ),
            infraction = Infraction.CARD,
            keeperPosition = "GK",
        )

        Sport.ICE_HOCKEY -> SportConfig(
            onCourtSize = 6,
            minimumOnCourt = 5, // IIHF: a goalkeeper and four skaters.
            squadPositions = listOf("G", "LD", "RD", "LW", "C", "RW"),
            defaultPeriodCount = 3,
            defaultPeriodMinutes = 20,
            scoringOptions = listOf(
                ScoreOption(uiText(R.string.score_goal), 1),
            ),
            infraction = Infraction.PENALTY,
            keeperPosition = "G",
        )

        Sport.TOUCH_FOOTBALL -> SportConfig(
            onCourtSize = 6,
            minimumOnCourt = 4, // FIT rules.
            squadPositions = listOf("Link", "Middle", "Wing"),
            defaultPeriodCount = 2,
            defaultPeriodMinutes = 20,
            scoringOptions = listOf(
                ScoreOption(uiText(R.string.score_touchdown), 1),
            ),
            infraction = Infraction.PENALTY,
        )

        Sport.WATER_POLO -> SportConfig(
            onCourtSize = 7,
            minimumOnCourt = 7, // World Aquatics: a team starts with seven.
            squadPositions = listOf("GK", "Driver", "Hole Set", "Wing", "Point"),
            defaultPeriodCount = 4,
            defaultPeriodMinutes = 8,
            scoringOptions = listOf(
                ScoreOption(uiText(R.string.score_goal), 1),
            ),
            infraction = Infraction.EXCLUSION,
            keeperPosition = "GK",
        )

        Sport.HANDBALL -> SportConfig(
            onCourtSize = 7,
            minimumOnCourt = 5, // IHF: five to start.
            squadPositions = listOf(
                "GK", "Left Wing", "Left Back", "Centre Back", "Right Back", "Right Wing", "Pivot",
            ),
            defaultPeriodCount = 2,
            defaultPeriodMinutes = 30,
            scoringOptions = listOf(
                ScoreOption(uiText(R.string.score_goal), 1),
            ),
            infraction = Infraction.FOUL,
            keeperPosition = "GK",
        )

        Sport.LACROSSE -> SportConfig(
            onCourtSize = 10,
            minimumOnCourt = 8, // Competition rules (a team fielding fewer than eight forfeits).
            squadPositions = listOf("GK", "Defense", "Midfield", "Attack"),
            defaultPeriodCount = 4,
            defaultPeriodMinutes = 15,
            scoringOptions = listOf(
                ScoreOption(uiText(R.string.score_goal), 1),
            ),
            infraction = Infraction.PENALTY,
            keeperPosition = "GK",
        )

        Sport.GAELIC_FOOTBALL -> SportConfig(
            onCourtSize = 15,
            minimumOnCourt = 13, // GAA Official Guide: a team may start with 13.
            squadPositions = listOf(
                "GK", "Full Back", "Half Back", "Midfield", "Half Forward", "Full Forward",
            ),
            defaultPeriodCount = 2,
            defaultPeriodMinutes = 30,
            scoringOptions = listOf(
                ScoreOption(uiText(R.string.score_goal_3), 3),
                ScoreOption(uiText(R.string.score_point_1), 1),
            ),
            infraction = Infraction.FOUL,
            keeperPosition = "GK",
        )

        Sport.FLAG_FOOTBALL -> SportConfig(
            onCourtSize = 5,
            minimumOnCourt = 4, // IFAF flag rules.
            squadPositions = listOf("QB", "WR", "RB", "DB", "Rusher"),
            defaultPeriodCount = 2,
            defaultPeriodMinutes = 20,
            scoringOptions = listOf(
                ScoreOption(uiText(R.string.score_touchdown_6), 6),
                ScoreOption(uiText(R.string.score_1_pt_extra_1), 1),
                ScoreOption(uiText(R.string.score_2_pt_extra_2), 2),
                ScoreOption(uiText(R.string.score_safety_2), 2),
            ),
            infraction = Infraction.PENALTY,
        )

        Sport.ULTIMATE_FRISBEE -> SportConfig(
            onCourtSize = 7,
            minimumOnCourt = 5, // WFDF: at least five on the field.
            squadPositions = listOf("Handler", "Cutter", "Deep"),
            defaultPeriodCount = 2,
            defaultPeriodMinutes = 20,
            scoringOptions = listOf(
                ScoreOption(uiText(R.string.score_point), 1),
            ),
            infraction = Infraction.FOUL,
        )
    }
