package com.lukearnold.coachesassistant.data

/** Sports the app can be used to coach. */
enum class Sport(val displayName: String, val emoji: String) {
    BASKETBALL("Basketball", "🏀"),
    NETBALL("Netball", "🏐"),
    SOCCER("Soccer", "⚽"),
    AFL("AFL", "🏈"),
    RUGBY_UNION("Rugby Union", "🏉"),
    RUGBY_LEAGUE("Rugby League", "🏉"),
    FUTSAL("Futsal", "⚽"),
    FIELD_HOCKEY("Field Hockey", "🏑"),
    ICE_HOCKEY("Ice Hockey", "🏒"),
    TOUCH_FOOTBALL("Touch Football", "🏉"),
    WATER_POLO("Water Polo", "🤽"),
    HANDBALL("Handball", "🤾"),
    LACROSSE("Lacrosse", "🥍"),
    GAELIC_FOOTBALL("Gaelic Football", "🏐"),
    FLAG_FOOTBALL("Flag Football", "🏈"),
    ULTIMATE_FRISBEE("Ultimate Frisbee", "🥏"),
}
