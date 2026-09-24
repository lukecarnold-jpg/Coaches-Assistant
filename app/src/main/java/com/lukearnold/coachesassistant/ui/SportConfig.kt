package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.data.Sport

/** A scoring action available while tracking a game, e.g. "Try" for +5. */
data class ScoreOption(val label: String, val points: Int)

/** The rotation/scoring rules a sport's game screen is configured with. */
data class SportConfig(
    val onCourtSize: Int,
    val squadPositions: List<String>,
    /** Default number of periods: 2 halves, 3 periods, or 4 quarters. */
    val defaultPeriodCount: Int,
    val defaultPeriodMinutes: Int,
    val scoringOptions: List<ScoreOption>,
    val infractionLabel: String,
) {
    /** Plural form for stat summaries, e.g. "Penalty" -> "Penalties". */
    val infractionLabelPlural: String
        get() = if (infractionLabel.endsWith("y")) "${infractionLabel.dropLast(1)}ies" else "${infractionLabel}s"
}

val Sport.config: SportConfig
    get() = when (this) {
        Sport.BASKETBALL -> SportConfig(
            onCourtSize = 5,
            squadPositions = listOf("PG", "SG", "SF", "PF", "C"),
            defaultPeriodCount = 4,
            defaultPeriodMinutes = 10,
            scoringOptions = listOf(
                ScoreOption("+1", 1),
                ScoreOption("+2", 2),
                ScoreOption("+3", 3),
            ),
            infractionLabel = "Foul",
        )

        Sport.NETBALL -> SportConfig(
            onCourtSize = 7,
            squadPositions = listOf("GS", "GA", "WA", "C", "WD", "GD", "GK"),
            defaultPeriodCount = 4,
            defaultPeriodMinutes = 15,
            scoringOptions = listOf(
                ScoreOption("Goal", 1),
            ),
            infractionLabel = "Penalty",
        )

        Sport.SOCCER -> SportConfig(
            onCourtSize = 11,
            squadPositions = listOf("GK", "DEF", "MID", "FWD"),
            defaultPeriodCount = 2,
            defaultPeriodMinutes = 45,
            scoringOptions = listOf(
                ScoreOption("Goal", 1),
            ),
            infractionLabel = "Card",
        )

        Sport.AFL -> SportConfig(
            onCourtSize = 18,
            squadPositions = listOf(
                "Full Forward", "Half Forward", "Midfield", "Half Back", "Full Back", "Ruck", "Interchange",
            ),
            defaultPeriodCount = 4,
            defaultPeriodMinutes = 20,
            scoringOptions = listOf(
                ScoreOption("Goal (6)", 6),
                ScoreOption("Behind (1)", 1),
            ),
            infractionLabel = "Free Kick",
        )

        Sport.RUGBY_UNION -> SportConfig(
            onCourtSize = 15,
            squadPositions = listOf(
                "Prop", "Hooker", "Lock", "Flanker", "No.8", "Scrum-half", "Fly-half", "Centre", "Wing", "Fullback",
            ),
            defaultPeriodCount = 2,
            defaultPeriodMinutes = 40,
            scoringOptions = listOf(
                ScoreOption("Try", 5),
                ScoreOption("Conversion", 2),
                ScoreOption("Penalty", 3),
                ScoreOption("Drop Goal", 3),
            ),
            infractionLabel = "Penalty",
        )

        Sport.RUGBY_LEAGUE -> SportConfig(
            onCourtSize = 13,
            squadPositions = listOf(
                "Prop", "Hooker", "Second Row", "Lock", "Halfback", "Five-Eighth", "Centre", "Wing", "Fullback",
            ),
            defaultPeriodCount = 2,
            defaultPeriodMinutes = 40,
            scoringOptions = listOf(
                ScoreOption("Try", 4),
                ScoreOption("Conversion", 2),
                ScoreOption("Penalty", 2),
                ScoreOption("Field Goal", 1),
            ),
            infractionLabel = "Penalty",
        )

        Sport.FUTSAL -> SportConfig(
            onCourtSize = 5,
            squadPositions = listOf("GK", "FIX", "ALA", "PIVO"),
            defaultPeriodCount = 2,
            defaultPeriodMinutes = 20,
            scoringOptions = listOf(
                ScoreOption("Goal", 1),
            ),
            infractionLabel = "Foul",
        )

        Sport.FIELD_HOCKEY -> SportConfig(
            onCourtSize = 11,
            squadPositions = listOf("GK", "DEF", "MID", "FWD"),
            defaultPeriodCount = 4,
            defaultPeriodMinutes = 15,
            scoringOptions = listOf(
                ScoreOption("Goal", 1),
            ),
            infractionLabel = "Card",
        )

        Sport.ICE_HOCKEY -> SportConfig(
            onCourtSize = 6,
            squadPositions = listOf("G", "LD", "RD", "LW", "C", "RW"),
            defaultPeriodCount = 3,
            defaultPeriodMinutes = 20,
            scoringOptions = listOf(
                ScoreOption("Goal", 1),
            ),
            infractionLabel = "Penalty",
        )

        Sport.TOUCH_FOOTBALL -> SportConfig(
            onCourtSize = 6,
            squadPositions = listOf("Link", "Middle", "Wing"),
            defaultPeriodCount = 2,
            defaultPeriodMinutes = 20,
            scoringOptions = listOf(
                ScoreOption("Touchdown", 1),
            ),
            infractionLabel = "Penalty",
        )

        Sport.WATER_POLO -> SportConfig(
            onCourtSize = 7,
            squadPositions = listOf("GK", "Driver", "Hole Set", "Wing", "Point"),
            defaultPeriodCount = 4,
            defaultPeriodMinutes = 8,
            scoringOptions = listOf(
                ScoreOption("Goal", 1),
            ),
            infractionLabel = "Exclusion",
        )

        Sport.HANDBALL -> SportConfig(
            onCourtSize = 7,
            squadPositions = listOf(
                "GK", "Left Wing", "Left Back", "Centre Back", "Right Back", "Right Wing", "Pivot",
            ),
            defaultPeriodCount = 2,
            defaultPeriodMinutes = 30,
            scoringOptions = listOf(
                ScoreOption("Goal", 1),
            ),
            infractionLabel = "Foul",
        )

        Sport.LACROSSE -> SportConfig(
            onCourtSize = 10,
            squadPositions = listOf("GK", "Defense", "Midfield", "Attack"),
            defaultPeriodCount = 4,
            defaultPeriodMinutes = 15,
            scoringOptions = listOf(
                ScoreOption("Goal", 1),
            ),
            infractionLabel = "Penalty",
        )

        Sport.GAELIC_FOOTBALL -> SportConfig(
            onCourtSize = 15,
            squadPositions = listOf(
                "GK", "Full Back", "Half Back", "Midfield", "Half Forward", "Full Forward",
            ),
            defaultPeriodCount = 2,
            defaultPeriodMinutes = 30,
            scoringOptions = listOf(
                ScoreOption("Goal (3)", 3),
                ScoreOption("Point (1)", 1),
            ),
            infractionLabel = "Foul",
        )

        Sport.FLAG_FOOTBALL -> SportConfig(
            onCourtSize = 5,
            squadPositions = listOf("QB", "WR", "RB", "DB", "Rusher"),
            defaultPeriodCount = 2,
            defaultPeriodMinutes = 20,
            scoringOptions = listOf(
                ScoreOption("Touchdown (6)", 6),
                ScoreOption("1-pt Extra (1)", 1),
                ScoreOption("2-pt Extra (2)", 2),
                ScoreOption("Safety (2)", 2),
            ),
            infractionLabel = "Penalty",
        )

        Sport.ULTIMATE_FRISBEE -> SportConfig(
            onCourtSize = 7,
            squadPositions = listOf("Handler", "Cutter", "Deep"),
            defaultPeriodCount = 2,
            defaultPeriodMinutes = 20,
            scoringOptions = listOf(
                ScoreOption("Point", 1),
            ),
            infractionLabel = "Foul",
        )
    }
