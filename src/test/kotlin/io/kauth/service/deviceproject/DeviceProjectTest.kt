package io.kauth.service.deviceproject

import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.isFailure
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlinx.datetime.Clock
import io.kauth.service.deviceproject.DeviceProject.Command
import io.kauth.service.deviceproject.DeviceProject.Event
import io.kauth.service.deviceproject.DeviceProject.commandStateMachine
import io.kauth.service.deviceproject.DeviceProject.State

class DeviceProjectTest {
    @Test
    fun `create project should produce ProjectCreated event`() {
        val now = Clock.System.now()
        val createdBy = "user1"
        val name = "ProjectX"
        val owners = listOf("user1", "user2")
        val cmd = Command.CreateProject(now, createdBy, name, owners)
        val (events, output) = commandStateMachine.run(cmd, null)
        assertTrue(output == Ok)
        assertEquals(1, events.size)
        val expected = Event.ProjectCreated(
            State(
                name = name,
                createdBy = createdBy,
                owners = owners,
                createdAt = now,
                enabled = true
            )
        )
        assertEquals(expected, events[0])
    }

    @Test
    fun `cannot create project if already exists`() {
        val now = Clock.System.now()
        val createdBy = "user1"
        val name = "ProjectX"
        val owners = listOf("user1", "user2")
        val state = State(name, createdBy, owners, now, true)
        val cmd = Command.CreateProject(now, createdBy, name, owners)
        val (events, output) = commandStateMachine.run(cmd, state)
        assertTrue(events.any { it is DeviceProject.Error.UnknownError })
        assertTrue(output.isFailure)
    }

    @Test
    fun `set enabled should produce EnabledSet event`() {
        val now = Clock.System.now()
        val createdBy = "user1"
        val name = "ProjectX"
        val owners = listOf("user1", "user2")
        val state = State(name, createdBy, owners, now, true)
        val cmd = Command.SetEnabled(false)
        val (events, output) = commandStateMachine.run(cmd, state)
        assertTrue(output == Ok)
        assertEquals(1, events.size)
        val expected = Event.EnabledSet(false)
        assertEquals(expected, events[0])
    }

    @Test
    fun `cannot set enabled if project does not exist`() {
        val cmd = Command.SetEnabled(false)
        val (events, output) = commandStateMachine.run(cmd, null)
        assertTrue(events.any { it is DeviceProject.Error.UnknownError })
        assertTrue(output.isFailure)
    }
}

