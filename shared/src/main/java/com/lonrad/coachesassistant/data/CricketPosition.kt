package com.lonrad.coachesassistant.data

/**
 * The fielding positions a field plan can use. Names are cricket's own English terms, which other
 * languages use too, so they aren't translated; [code] is the short form shown on the field.
 *
 * [x] and [y] place each position on a field drawn with the striker at the top and the bowler
 * running in from the bottom, for a right-handed batter: the off side is on the left. Both run from
 * 0 to 1 across the field, whose boundary is the circle around (0.5, 0.5) with radius 0.5. For a
 * left-handed batter the field is mirrored (see [xFor]).
 */
enum class CricketPosition(val displayName: String, val code: String, val x: Float, val y: Float) {
    WICKETKEEPER("Wicketkeeper", "WK", 0.50f, 0.30f),
    BOWLER("Bowler", "B", 0.53f, 0.66f),
    FIRST_SLIP("First slip", "1S", 0.44f, 0.29f),
    SECOND_SLIP("Second slip", "2S", 0.39f, 0.30f),
    THIRD_SLIP("Third slip", "3S", 0.34f, 0.32f),
    GULLY("Gully", "GU", 0.28f, 0.36f),
    LEG_SLIP("Leg slip", "LS", 0.57f, 0.29f),
    SILLY_POINT("Silly point", "SP", 0.41f, 0.41f),
    SHORT_LEG("Short leg", "SL", 0.59f, 0.41f),
    POINT("Point", "PT", 0.20f, 0.43f),
    COVER("Cover", "CV", 0.22f, 0.55f),
    EXTRA_COVER("Extra cover", "XC", 0.30f, 0.63f),
    MID_OFF("Mid-off", "MO", 0.40f, 0.72f),
    MID_ON("Mid-on", "MN", 0.60f, 0.72f),
    MID_WICKET("Mid-wicket", "MW", 0.76f, 0.57f),
    SQUARE_LEG("Square leg", "SQ", 0.79f, 0.43f),
    SHORT_FINE_LEG("Short fine leg", "SF", 0.64f, 0.27f),
    FINE_LEG("Fine leg", "FL", 0.70f, 0.09f),
    THIRD_MAN("Third man", "TM", 0.30f, 0.09f),
    DEEP_POINT("Deep point", "DP", 0.04f, 0.40f),
    DEEP_COVER("Deep cover", "DC", 0.09f, 0.68f),
    LONG_OFF("Long-off", "LO", 0.36f, 0.95f),
    LONG_ON("Long-on", "LN", 0.64f, 0.95f),
    DEEP_MID_WICKET("Deep mid-wicket", "DM", 0.91f, 0.68f),
    DEEP_SQUARE_LEG("Deep square leg", "DS", 0.96f, 0.40f),
    ;

    /** Across-the-field position for a right- or left-handed batter (a left-hander's field is mirrored). */
    fun xFor(leftHandedBatter: Boolean): Float = if (leftHandedBatter) 1f - x else x

    companion object {
        fun fromRaw(raw: String): CricketPosition? = entries.firstOrNull { it.name == raw }
    }
}
