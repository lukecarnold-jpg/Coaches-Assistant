package com.example.basketballrotation.ui

import com.example.basketballrotation.data.Sport

/** A scoring action available while tracking a game, e.g. "Try" for +5. */
data class ScoreOption(val label: String, val points: Int)

/** The rotation/scoring rules a sport's game screen is configured with. */
data class SportConfig(
    val onCourtSize: Int,
    val squadPositions: List<String>,
    val useQuartersDefault: Boolean,
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
            useQuartersDefault = true,
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
            useQuartersDefault = true,
            defaultPeriodMinutes = 15,
            scoringOptions = listOf(
                ScoreOption("Goal", 1),
            ),
            infractionLabel = "Penalty",
        )

        Sport.SOCCER -> SportConfig(
            onCourtSize = 11,
            squadPositions = listOf("GK", "DEF", "MID", "FWD"),
            useQuartersDefault = false,
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
            useQuartersDefault = true,
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
            useQuartersDefault = false,
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
            useQuartersDefault = false,
            defaultPeriodMinutes = 40,
            scoringOptions = listOf(
                ScoreOption("Try", 4),
                ScoreOption("Conversion", 2),
                ScoreOption("Penalty", 2),
                ScoreOption("Field Goal", 1),
            ),
            infractionLabel = "Penalty",
        )
    }
