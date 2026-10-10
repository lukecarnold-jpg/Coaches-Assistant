package com.lukearnold.coachesassistant.ui

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageQueueTest {

    @Test
    fun messagesSentWithNoOneCollecting_areShownOnceSomeoneDoes() = runBlocking {
        val queue = MessageQueue()
        // e.g. mid-rotation, between the old screen and the new one
        queue.tryEmit(UiMessage(UiText.Plain("Player added.")))
        queue.tryEmit(UiMessage(UiText.Plain("Lineup saved.")))

        val shown = queue.messages.take(2).toList().map { it.text }

        assertEquals(listOf(UiText.Plain("Player added."), UiText.Plain("Lineup saved.")), shown)
    }

    @Test
    fun eachMessageIsShownOnlyOnce() = runBlocking {
        val queue = MessageQueue()
        queue.tryEmit(UiMessage(UiText.Plain("Undone.")))
        queue.messages.first()
        queue.tryEmit(UiMessage(UiText.Plain("Game created.")))

        assertEquals(UiText.Plain("Game created."), queue.messages.first().text)
    }
}
