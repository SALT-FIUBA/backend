package io.kauth.service.train

import io.kauth.service.train.Train.State
import io.kauth.service.train.Train.Command
import io.kauth.service.train.Train.Event
import io.kauth.service.train.Train.commandStateMachine
import io.kauth.service.train.Train.eventReducer
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Failure
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class TrainTest {
    private fun sampleState(deleted: Boolean = false): State = State(
        organism = UUID.randomUUID(),
        seriesNumber = "SN-001",
        name = "Test Train",
        description = "desc",
        createdBy = "user1",
        createdAt = Clock.System.now(),
        deleted = deleted
    )

    @Test
    fun `delete train happy path emits event and sets deleted`() {
        val state = sampleState(deleted = false)
        val cmd = Command.DeleteTrain(deletedBy = "admin", deletedAt = Clock.System.now())
        val (events, output) = commandStateMachine.run(cmd, state)
        assertEquals(Ok, output)
        assertTrue(events.any { it is Event.TrainDeleted })
        val deletedEvent = events.filterIsInstance<Event.TrainDeleted>().first()
        val newState = eventReducer.run(state, deletedEvent)
        assertTrue(newState?.deleted == true)
    }

    @Test
    fun `delete train fails if already deleted`() {
        val state = sampleState(deleted = true)
        val cmd = Command.DeleteTrain(deletedBy = "admin", deletedAt = Clock.System.now())
        val (events, output) = commandStateMachine.run(cmd, state)
        assertTrue(output is Failure)
        assertTrue(events.any { it is Train.Error.InvalidCommand })
    }

    @Test
    fun `delete train fails if not exists`() {
        val cmd = Command.DeleteTrain(deletedBy = "admin", deletedAt = Clock.System.now())
        val (events, output) = commandStateMachine.run(cmd, null)
        assertTrue(output is Failure)
        assertTrue(events.any { it is Train.Error.InvalidCommand })
    }

    @Test
    fun `reducer sets deleted to true`() {
        val state = sampleState(deleted = false)
        val event = Event.TrainDeleted(deletedBy = "admin", deletedAt = Clock.System.now())
        val newState = eventReducer.run(state, event)
        assertTrue(newState?.deleted == true)
    }
}
