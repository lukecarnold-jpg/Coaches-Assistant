package com.lukearnold.coachesassistant.data

import org.json.JSONException
import org.json.JSONObject

/**
 * Serializes the per-player MVP vote tally stored in [GameEntity.mvpVotesJson].
 *
 * New data is written as real JSON (`{"1":2,"3":4}`). [parse] also understands the legacy,
 * hand-rolled format (`{1:2,3:4}`) used by earlier app versions so existing saved games keep
 * working.
 */
object MvpVotes {
    fun parse(json: String): Map<Long, Int> {
        if (json.isBlank()) return emptyMap()

        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associate { key -> key.toLong() to obj.getInt(key) }
        } catch (_: JSONException) {
            parseLegacy(json)
        } catch (_: NumberFormatException) {
            parseLegacy(json)
        }
    }

    fun serialize(votes: Map<Long, Int>): String {
        val obj = JSONObject()
        votes.forEach { (playerId, count) -> obj.put(playerId.toString(), count) }
        return obj.toString()
    }

    private fun parseLegacy(json: String): Map<Long, Int> {
        return try {
            val body = json.removeSurrounding("{", "}")
            if (body.isBlank()) return emptyMap()

            body.split(",").mapNotNull { pair ->
                val parts = pair.split(":")
                if (parts.size == 2) {
                    parts[0].trim().toLong() to parts[1].trim().toInt()
                } else {
                    null
                }
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
