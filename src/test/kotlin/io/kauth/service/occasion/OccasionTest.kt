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
        val (events, output) = Occasion.commandStateMachine.run(cmd, null)
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
        val (events, output) = Occasion.commandStateMachine.run(cmd, state)
        assertTrue(events.any { it is Error.OccasionAlreadyExists })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot create occasion with empty description`() {
        val now = Clock.System.now()
        val fanPageId = UUID.randomUUID()
        val cmd = Command.CreateOccasion("res1", fanPageId, "Occasion1", "", listOf(Category("VIP", 10)), now.plus(10000.seconds), now.plus(20000.seconds), now, "creator", null)
        val (events, output) = Occasion.commandStateMachine.run(cmd, null)
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
        val (events, output) = Occasion.commandStateMachine.run(cmd, null)
        assertTrue(events.any { it is Error.InvalidCommand })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot create occasion with empty categories`() {
        val now = Clock.System.now()
        val fanPageId = UUID.randomUUID()
        val cmd = Command.CreateOccasion("res1", fanPageId, "Occasion1", "desc", emptyList(), now.plus(10000.seconds), now.plus(20000.seconds), now, "creator", null)
        val (events, output) = Occasion.commandStateMachine.run(cmd, null)
        assertTrue(events.any { it is Error.InvalidCommand })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot create occasion with zero capacity category`() {
        val now = Clock.System.now()
        val fanPageId = UUID.randomUUID()
        val cmd = Command.CreateOccasion("res1", fanPageId, "Occasion1", "desc", listOf(Category("VIP", 0)), now.plus(10000.seconds), now.plus(2000.seconds), now, "creator", null)
        val (events, output) = Occasion.commandStateMachine.run(cmd, null)
        assertTrue(events.any { it is Error.InvalidCommand })
        assertTrue(output.isFailure)
    }

    @Test
    fun `can change visibility of an occasion`() {
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
            categories = listOf(Category("VIP", 10)).map { Occasion.CategoryState(it.name, it.capacity, emptyList(), emptyList()) },
            disabled = false,
            createdAt = now,
            location = null
        )
        val cmd = Command.Visibility(true)
        val (events, output) = Occasion.commandStateMachine.run(cmd, state)
        assertTrue(output == Ok)
        assertEquals(1, events.size)
        val event = events[0] as Event.VisibilityChanged
        assertTrue(event.disabled)
    }

    @Test
    fun `cannot change visibility if occasion does not exist`() {
        val cmd = Command.Visibility(true)
        val (events, output) = Occasion.commandStateMachine.run(cmd, null)
        assertTrue(events.any { it is Error.InvalidCommand })
        assertTrue(output.isFailure)
    }

    @Test
    fun `can reserve a place in a category`() {
        val now = Clock.System.now()
        val fanPageId = UUID.randomUUID()
        val categoryName = "VIP"
        val state = State(
            status = Occasion.Status.open,
            fanPageId = fanPageId,
            name = "Occasion1",
            description = "desc",
            resource = "res1",
            startDateTime = now,
            endDateTime = now,
            categories = listOf(Occasion.CategoryState(categoryName, 10, emptyList(), emptyList())),
            disabled = false,
            createdAt = now,
            location = null
        )
        val cmd = Command.ReservePlace(categoryName, "user1", now, 2)
        val (events, output) = Occasion.commandStateMachine.run(cmd, state)
        assertTrue(output == Ok)
        assertEquals(1, events.size)
        val event = events[0] as Event.PlaceReserved
        assertEquals(categoryName, event.categoryName)
        assertEquals("user1", event.resource)
        assertEquals(2, event.places)
    }

    @Test
    fun `cannot reserve a place if occasion does not exist`() {
        val now = Clock.System.now()
        val cmd = Command.ReservePlace("VIP", "user1", now, 2)
        val (events, output) = Occasion.commandStateMachine.run(cmd, null)
        assertTrue(events.any { it is Error.InvalidCommand })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot reserve a place in non-existent category`() {
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
            categories = listOf(Occasion.CategoryState("VIP", 10, emptyList(), emptyList())),
            disabled = false,
            createdAt = now,
            location = null
        )
        val cmd = Command.ReservePlace("REGULAR", "user1", now, 2)
        val (events, output) = Occasion.commandStateMachine.run(cmd, state)
        assertTrue(events.any { it is Error.InvalidCommand })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot reserve a place if already confirmed`() {
        val now = Clock.System.now()
        val fanPageId = UUID.randomUUID()
        val categoryName = "VIP"
        val confirmed = listOf(Occasion.PlaceState(now, "user1", 2))
        val state = State(
            status = Occasion.Status.open,
            fanPageId = fanPageId,
            name = "Occasion1",
            description = "desc",
            resource = "res1",
            startDateTime = now,
            endDateTime = now,
            categories = listOf(Occasion.CategoryState(categoryName, 10, emptyList(), confirmed)),
            disabled = false,
            createdAt = now,
            location = null
        )
        val cmd = Command.ReservePlace(categoryName, "user1", now, 2)
        val (events, output) = Occasion.commandStateMachine.run(cmd, state)
        assertTrue(events.any { it is Error.InvalidCommand })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot reserve a place if category is full`() {
        val now = Clock.System.now()
        val fanPageId = UUID.randomUUID()
        val categoryName = "VIP"
        val reserved = listOf(Occasion.PlaceState(now, "user2", 10))
        val state = State(
            status = Occasion.Status.open,
            fanPageId = fanPageId,
            name = "Occasion1",
            description = "desc",
            resource = "res1",
            startDateTime = now,
            endDateTime = now,
            categories = listOf(Occasion.CategoryState(categoryName, 10, reserved, emptyList())),
            disabled = false,
            createdAt = now,
            location = null
        )
        val cmd = Command.ReservePlace(categoryName, "user1", now, 1)
        val (events, output) = Occasion.commandStateMachine.run(cmd, state)
        assertTrue(events.any { it is Error.CategoryFull })
        assertTrue(output == Ok)
    }

    @Test
    fun `reserve place emits CategoryFull with correct name and resource`() {
        val now = Clock.System.now()
        val fanPageId = UUID.randomUUID()
        val categoryName = "VIP"
        val reserved = listOf(Occasion.PlaceState(now, "user2", 10))
        val state = State(
            status = Occasion.Status.open,
            fanPageId = fanPageId,
            name = "Occasion1",
            description = "desc",
            resource = "res1",
            startDateTime = now,
            endDateTime = now,
            categories = listOf(Occasion.CategoryState(categoryName, 10, reserved, emptyList())),
            disabled = false,
            createdAt = now,
            location = null
        )
        val cmd = Command.ReservePlace(categoryName, "user1", now, 1)
        val (events, output) = Occasion.commandStateMachine.run(cmd, state)
        val categoryFullEvent = events.find { it is Error.CategoryFull } as? Error.CategoryFull
        assertNotNull(categoryFullEvent)
        assertEquals(categoryName, categoryFullEvent!!.name)
        assertEquals("user1", categoryFullEvent.resource)
        assertTrue(output == Ok)
    }

    @Test
    fun `cannot reserve a place after occasion start time`() {
        val now = Clock.System.now()
        val fanPageId = UUID.randomUUID()
        val categoryName = "VIP"
        val startDateTime = now.plus(10000.seconds)
        val endDateTime = startDateTime.plus(3600.seconds)
        val state = State(
            status = Occasion.Status.open,
            fanPageId = fanPageId,
            name = "Occasion1",
            description = "desc",
            resource = "res1",
            startDateTime = startDateTime,
            endDateTime = endDateTime,
            categories = listOf(Occasion.CategoryState(categoryName, 10, emptyList(), emptyList())),
            disabled = false,
            createdAt = now,
            location = null
        )
        val takenAt = now.plus(20000.seconds) // After the start time
        val cmd = Command.ReservePlace(categoryName, "user1", takenAt, 2)
        val (events, output) = Occasion.commandStateMachine.run(cmd, state)
        assertTrue(events.any { it is Error.InvalidCommand })
        assertTrue(output== Ok)
    }

    @Test
    fun `can confirm a reserved place`() {
        val now = Clock.System.now()
        val fanPageId = UUID.randomUUID()
        val categoryName = "VIP"
        val reserved = listOf(Occasion.PlaceState(now, "user1", 2))
        val state = State(
            status = Occasion.Status.open,
            fanPageId = fanPageId,
            name = "Occasion1",
            description = "desc",
            resource = "res1",
            startDateTime = now,
            endDateTime = now,
            categories = listOf(Occasion.CategoryState(categoryName, 10, reserved, emptyList())),
            disabled = false,
            createdAt = now,
            location = null
        )
        val cmd = Command.ConfirmPlace("user1", now)
        val (events, output) = Occasion.commandStateMachine.run(cmd, state)
        assertTrue(output == Ok)
        assertEquals(1, events.size)
        val event = events[0] as Event.PlaceConfirmed
        assertEquals("user1", event.resource)
        assertEquals(now, event.confirmedAt)
    }

    @Test
    fun `cannot confirm place if occasion does not exist`() {
        val now = Clock.System.now()
        val cmd = Command.ConfirmPlace("user1", now)
        val (events, output) = Occasion.commandStateMachine.run(cmd, null)
        assertTrue(events.any { it is Error.InvalidCommand })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot confirm place if category does not exist`() {
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
            categories = listOf(Occasion.CategoryState("VIP", 10, emptyList(), emptyList())),
            disabled = false,
            createdAt = now,
            location = null
        )
        val cmd = Command.ConfirmPlace("user1", now)
        val (events, output) = Occasion.commandStateMachine.run(cmd, state)
        assertTrue(events.any { it is Error.InvalidCommand })
        assertTrue(output.isFailure)
    }

    @Test
    fun `cannot confirm place if reservation does not exist`() {
        val now = Clock.System.now()
        val fanPageId = UUID.randomUUID()
        val categoryName = "VIP"
        val state = State(
            status = Occasion.Status.open,
            fanPageId = fanPageId,
            name = "Occasion1",
            description = "desc",
            resource = "res1",
            startDateTime = now,
            endDateTime = now,
            categories = listOf(Occasion.CategoryState(categoryName, 10, emptyList(), emptyList())),
            disabled = false,
            createdAt = now,
            location = null
        )
        val cmd = Command.ConfirmPlace("user1", now)
        val (events, output) = Occasion.commandStateMachine.run(cmd, state)
        assertTrue(events.any { it is Error.InvalidCommand })
        assertTrue(output.isFailure)
    }

    @Test
    fun `eventReducer applies events correctly`() {
        val now = Clock.System.now()
        val fanPageId = UUID.randomUUID()
        val categoryName = "VIP"
        val initialState: State? = null
        val createdEvent = Event.OccasionCreated(
            State(
                status = Occasion.Status.open,
                fanPageId = fanPageId,
                name = "Occasion1",
                description = "desc",
                resource = "res1",
                startDateTime = now,
                endDateTime = now.plus(3600.seconds),
                categories = listOf(Occasion.CategoryState(categoryName, 10, emptyList(), emptyList())),
                disabled = false,
                createdAt = now,
                location = null
            )
        )
        val stateAfterCreate = Occasion.eventReducer.run(initialState, createdEvent)
        assertNotNull(stateAfterCreate)
        assertEquals("Occasion1", stateAfterCreate!!.name)
        val reservedEvent = Event.PlaceReserved(categoryName, "user1", now, 2)
        val stateAfterReserve = Occasion.eventReducer.run(stateAfterCreate, reservedEvent)
        assertEquals(2, stateAfterReserve!!.categories[0].reservedPlaces[0].places)
        val confirmedEvent = Event.PlaceConfirmed("user1", now)
        val stateAfterConfirm = Occasion.eventReducer.run(stateAfterReserve, confirmedEvent)
        assertEquals(2, stateAfterConfirm!!.categories[0].confirmedPlaces[0].places)
        assertEquals(0, stateAfterConfirm.categories[0].reservedPlaces.size)
    }

    @Test
    fun `eventReducer sets status to completed when all categories are full`() {
        val now = Clock.System.now()
        val fanPageId = UUID.randomUUID()
        val categoryName = "VIP"
        val initialState: State? = null
        val createdEvent = Event.OccasionCreated(
            State(
                status = Occasion.Status.open,
                fanPageId = fanPageId,
                name = "Occasion1",
                description = "desc",
                resource = "res1",
                startDateTime = now,
                endDateTime = now.plus(3600.seconds),
                categories = listOf(Occasion.CategoryState(categoryName, 2, emptyList(), emptyList())),
                disabled = false,
                createdAt = now,
                location = null
            )
        )
        val stateAfterCreate = Occasion.eventReducer.run(initialState, createdEvent)
        val reservedEvent = Event.PlaceReserved(categoryName, "user1", now, 2)
        val stateAfterReserve = Occasion.eventReducer.run(stateAfterCreate, reservedEvent)
        val confirmedEvent = Event.PlaceConfirmed("user1", now)
        val stateAfterConfirm = Occasion.eventReducer.run(stateAfterReserve, confirmedEvent)
        assertEquals(Occasion.Status.completed, stateAfterConfirm!!.status)
    }

    @Test
    fun `eventReducer handles unknown event gracefully`() {
        val now = Clock.System.now()
        val fanPageId = UUID.randomUUID()
        val state = State(
            status = Occasion.Status.open,
            fanPageId = fanPageId,
            name = "Occasion1",
            description = "desc",
            resource = "res1",
            startDateTime = now,
            endDateTime = now.plus(3600.seconds),
            categories = listOf(Occasion.CategoryState("VIP", 2, emptyList(), emptyList())),
            disabled = false,
            createdAt = now,
            location = null
        )
        // Use an event type not handled by the reducer
        val cancelledEvent = Event.Cancelled(now)
        val result = Occasion.eventReducer.run(state, cancelledEvent)
        // Should set the status to cancelled
        assertEquals(Occasion.Status.cancelled, result!!.status)
    }

    @Test
    fun `handleCancel prohibits cancel if already cancelled or completed`() {
        val now = Clock.System.now()
        val fanPageId = UUID.randomUUID()
        val stateCancelled = State(
            status = Occasion.Status.cancelled,
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
        val stateCompleted = stateCancelled.copy(status = Occasion.Status.completed)
        val cmd = Command.Cancel(now)
        val (eventsCancelled, outputCancelled) = Occasion.commandStateMachine.run(cmd, stateCancelled)
        assertTrue(eventsCancelled.any { it is Error.InvalidCommand })
        assertTrue(outputCancelled.isFailure)
        val (eventsCompleted, outputCompleted) = Occasion.commandStateMachine.run(cmd, stateCompleted)
        assertTrue(eventsCompleted.any { it is Error.InvalidCommand })
        assertTrue(outputCompleted.isFailure)
    }
}
