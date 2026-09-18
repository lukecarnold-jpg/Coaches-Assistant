package com.example.basketballrotation.ai

/** Same instruction works across providers, since parsing keys off the literal labels below. */
fun buildDrillPrompt(ageGroup: String): String =
    "Generate exactly 3 age-appropriate basketball drills for $ageGroup players. " +
        "Format each drill with a 'TITLE:', 'DESCRIPTION:', and 'TEACHING POINTS:' on new lines. " +
        "Keep descriptions concise and appropriate for the age group."

fun parseDrills(text: String): List<BasketballDrill> {
    val drills = mutableListOf<BasketballDrill>()
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
                drills.add(BasketballDrill(title, description, teachingPoints))
            }
        }
    }
    return drills.take(3)
}
