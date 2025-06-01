package io.kauth.service.accessrequest

import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.isFailure
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlinx.datetime.Clock
import java.util.UUID
import io.kauth.service.accessrequest.AccessRequest.Command
import io.kauth.service.accessrequest.AccessRequest.Event
import io.kauth.service.accessrequest.AccessRequest.commandHandler
import io.kauth.service.accessrequest.AccessRequest.State

class AccessRequestTest {
    @Test
    fun `create request should produce RequestCreated and PendingAccept events`() {
        val occasionId = UUID.randomUUID()
        val userId = "user1"
        val categoryName = "cat"
        val description = "desc"
        val places = 2
        val now = Clock.System.now()
        val cmd = Command.CreateRequest(occasionId, categoryName, userId, now, description, places)
        val (events, output) = commandHandler.run(cmd, null)
        assertTrue(output == Ok)
        assertEquals(2, events.size)
        val expectedCreated = Event.RequestCreated(
            occasionId = occasionId,
            userId = userId,
            createdAt = now,
            categoryName = categoryName,
            description = description,
            places = places
        )
        val expectedPendingAccept = Event.RequestPendingAccept(
            acceptedAt = now,
            acceptedBy = "automatic"
        )
        assertEquals(expectedCreated, events[0])
        assertEquals(expectedPendingAccept, events[1])
    }

    @Test
    fun `accept request should transition from Pending to PendingAccept`() {
        val now = Clock.System.now()
        val state = State(UUID.randomUUID(), "cat", "desc", now, "user1", AccessRequest.Status.Pending, 1)
        val cmd = Command.AcceptRequest(now, "admin")
        val (events, output) = commandHandler.run(cmd, state)
        assertTrue(output == Ok)
        assertEquals(1, events.size)
        val expected = Event.RequestPendingAccept(
            acceptedAt = now,
            acceptedBy = "admin"
        )
        assertEquals(expected, events[0])
    }

    @Test
    fun `accept request result should transition from PendingAccept to Accepted`() {
        val now = Clock.System.now()
        val state = State(UUID.randomUUID(), "cat", "desc", now, "user1", AccessRequest.Status.PendingAccept(now, "admin"), 1)
        val cmd = Command.AcceptRequestResult(true, "ok", now)
        val (events, output) = commandHandler.run(cmd, state)
        assertTrue(output == Ok)
        assertEquals(1, events.size)
        val expected = Event.RequestAccepted(
            acceptedAt = now,
            reason = "ok"
        )
        assertEquals(expected, events[0])
    }

    @Test
    fun `reject request result should transition from PendingAccept to Rejected`() {
        val now = Clock.System.now()
        val state = State(UUID.randomUUID(), "cat", "desc", now, "user1", AccessRequest.Status.PendingAccept(now, "admin"), 1)
        val cmd = Command.AcceptRequestResult(false, "not allowed", now)
        val (events, output) = commandHandler.run(cmd, state)
        assertTrue(output == Ok)
        assertEquals(1, events.size)
        val expected = Event.RequestRejected(
            rejectedAt = now,
            reason = "not allowed"
        )
        assertEquals(expected, events[0])
    }

    @Test
    fun `confirm request should transition from Accepted to PendingConfirmation`() {
        val now = Clock.System.now()
        val state = State(UUID.randomUUID(), "cat", "desc", now, "user1", AccessRequest.Status.Accepted(now, "admin"), 1)
        val cmd = Command.ConfirmRequest(now, "admin")
        val (events, output) = commandHandler.run(cmd, state)
        assertTrue(output == Ok)
        assertEquals(1, events.size)
        val expected = Event.RequestPendingConfirmation(
            confirmedAt = now,
            confirmedBy = "admin"
        )
        assertEquals(expected, events[0])
    }

    @Test
    fun `confirm request result should transition from PendingConfirmation to Confirmed`() {
        val now = Clock.System.now()
        val state = State(UUID.randomUUID(), "cat", "desc", now, "user1", AccessRequest.Status.PendingConfirmation(now, "admin"), 1)
        val cmd = Command.ConfirmRequestResult(true, "confirmed", now)
        val (events, output) = commandHandler.run(cmd, state)
        assertTrue(output == Ok)
        assertEquals(1, events.size)
        val expected = Event.RequestConfirmed(
            confirmedAt = now,
            reason = "confirmed"
        )
        assertEquals(expected, events[0])
    }

