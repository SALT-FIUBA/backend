package io.kauth.service.reservation

import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.isFailure
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import io.kauth.service.reservation.Reservation.Command
import io.kauth.service.reservation.Reservation.ResourceEvent
import io.kauth.service.reservation.Reservation.Reservation as ReservationState
import io.kauth.service.reservation.Reservation.stateMachine

class ReservationTest {
    @Test
    fun `take should produce ResourceTaken event when not taken`() {
        val ownerId = "user1"
        val cmd = Command.Take(ownerId)
        val (events, output) = stateMachine.run(cmd, null)
        assertTrue(output == Ok)
        assertEquals(1, events.size)
        val expected = ResourceEvent.ResourceTaken(ownerId)
        assertEquals(expected, events[0])
    }

    @Test
    fun `take should fail if already taken`() {
        val ownerId = "user1"
        val state = ReservationState(true, ownerId)
        val cmd = Command.Take("user2")
        val (events, output) = stateMachine.run(cmd, state)
        assertTrue(output.isFailure)
    }

    @Test
    fun `release should produce ResourceReleased event when taken`() {
        val ownerId = "user1"
        val state = ReservationState(true, ownerId)
        val cmd = Command.Release
        val (events, output) = stateMachine.run(cmd, state)
        assertTrue(output == Ok)
        assertEquals(1, events.size)
        val expected = ResourceEvent.ResourceReleased
        assertEquals(expected, events[0])
    }

    @Test
    fun `release should fail if not taken`() {
        val cmd = Command.Release
        val (events, output) = stateMachine.run(cmd, null)
        assertTrue(output.isFailure)
    }
}

