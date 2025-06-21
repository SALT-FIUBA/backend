package io.kauth.service.salt

import io.kauth.service.salt.Device.State
import io.kauth.service.salt.Device.Topics
import io.kauth.service.salt.Device.Command
import io.kauth.service.salt.Device.Event
import io.kauth.service.salt.Device.Error
import io.kauth.service.salt.Device.commandStateMachine
import io.kauth.service.salt.Device.eventReducer
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Failure
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class DeviceTest {
    private val now: Instant = Clock.System.now()
    private val uuid: UUID = UUID.randomUUID()
    private val state = State(
        organismId = uuid,
        trainId = uuid,
        seriesNumber = "SN-123",
        ports = listOf("P1", "P2"),
        status = "active",
        createdBy = "user1",
        createdAt = now,
        topics = Topics("state", "command", "status"),
        deviceState = null,
        deleted = false
    )

    @Test
    fun `delete command sets deleted flag and emits Deleted event`() {
        val cmd = Command.Delete(deletedBy = "admin", deletedAt = now)
        val (events, output) = commandStateMachine.run(cmd, state)
        assertEquals(Ok, output)
        assertTrue(events.any { it is Event.Deleted })
        val deletedEvent = events.filterIsInstance<Event.Deleted>().first()
        val newState = eventReducer.run(state, deletedEvent)
        assertTrue(newState?.deleted == true)
    }

    @Test
    fun `delete command fails if already deleted`() {
        val deletedState = state.copy(deleted = true)
        val cmd = Command.Delete(deletedBy = "admin", deletedAt = now)
        val (events, output) = commandStateMachine.run(cmd, deletedState)
        assertTrue(output is Failure<*>)
        assertTrue(events.any { it is Error.DeviceDoesNotExists })
    }

    @Test
    fun `delete command fails if device does not exist`() {
        val cmd = Command.Delete(deletedBy = "admin", deletedAt = now)
        val (events, output) = commandStateMachine.run(cmd, null)
        assertTrue(output is Failure<*>)
        assertTrue(events.any { it is Error.DeviceDoesNotExists })
    }

    @Test
    fun `edit device happy path emits event and updates trainId`() {
        val newTrainId = UUID.randomUUID()
        val cmd = Command.EditDevice(
            trainId = newTrainId,
            editedBy = "admin",
            editedAt = now
        )
        val (events, output) = commandStateMachine.run(cmd, state)
        assertEquals(Ok, output)
        assertTrue(events.any { it is Event.DeviceEdited })
        val editedEvent = events.filterIsInstance<Event.DeviceEdited>().first()
        val newState = eventReducer.run(state, editedEvent)
        assertEquals(newTrainId, newState?.trainId)
    }

    @Test
    fun `edit device fails if already deleted`() {
        val deletedState = state.copy(deleted = true)
        val cmd = Command.EditDevice(
            trainId = UUID.randomUUID(),
            editedBy = "admin",
            editedAt = now
        )
        val (events, output) = commandStateMachine.run(cmd, deletedState)
        assertTrue(output is Failure<*>)
        assertTrue(events.any { it is Error.DeviceDoesNotExists })
    }

    @Test
    fun `edit device fails if device does not exist`() {
        val cmd = Command.EditDevice(
            trainId = UUID.randomUUID(),
            editedBy = "admin",
            editedAt = now
        )
        val (events, output) = commandStateMachine.run(cmd, null)
        assertTrue(output is Failure<*>)
        assertTrue(events.any { it is Error.DeviceDoesNotExists })
    }

    @Test
    fun `edit device fails if trainId is unchanged`() {
        val cmd = Command.EditDevice(
            trainId = state.trainId,
            editedBy = "admin",
            editedAt = now
        )
        val (events, output) = commandStateMachine.run(cmd, state)
        assertTrue(output is Failure<*>)
        assertTrue(events.any { it is Error.DeviceDoesNotExists })
    }

    @Test
    fun `reducer updates trainId on DeviceEdited event`() {
        val newTrainId = UUID.randomUUID()
        val event = Event.DeviceEdited(
            trainId = newTrainId,
            editedBy = "admin",
            editedAt = now,
            ports = null
        )
        val newState = eventReducer.run(state, event)
        assertEquals(newTrainId, newState?.trainId)
    }

    @Test
    fun `edit device with only ports updates ports`() {
        val newPorts = listOf("P3", "P4")
        val cmd = Command.EditDevice(
            trainId = null,
            ports = newPorts,
            editedBy = "admin",
            editedAt = now
        )
        val (events, output) = commandStateMachine.run(cmd, state)
        assertEquals(Ok, output)
        val editedEvent = events.filterIsInstance<Event.DeviceEdited>().first()
        val newState = eventReducer.run(state, editedEvent)
        assertEquals(newPorts, newState?.ports)
        assertEquals(state.trainId, newState?.trainId)
    }

    @Test
    fun `edit device with trainId and ports updates both`() {
        val newTrainId = UUID.randomUUID()
        val newPorts = listOf("P5", "P6")
        val cmd = Command.EditDevice(
            trainId = newTrainId,
            ports = newPorts,
            editedBy = "admin",
            editedAt = now
        )
        val (events, output) = commandStateMachine.run(cmd, state)
        assertEquals(Ok, output)
        val editedEvent = events.filterIsInstance<Event.DeviceEdited>().first()
        val newState = eventReducer.run(state, editedEvent)
        assertEquals(newTrainId, newState?.trainId)
        assertEquals(newPorts, newState?.ports)
    }

    @Test
    fun `edit device fails if no changes provided`() {
        val cmd = Command.EditDevice(
            trainId = state.trainId,
            ports = state.ports,
            editedBy = "admin",
            editedAt = now
        )
        val (events, output) = commandStateMachine.run(cmd, state)
        assertTrue(output is Failure<*>)
        assertTrue(events.any { it is Error.DeviceDoesNotExists })
    }

    @Test
    fun `edit device fails if ports is set to empty`() {
        val cmd = Command.EditDevice(
            trainId = null,
            ports = emptyList(),
            editedBy = "admin",
            editedAt = now
        )
        val (events, output) = commandStateMachine.run(cmd, state)
        assertTrue(output is Failure<*>)
        assertTrue(events.any { it is Error.DeviceDoesNotExists })
    }

    @Test
    fun `reducer updates ports on DeviceEdited event`() {
        val newPorts = listOf("P7", "P8")
        val event = Event.DeviceEdited(
            trainId = state.trainId,
            ports = newPorts,
            editedBy = "admin",
            editedAt = now
        )
        val newState = eventReducer.run(state, event)
        assertEquals(newPorts, newState?.ports)
    }
}