    @Test
    fun `confirm request result should transition from PendingConfirmation to Rejected`() {
        val now = Clock.System.now()
        val state = State(UUID.randomUUID(), "cat", "desc", now, "user1", AccessRequest.Status.PendingConfirmation(now, "admin"), 1)
        val cmd = Command.ConfirmRequestResult(false, "not confirmed", now)
        val (events, output) = commandHandler.run(cmd, state)
        assertTrue(output == Ok)
        assertEquals(1, events.size)
        val expected = Event.RequestRejected(
            rejectedAt = now,
            reason = "not confirmed"
        )
        assertEquals(expected, events[0])
    }

    @Test
    fun `cannot create request if already exists`() {
        val now = Clock.System.now()
        val occasionId = UUID.randomUUID()
        val state = State(occasionId, "cat", "desc", now, "user1", AccessRequest.Status.Pending, 1)
        val cmd = Command.CreateRequest(occasionId, "cat", "user1", now, "desc", 1)
        val (events, output) = commandHandler.run(cmd, state)
        assertTrue(events.any { it is AccessRequest.Error.RequestAlreadyExists })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot accept request if not Pending`() {
        val now = Clock.System.now()
        val state = State(UUID.randomUUID(), "cat", "desc", now, "user1", AccessRequest.Status.Accepted(now, "admin"), 1)
        val cmd = Command.AcceptRequest(now, "admin")
        val (events, output) = commandHandler.run(cmd, state)
        assertTrue(events.any { it is AccessRequest.Error.InvalidTransition })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot accept request if state is null`() {
        val now = Clock.System.now()
        val cmd = Command.AcceptRequest(now, "admin")
        val (events, output) = commandHandler.run(cmd, null)
        assertTrue(events.any { it is AccessRequest.Error.RequestNotFound })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot accept result if not PendingAccept`() {
        val now = Clock.System.now()
        val state = State(UUID.randomUUID(), "cat", "desc", now, "user1", AccessRequest.Status.Pending, 1)
        val cmd = Command.AcceptRequestResult(true, "ok", now)
        val (events, output) = commandHandler.run(cmd, state)
        assertTrue(events.any { it is AccessRequest.Error.InvalidTransition })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot accept result if state is null`() {
        val now = Clock.System.now()
        val cmd = Command.AcceptRequestResult(true, "ok", now)
        val (events, output) = commandHandler.run(cmd, null)
        assertTrue(events.any { it is AccessRequest.Error.RequestNotFound })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot confirm request if not Accepted`() {
        val now = Clock.System.now()
        val state = State(UUID.randomUUID(), "cat", "desc", now, "user1", AccessRequest.Status.Pending, 1)
        val cmd = Command.ConfirmRequest(now, "admin")
        val (events, output) = commandHandler.run(cmd, state)
        assertTrue(events.any { it is AccessRequest.Error.InvalidTransition })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot confirm request if state is null`() {
        val now = Clock.System.now()
        val cmd = Command.ConfirmRequest(now, "admin")
        val (events, output) = commandHandler.run(cmd, null)
        assertTrue(events.any { it is AccessRequest.Error.RequestNotFound })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot confirm result if not PendingConfirmation`() {
        val now = Clock.System.now()
        val state = State(UUID.randomUUID(), "cat", "desc", now, "user1", AccessRequest.Status.Accepted(now, "admin"), 1)
        val cmd = Command.ConfirmRequestResult(true, "ok", now)
        val (events, output) = commandHandler.run(cmd, state)
        assertTrue(events.any { it is AccessRequest.Error.InvalidTransition })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot confirm result if state is null`() {
        val now = Clock.System.now()
        val cmd = Command.ConfirmRequestResult(true, "ok", now)
        val (events, output) = commandHandler.run(cmd, null)
        assertTrue(events.any { it is AccessRequest.Error.RequestNotFound })
        assertTrue(output.isFailure)
    }

    @Test
    fun `reducer applies all events correctly`() {
        val now = Clock.System.now()
        val occasionId = UUID.randomUUID()
        val userId = "user1"
        val categoryName = "cat"
        val description = "desc"
        val places = 2
        // RequestCreated
        val created = AccessRequest.Event.RequestCreated(occasionId, userId, now, categoryName, description, places)
        val stateCreated = AccessRequest.reducer.run(null, created)
        assertNotNull(stateCreated)
        assertEquals(occasionId, stateCreated!!.occasionId)
        assertEquals(userId, stateCreated.userId)
        assertEquals(AccessRequest.Status.Pending, stateCreated.status)
        // RequestPendingAccept
        val pendingAccept = AccessRequest.Event.RequestPendingAccept(now, "admin")
        val statePendingAccept = AccessRequest.reducer.run(stateCreated, pendingAccept)
        assertTrue(statePendingAccept!!.status is AccessRequest.Status.PendingAccept)
        // RequestAccepted
        val accepted = AccessRequest.Event.RequestAccepted(now, "ok")
        val stateAccepted = AccessRequest.reducer.run(statePendingAccept, accepted)
        assertTrue(stateAccepted!!.status is AccessRequest.Status.Accepted)
        // RequestPendingConfirmation
        val pendingConfirmation = AccessRequest.Event.RequestPendingConfirmation(now, "admin")
        val statePendingConfirmation = AccessRequest.reducer.run(stateAccepted, pendingConfirmation)
        assertTrue(statePendingConfirmation!!.status is AccessRequest.Status.PendingConfirmation)
        // RequestConfirmed
        val confirmed = AccessRequest.Event.RequestConfirmed(now, "confirmed")
        val stateConfirmed = AccessRequest.reducer.run(statePendingConfirmation, confirmed)
        assertTrue(stateConfirmed!!.status is AccessRequest.Status.Confirmed)
        // RequestRejected
        val rejected = AccessRequest.Event.RequestRejected(now, "rejected")
        val stateRejected = AccessRequest.reducer.run(stateConfirmed, rejected)
        assertTrue(stateRejected!!.status is AccessRequest.Status.Rejected)
        // Error event should not change state
        val error = AccessRequest.Error.InvalidTransition("err")
        val stateAfterError = AccessRequest.reducer.run(stateRejected, error)
        assertEquals(stateRejected, stateAfterError)
    }

    @Test
    fun `can cancel a pending request`() {
        val now = Clock.System.now()
        val state = State(UUID.randomUUID(), "cat", "desc", now, "user1", AccessRequest.Status.Pending, 1)
        val cmd = Command.CancelRequest(now, "user1")
        val (events, output) = commandHandler.run(cmd, state)
        assertTrue(output == Ok)
        assertEquals(1, events.size)
        val expected = Event.RequestCancelled(
            cancelledAt = now,
            cancelledBy = "user1"
        )
        assertEquals(expected, events[0])
    }

    @Test
    fun `cannot cancel a confirmed request`() {
        val now = Clock.System.now()
        val state = State(UUID.randomUUID(), "cat", "desc", now, "user1", AccessRequest.Status.Confirmed(now, "admin"), 1)
        val cmd = Command.CancelRequest(now, "user1")
        val (events, output) = commandHandler.run(cmd, state)
        assertTrue(events.any { it is AccessRequest.Error.InvalidTransition })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot cancel a rejected request`() {
        val now = Clock.System.now()
        val state = State(UUID.randomUUID(), "cat", "desc", now, "user1", AccessRequest.Status.Rejected(now, "admin"), 1)
        val cmd = Command.CancelRequest(now, "user1")
        val (events, output) = commandHandler.run(cmd, state)
        assertTrue(events.any { it is AccessRequest.Error.InvalidTransition })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot cancel if request does not exist`() {
        val now = Clock.System.now()
        val cmd = Command.CancelRequest(now, "user1")
        val (events, output) = commandHandler.run(cmd, null)
        assertTrue(events.any { it is AccessRequest.Error.RequestNotFound })
        assertTrue(output.isFailure)
    }
}
