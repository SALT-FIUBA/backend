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
}

