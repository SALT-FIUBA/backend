package io.kauth.service.notification

import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.isFailure
import io.kauth.service.notification.Notification.Command
import io.kauth.service.notification.Notification.Event
import io.kauth.service.notification.Notification.State
import io.kauth.service.notification.Notification.Status
import io.kauth.service.notification.Notification.Channel
import io.kauth.service.notification.Notification.commandStateMachine
import io.kauth.service.notification.Notification.eventReducer
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class NotificationTest {
    @Test
    fun `send notification emits SendNotificationEvent and NotificationSent`() {
        val now = Clock.System.now()
        val cmd = Command.SendNotification(
            channel = Channel.email,
            recipient = "user@example.com",
            content = "Hello!",
            sender = "noreply@example.com",
            createdAt = now
        )
        val (events, output) = commandStateMachine.run(cmd, null)
        assertEquals(Ok, output)
        assertEquals(1, events.size)
        assertTrue(events[0] is Event.SendNotificationEvent)
        val sendEvent = events[0] as Event.SendNotificationEvent
        assertEquals(Channel.email, sendEvent.channel)
        assertEquals("user@example.com", sendEvent.recipient)
        assertEquals("Hello!", sendEvent.content)
        assertEquals("noreply@example.com", sendEvent.sender)
        assertEquals(now, sendEvent.createdAt)
    }

    @Test
    fun `send notification fails with missing fields`() {
        val now = Clock.System.now()
        val cmd = Command.SendNotification(
            channel = Channel.email,
            recipient = "",
            content = "",
            sender = "",
            createdAt = now
        )
        val (events, output) = commandStateMachine.run(cmd, null)
        assertTrue(events.any { it is Notification.Error.InvalidCommand })
        assertTrue(output.isFailure)
    }

    @Test
    fun `send result success emits NotificationSent`() {
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
        val cmd = Command.SendResult(sentAt = now, success = true)
        val (events, output) = commandStateMachine.run(cmd, state)
        assertEquals(Ok, output)
        assertEquals(1, events.size)
        assertTrue(events[0] is Event.NotificationSent)
        val sent = events[0] as Event.NotificationSent
        assertEquals(now, sent.sentAt)
    }

    @Test
    fun `send result failure emits NotificationFailed`() {
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
        val cmd = Command.SendResult(sentAt = null, success = false, error = "SMTP error")
        val (events, output) = commandStateMachine.run(cmd, state)
        assertEquals(Ok, output)
        assertEquals(1, events.size)
        assertTrue(events[0] is Event.NotificationFailed)
        val failed = events[0] as Event.NotificationFailed
        assertEquals("SMTP error", failed.error)
    }

    @Test
    fun `send result fails if notification does not exist`() {
        val now = Clock.System.now()
        val cmd = Command.SendResult(sentAt = now, success = true)
        val (events, output) = commandStateMachine.run(cmd, null)
        assertTrue(events.any { it is Notification.Error.InvalidCommand })
        assertTrue(output.isFailure)
    }

    @Test
    fun `eventReducer applies NotificationSent and NotificationFailed`() {
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
        val sentEvent = Event.NotificationSent(sentAt = now)
        val sentState = eventReducer.run(state, sentEvent)
        assertEquals(Status.sent, sentState!!.status)
        assertEquals(now, sentState.sentAt)
        val failedEvent = Event.NotificationFailed(error = "fail")
        val failedState = eventReducer.run(state, failedEvent)
        assertEquals(Status.failed, failedState!!.status)
        assertEquals("fail", failedState.error)
    }
}

