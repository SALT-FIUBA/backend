package io.kauth.service.notification

import io.kauth.service.notification.Notification.Event
import io.kauth.service.notification.Notification.State
import io.kauth.service.notification.Notification.Status
import io.kauth.service.notification.Notification.Channel
import io.kauth.service.notification.Notification.eventReducer
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class NotificationReducerTest {
    @Test
    fun `eventReducer applies NotificationSent event correctly`() {
        val now = Clock.System.now()
        val state = State(
            resource = UUID.randomUUID().toString(),
            channel = Channel.email,
            recipient = "user@example.com",
            content = "Hello!",
            sender = "noreply@example.com",
            status = Status.pending,
            createdAt = now
        )
        val event = Event.NotificationSent(sentAt = now)
        val newState = eventReducer.run(state, event)
        assertNotNull(newState)
        assertEquals(Status.sent, newState!!.status)
        assertEquals(now, newState.sentAt)
        assertNull(newState.error)
    }

    @Test
    fun `eventReducer applies NotificationFailed event correctly`() {
        val now = Clock.System.now()
        val state = State(
            resource = UUID.randomUUID().toString(),
            channel = Channel.email,
            recipient = "user@example.com",
            content = "Hello!",
            sender = "noreply@example.com",
            status = Status.pending,
            createdAt = now
        )
        val event = Event.NotificationFailed(error = "SMTP error")
        val newState = eventReducer.run(state, event)
        assertNotNull(newState)
        assertEquals(Status.failed, newState!!.status)
        assertEquals("SMTP error", newState.error)
    }

}

