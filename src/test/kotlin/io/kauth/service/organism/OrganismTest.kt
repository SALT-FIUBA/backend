package io.kauth.service.organism

import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Failure
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class OrganismTest {
    private fun sampleUserInfo(): Organism.UserInfo = Organism.UserInfo(
        id = UUID.randomUUID(),
        addedBy = UUID.randomUUID(),
        addedAt = Clock.System.now()
    )

    private fun sampleState(deleted: Boolean = false): Organism.State = Organism.State(
        tag = "tag1",
        name = "Test Organism",
        description = "desc",
        createdBy = "user1",
        createdAt = Clock.System.now(),
        supervisors = listOf(sampleUserInfo()),
        operators = listOf(sampleUserInfo()),
        deleted = deleted
    )

    @Test
    fun `delete organism happy path emits event and sets deleted`() {
        val state = sampleState(deleted = false)
        val cmd = Organism.Command.DeleteOrganism(deletedBy = "admin", deletedAt = Clock.System.now())
        val (events, output) = Organism.commandStateMachine.run(cmd, state)
        assertEquals(Ok, output)
        assertTrue(events.any { it is Organism.Event.OrganismDeleted })
        val deletedEvent = events.filterIsInstance<Organism.Event.OrganismDeleted>().first()
        val newState = Organism.eventReducer.run(state, deletedEvent)
        assertTrue(newState?.deleted == true)
    }

    @Test
    fun `delete organism fails if already deleted`() {
        val state = sampleState(deleted = true)
        val cmd = Organism.Command.DeleteOrganism(deletedBy = "admin", deletedAt = Clock.System.now())
        val (events, output) = Organism.commandStateMachine.run(cmd, state)
        assertTrue(output is Failure)
        assertTrue(events.any { it is Organism.Error.InvalidCommand })
    }

    @Test
    fun `delete organism fails if not exists`() {
        val cmd = Organism.Command.DeleteOrganism(deletedBy = "admin", deletedAt = Clock.System.now())
        val (events, output) = Organism.commandStateMachine.run(cmd, null)
        assertTrue(output is Failure)
        assertTrue(events.any { it is Organism.Error.OrganismDoesNotExists })
    }

    @Test
    fun `reducer sets deleted to true`() {
        val state = sampleState(deleted = false)
        val event = Organism.Event.OrganismDeleted(deletedBy = "admin", deletedAt = Clock.System.now())
        val newState = Organism.eventReducer.run(state, event)
        assertTrue(newState?.deleted == true)
    }

    @Test
    fun `edit organism happy path emits event and updates state`() {
        val state = sampleState(deleted = false)
        val cmd = Organism.Command.EditOrganism(
            tag = "newtag",
            name = "New Name",
            description = "New Description",
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val (events, output) = Organism.commandStateMachine.run(cmd, state)
        assertEquals(Ok, output)
        assertTrue(events.any { it is Organism.Event.OrganismEdited })
        val editedEvent = events.filterIsInstance<Organism.Event.OrganismEdited>().first()
        val newState = Organism.eventReducer.run(state, editedEvent)
        assertEquals("newtag", newState?.tag)
        assertEquals("New Name", newState?.name)
        assertEquals("New Description", newState?.description)
    }

    @Test
    fun `edit organism fails if deleted`() {
        val state = sampleState(deleted = true)
        val cmd = Organism.Command.EditOrganism(
            tag = "newtag",
            name = "New Name",
            description = "New Description",
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val (events, output) = Organism.commandStateMachine.run(cmd, state)
        assertTrue(output is Failure)
        assertTrue(events.any { it is Organism.Error.InvalidCommand })
    }

    @Test
    fun `edit organism fails if not exists`() {
        val cmd = Organism.Command.EditOrganism(
            tag = "newtag",
            name = "New Name",
            description = "New Description",
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val (events, output) = Organism.commandStateMachine.run(cmd, null)
        assertTrue(output is Failure)
        assertTrue(events.any { it is Organism.Error.OrganismDoesNotExists })
    }

    @Test
    fun `reducer updates tag name and description on OrganismEdited event`() {
        val state = sampleState(deleted = false)
        val event = Organism.Event.OrganismEdited(
            tag = "editedtag",
            name = "Edited Name",
            description = "Edited Desc",
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val newState = Organism.eventReducer.run(state, event)
        assertEquals("editedtag", newState?.tag)
        assertEquals("Edited Name", newState?.name)
        assertEquals("Edited Desc", newState?.description)
    }

    @Test
    fun `edit organism with only tag updates tag`() {
        val state = sampleState(deleted = false)
        val cmd = Organism.Command.EditOrganism(
            tag = "tag2",
            name = null,
            description = null,
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val (events, output) = Organism.commandStateMachine.run(cmd, state)
        assertEquals(Ok, output)
        val editedEvent = events.filterIsInstance<Organism.Event.OrganismEdited>().first()
        val newState = Organism.eventReducer.run(state, editedEvent)
        assertEquals("tag2", newState?.tag)
        assertEquals(state.name, newState?.name)
        assertEquals(state.description, newState?.description)
    }

    @Test
    fun `edit organism with only name updates name`() {
        val state = sampleState(deleted = false)
        val cmd = Organism.Command.EditOrganism(
            tag = null,
            name = "Changed Name",
            description = null,
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val (events, output) = Organism.commandStateMachine.run(cmd, state)
        assertEquals(Ok, output)
        val editedEvent = events.filterIsInstance<Organism.Event.OrganismEdited>().first()
        val newState = Organism.eventReducer.run(state, editedEvent)
        assertEquals(state.tag, newState?.tag)
        assertEquals("Changed Name", newState?.name)
        assertEquals(state.description, newState?.description)
    }

    @Test
    fun `edit organism with only description updates description`() {
        val state = sampleState(deleted = false)
        val cmd = Organism.Command.EditOrganism(
            tag = null,
            name = null,
            description = "Changed Desc",
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val (events, output) = Organism.commandStateMachine.run(cmd, state)
        assertEquals(Ok, output)
        val editedEvent = events.filterIsInstance<Organism.Event.OrganismEdited>().first()
        val newState = Organism.eventReducer.run(state, editedEvent)
        assertEquals(state.tag, newState?.tag)
        assertEquals(state.name, newState?.name)
        assertEquals("Changed Desc", newState?.description)
    }

    @Test
    fun `edit organism fails if no changes provided`() {
        val state = sampleState(deleted = false)
        val cmd = Organism.Command.EditOrganism(
            tag = null,
            name = null,
            description = null,
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val (events, output) = Organism.commandStateMachine.run(cmd, state)
        assertTrue(output is Failure)
        assertTrue(events.any { it is Organism.Error.InvalidCommand })
    }

    @Test
    fun `edit organism fails if tag is set to empty`() {
        val state = sampleState(deleted = false)
        val cmd = Organism.Command.EditOrganism(
            tag = "",
            name = null,
            description = null,
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val (events, output) = Organism.commandStateMachine.run(cmd, state)
        assertTrue(output is Failure)
        assertTrue(events.any { it is Organism.Error.InvalidCommand })
    }

    @Test
    fun `edit organism fails if name is set to empty`() {
        val state = sampleState(deleted = false)
        val cmd = Organism.Command.EditOrganism(
            tag = null,
            name = "",
            description = null,
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val (events, output) = Organism.commandStateMachine.run(cmd, state)
        assertTrue(output is Failure)
        assertTrue(events.any { it is Organism.Error.InvalidCommand })
    }

    @Test
    fun `edit organism fails if description is set to empty`() {
        val state = sampleState(deleted = false)
        val cmd = Organism.Command.EditOrganism(
            tag = null,
            name = null,
            description = "",
            editedBy = "admin",
            editedAt = Clock.System.now()
        )
        val (events, output) = Organism.commandStateMachine.run(cmd, state)
        assertTrue(output is Failure)
        assertTrue(events.any { it is Organism.Error.InvalidCommand })
    }
}
