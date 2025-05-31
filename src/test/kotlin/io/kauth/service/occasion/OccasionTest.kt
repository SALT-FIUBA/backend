package io.kauth.service.occasion

import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.isFailure
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID
import io.kauth.service.occasion.Occasion.Command
import io.kauth.service.occasion.Occasion.Event
import io.kauth.service.occasion.Occasion.Error
import io.kauth.service.occasion.Occasion.State
import io.kauth.service.occasion.Occasion.Category
import io.kauth.service.occasion.Occasion.commandStateMachine
import kotlin.time.Duration.Companion.seconds

class OccasionTest {
    @Test
    fun `create occasion should produce OccasionCreated event`() {
        val now = Clock.System.now()
        val fanPageId = UUID.randomUUID()
        val name = "Occasion1"
        val description = "A great event"
        val resource = "res1"
        val startDateTime = now.plus(10000.seconds)
        val endDateTime = startDateTime.plus(3600.seconds)
        val categories = listOf(Category("VIP", 10))
        val cmd = Command.CreateOccasion(resource, fanPageId, name, description, categories, startDateTime, endDateTime, now, "creator", "location1")
        val (events, output) = Occasion.handleCreate.run(cmd, null)
        assertTrue(output == Ok)
        assertEquals(1, events.size)
        val created = events[0] as Event.OccasionCreated
        assertEquals(name, created.occasion.name)
        assertEquals(description, created.occasion.description)
        assertEquals(fanPageId, created.occasion.fanPageId)
        assertEquals(resource, created.occasion.resource)
        assertEquals(startDateTime, created.occasion.startDateTime)
        assertEquals(endDateTime, created.occasion.endDateTime)
        assertEquals(categories[0].name, created.occasion.categories[0].name)
        assertEquals(categories[0].capacity, created.occasion.categories[0].capacity)
        assertEquals(false, created.occasion.disabled)
        assertEquals(now, created.occasion.createdAt)
        assertEquals("location1", created.occasion.location)
    }

    @Test
    fun `cannot create occasion if already exists`() {
        val now = Clock.System.now()
        val fanPageId = UUID.randomUUID()
        val state = State(
            status = Occasion.Status.open,
            fanPageId = fanPageId,
            name = "Occasion1",
            description = "desc",
            resource = "res1",
            startDateTime = now,
            endDateTime = now,
            categories = emptyList(),
            disabled = false,
            createdAt = now,
            location = null
        )
        val cmd = Command.CreateOccasion("res1", fanPageId, "Occasion1", "desc", listOf(Category("VIP", 10)), now, now, now, "creator", null)
        val (events, output) = Occasion.handleCreate.run(cmd, state)
        assertTrue(events.any { it is Error.OccasionAlreadyExists })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot create occasion with empty description`() {
        val now = Clock.System.now()
        val fanPageId = UUID.randomUUID()
        val cmd = Command.CreateOccasion("res1", fanPageId, "Occasion1", "", listOf(Category("VIP", 10)), now.plus(10000.seconds), now.plus(20000.seconds), now, "creator", null)
        val (events, output) = Occasion.handleCreate.run(cmd, null)
        assertTrue(events.any { it is Error.InvalidCommand })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot create occasion with start date after end date`() {
        val now = Clock.System.now()
        val fanPageId = UUID.randomUUID()
        val start = now.plus(20000.seconds)
        val end = now.plus(10000.seconds)
        val cmd = Command.CreateOccasion("res1", fanPageId, "Occasion1", "desc", listOf(Category("VIP", 10)), start, end, now, "creator", null)
        val (events, output) = Occasion.handleCreate.run(cmd, null)
        assertTrue(events.any { it is Error.InvalidCommand })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot create occasion with empty categories`() {
        val now = Clock.System.now()
        val fanPageId = UUID.randomUUID()
        val cmd = Command.CreateOccasion("res1", fanPageId, "Occasion1", "desc", emptyList(), now.plus(10000.seconds), now.plus(20000.seconds), now, "creator", null)
        val (events, output) = Occasion.handleCreate.run(cmd, null)
        assertTrue(events.any { it is Error.InvalidCommand })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot create occasion with zero capacity category`() {
        val now = Clock.System.now()
        val fanPageId = UUID.randomUUID()
        val cmd = Command.CreateOccasion("res1", fanPageId, "Occasion1", "desc", listOf(Category("VIP", 0)), now.plus(10000.seconds), now.plus(2000.seconds), now, "creator", null)
        val (events, output) = Occasion.handleCreate.run(cmd, null)
        assertTrue(events.any { it is Error.InvalidCommand })
        assertTrue(output.isFailure)
    }
}

