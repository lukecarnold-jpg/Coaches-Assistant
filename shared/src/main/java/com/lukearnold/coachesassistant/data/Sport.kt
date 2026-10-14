package com.lukearnold.coachesassistant.data

import androidx.annotation.StringRes
import com.lukearnold.coachesassistant.shared.R

/** Sports the app can be used to coach. */
enum class Sport(@param:StringRes val label: Int, val emoji: String) {
    BASKETBALL(R.string.sport_basketball, "🏀"),
    NETBALL(R.string.sport_netball, "🏐"),
    SOCCER(R.string.sport_soccer, "⚽"),
    AFL(R.string.sport_afl, "🏈"),
    RUGBY_UNION(R.string.sport_rugby_union, "🏉"),
    RUGBY_LEAGUE(R.string.sport_rugby_league, "🏉"),
    FUTSAL(R.string.sport_futsal, "⚽"),
    FIELD_HOCKEY(R.string.sport_field_hockey, "🏑"),
    ICE_HOCKEY(R.string.sport_ice_hockey, "🏒"),
    TOUCH_FOOTBALL(R.string.sport_touch_football, "🏉"),
    WATER_POLO(R.string.sport_water_polo, "🤽"),
    HANDBALL(R.string.sport_handball, "🤾"),
    LACROSSE(R.string.sport_lacrosse, "🥍"),
    GAELIC_FOOTBALL(R.string.sport_gaelic_football, "🏐"),
    FLAG_FOOTBALL(R.string.sport_flag_football, "🏈"),
    ULTIMATE_FRISBEE(R.string.sport_ultimate_frisbee, "🥏"),
    CRICKET(R.string.sport_cricket, "🏏"),
    ;

    /**
     * Whether games are recorded as a scorecard per player (see CricketScorecardEntity) instead of
     * live with a clock, periods, and substitutions.
     */
    val usesScorecards: Boolean get() = this == CRICKET
}
