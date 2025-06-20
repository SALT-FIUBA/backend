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
}
