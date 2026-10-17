package com.lonrad.coachesassistant.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ReadTextUpToTest {

    @Test
    fun readsTheWholeTextWhenItFitsTheLimit() {
        assertEquals("héllo", ByteArrayInputStream("héllo".toByteArray()).readTextUpTo(limit = 100))
    }

    @Test
    fun aStreamLongerThanTheLimit_isRefused() {
        try {
            ByteArrayInputStream(ByteArray(10_000) { 'a'.code.toByte() }).readTextUpTo(limit = 9_999)
            fail("Expected UserFacingException")
        } catch (e: UserFacingException) {
            assertEquals(com.lonrad.coachesassistant.shared.R.string.file_too_large, e.messageRes)
        }
    }
}
