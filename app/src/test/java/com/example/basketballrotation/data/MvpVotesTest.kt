package com.example.basketballrotation.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MvpVotesTest {

    @Test
    fun parse_emptyString_returnsEmptyMap() {
        assertEquals(emptyMap<Long, Int>(), MvpVotes.parse(""))
    }

    @Test
    fun parse_emptyJsonObject_returnsEmptyMap() {
        assertEquals(emptyMap<Long, Int>(), MvpVotes.parse("{}"))
    }

    @Test
    fun serialize_thenParse_roundTrips() {
        val votes = mapOf(1L to 2, 42L to 5)

        val json = MvpVotes.serialize(votes)
        val parsed = MvpVotes.parse(json)

        assertEquals(votes, parsed)
    }

    @Test
    fun parse_legacyUnquotedFormat_isStillUnderstood() {
        val parsed = MvpVotes.parse("{1:2,3:4}")

        assertEquals(mapOf(1L to 2, 3L to 4), parsed)
    }

    @Test
    fun parse_malformedInput_returnsEmptyMap() {
        assertEquals(emptyMap<Long, Int>(), MvpVotes.parse("not json at all"))
    }
}
