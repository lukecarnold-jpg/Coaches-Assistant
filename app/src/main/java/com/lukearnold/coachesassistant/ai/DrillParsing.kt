package com.lukearnold.coachesassistant.ai

import com.lukearnold.coachesassistant.data.Sport
import java.util.Locale

/**
 * Same instruction works across providers, since parsing keys off the literal labels below. The
 * prompt is always English; for any other [languageTag] (the app's language, e.g. "fr") it asks
 * for the drills in that language, with the labels still in English so they can be parsed.
 */
fun buildDrillPrompt(sport: Sport, ageGroup: String, languageTag: String = "en"): String {
    val prompt = "Generate exactly 3 age-appropriate ${sport.promptName()} drills for $ageGroup players. " +
        "Format each drill with a 'TITLE:', 'DESCRIPTION:', and 'TEACHING POINTS:' on new lines. " +
        "Keep descriptions concise and appropriate for the age group."
    val language = Locale.forLanguageTag(languageTag).getDisplayLanguage(Locale.ENGLISH)
    return if (language.isBlank() || language == "English") {
        prompt
    } else {
        "$prompt Write the titles, descriptions, and teaching points in $language, but keep the labels " +
            "'TITLE:', 'DESCRIPTION:', and 'TEACHING POINTS:' in English exactly as written."
    }
}

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
