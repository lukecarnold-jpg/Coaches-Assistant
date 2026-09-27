package com.lukearnold.coachesassistant.ai

import com.lukearnold.coachesassistant.data.Sport

/** Same instruction works across providers, since parsing keys off the literal labels below. */
fun buildDrillPrompt(sport: Sport, ageGroup: String): String =
    "Generate exactly 3 age-appropriate ${sport.promptName()} drills for $ageGroup players. " +
        "Format each drill with a 'TITLE:', 'DESCRIPTION:', and 'TEACHING POINTS:' on new lines. " +
        "Keep descriptions concise and appropriate for the age group."

fun parseDrills(text: String): List<Drill> {
    val drills = mutableListOf<Drill>()
    val blocks = text.split(Regex("TITLE:", RegexOption.IGNORE_CASE))

    blocks.forEach { block ->
        if (block.isBlank()) return@forEach

        val lines = block.trim().lines()
        val title = lines.firstOrNull()?.trim() ?: ""

        val descriptionStart = block.indexOf("DESCRIPTION:", ignoreCase = true)
        val teachingPointsStart = block.indexOf("TEACHING POINTS:", ignoreCase = true)

        if ((descriptionStart != -1) && (teachingPointsStart != -1)) {
            val description = block.substring(descriptionStart + 12, teachingPointsStart).trim()
            val teachingPoints = block.substring(teachingPointsStart + 16).trim()

            if (title.isNotEmpty() && description.isNotEmpty()) {
                drills.add(Drill(title, description, teachingPoints))
            }
        }
    }
    return drills.take(3)
}

/**
 * How to name [this] sport to a model, in English like the rest of the prompt: its name, spelled
 * out where it's ambiguous.
 */
private fun Sport.promptName(): String = when (this) {
    Sport.AFL -> "Australian rules football (AFL)"
    Sport.GAELIC_FOOTBALL -> "Gaelic football"
    Sport.TOUCH_FOOTBALL -> "touch football (touch rugby)"
    Sport.FLAG_FOOTBALL -> "flag football"
    else -> name.lowercase().replace('_', ' ')
}

