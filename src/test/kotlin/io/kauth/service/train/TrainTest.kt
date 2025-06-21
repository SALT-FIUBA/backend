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

    @Test
    fun `edit train happy path emits event and updates state`() {
        val state = sampleState(deleted = false)
        val cmd = Command.EditTrain(
            seriesNumber = "SN-002",
            name = "New Name",
            description = "New Description",
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val (events, output) = commandStateMachine.run(cmd, state)
        assertEquals(Ok, output)
        assertTrue(events.any { it is Event.TrainEdited })
        val editedEvent = events.filterIsInstance<Event.TrainEdited>().first()
        val newState = eventReducer.run(state, editedEvent)
        assertEquals("SN-002", newState?.seriesNumber)
        assertEquals("New Name", newState?.name)
        assertEquals("New Description", newState?.description)
    }

    @Test
    fun `edit train fails if deleted`() {
        val state = sampleState(deleted = true)
        val cmd = Command.EditTrain(
            seriesNumber = "SN-002",
            name = "New Name",
            description = "New Description",
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val (events, output) = commandStateMachine.run(cmd, state)
        assertTrue(output is Failure)
        assertTrue(events.any { it is Train.Error.InvalidCommand })
    }

    @Test
    fun `edit train fails if not exists`() {
        val cmd = Command.EditTrain(
            seriesNumber = "SN-002",
            name = "New Name",
            description = "New Description",
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val (events, output) = commandStateMachine.run(cmd, null)
        assertTrue(output is Failure)
        assertTrue(events.any { it is Train.Error.InvalidCommand })
    }

    @Test
    fun `reducer updates seriesNumber name and description on TrainEdited event`() {
        val state = sampleState(deleted = false)
        val event = Event.TrainEdited(
            seriesNumber = "SN-003",
            name = "Edited Name",
            description = "Edited Desc",
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val newState = eventReducer.run(state, event)
        assertEquals("SN-003", newState?.seriesNumber)
        assertEquals("Edited Name", newState?.name)
        assertEquals("Edited Desc", newState?.description)
    }

    @Test
    fun `edit train with only seriesNumber updates seriesNumber`() {
        val state = sampleState(deleted = false)
        val cmd = Command.EditTrain(
            seriesNumber = "SN-NEW",
            name = null,
            description = null,
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val (events, output) = commandStateMachine.run(cmd, state)
        assertEquals(Ok, output)
        val editedEvent = events.filterIsInstance<Event.TrainEdited>().first()
        val newState = eventReducer.run(state, editedEvent)
        assertEquals("SN-NEW", newState?.seriesNumber)
        assertEquals(state.name, newState?.name)
        assertEquals(state.description, newState?.description)
    }

    @Test
    fun `edit train with only name updates name`() {
        val state = sampleState(deleted = false)
        val cmd = Command.EditTrain(
            seriesNumber = null,
            name = "Changed Name",
            description = null,
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val (events, output) = commandStateMachine.run(cmd, state)
        assertEquals(Ok, output)
        val editedEvent = events.filterIsInstance<Event.TrainEdited>().first()
        val newState = eventReducer.run(state, editedEvent)
        assertEquals(state.seriesNumber, newState?.seriesNumber)
        assertEquals("Changed Name", newState?.name)
        assertEquals(state.description, newState?.description)
    }

    @Test
    fun `edit train with only description updates description`() {
        val state = sampleState(deleted = false)
        val cmd = Command.EditTrain(
            seriesNumber = null,
            name = null,
            description = "Changed Desc",
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val (events, output) = commandStateMachine.run(cmd, state)
        assertEquals(Ok, output)
        val editedEvent = events.filterIsInstance<Event.TrainEdited>().first()
        val newState = eventReducer.run(state, editedEvent)
        assertEquals(state.seriesNumber, newState?.seriesNumber)
        assertEquals(state.name, newState?.name)
        assertEquals("Changed Desc", newState?.description)
    }

    @Test
    fun `edit train fails if no changes provided`() {
        val state = sampleState(deleted = false)
        val cmd = Command.EditTrain(
            seriesNumber = null,
            name = null,
            description = null,
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val (events, output) = commandStateMachine.run(cmd, state)
        assertTrue(output is Failure)
        assertTrue(events.any { it is Train.Error.InvalidCommand })
    }

    @Test
    fun `edit train fails if seriesNumber is set to empty`() {
        val state = sampleState(deleted = false)
        val cmd = Command.EditTrain(
            seriesNumber = "",
            name = null,
            description = null,
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val (events, output) = commandStateMachine.run(cmd, state)
        assertTrue(output is Failure)
        assertTrue(events.any { it is Train.Error.InvalidCommand })
    }

    @Test
    fun `edit train fails if name is set to empty`() {
        val state = sampleState(deleted = false)
        val cmd = Command.EditTrain(
            seriesNumber = null,
            name = "",
            description = null,
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val (events, output) = commandStateMachine.run(cmd, state)
        assertTrue(output is Failure)
        assertTrue(events.any { it is Train.Error.InvalidCommand })
    }

    @Test
    fun `edit train fails if description is set to empty`() {
        val state = sampleState(deleted = false)
        val cmd = Command.EditTrain(
            seriesNumber = null,
            name = null,
            description = "",
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val (events, output) = commandStateMachine.run(cmd, state)
        assertTrue(output is Failure)
        assertTrue(events.any { it is Train.Error.InvalidCommand })
    }
}
