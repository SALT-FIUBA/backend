package io.kauth.service.fanpage

import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.isFailure
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlinx.datetime.Clock
import io.kauth.service.fanpage.FanPage.Command
import io.kauth.service.fanpage.FanPage.Event
import io.kauth.service.fanpage.FanPage.commandStateMachine
import io.kauth.service.fanpage.FanPage.State

class FanPageTest {
    @Test
    fun `create fanpage should produce Created event`() {
        val now = Clock.System.now()
        val createdBy = "user1"
        val name = "My FanPage"
        val description = "A cool fanpage"
        val profilePhoto = "photo.jpg"
        val location = "Earth"
        val email = "fan@page.com"
        val phone = "123456789"
        val website = "https://fanpage.com"
        val category = "music"
        val cmd = Command.Create(
            createdBy = createdBy,
            createdAt = now,
            name = name,
            description = description,
            profilePhoto = profilePhoto,
            location = location,
            email = email,
            phone = phone,
            website = website,
            category = category
        )
        val (events, output) = commandStateMachine.run(cmd, null)
        assertTrue(output == Ok)
        assertEquals(1, events.size)
        val expected = Event.Created(
            State(
                name = name,
                createdBy = createdBy,
                createdAt = now,
                description = description,
                profilePhoto = profilePhoto,
                location = location,
                email = email,
                phone = phone,
                website = website,
                admins = emptyList(),
                category = category
            )
        )
        assertEquals(expected, events[0])
    }

    @Test
    fun `cannot create fanpage if already exists`() {
        val now = Clock.System.now()
        val createdBy = "user1"
        val name = "My FanPage"
        val description = "A cool fanpage"
        val profilePhoto = "photo.jpg"
        val location = "Earth"
        val email = "fan@page.com"
        val phone = "123456789"
        val website = "https://fanpage.com"
        val category = "music"
        val state = State(
            name = name,
            createdBy = createdBy,
            createdAt = now,
            description = description,
            profilePhoto = profilePhoto,
            location = location,
            email = email,
            phone = phone,
            website = website,
            admins = emptyList(),
            category = category
        )
        val cmd = Command.Create(
            createdBy = createdBy,
            createdAt = now,
            name = name,
            description = description,
            profilePhoto = profilePhoto,
            location = location,
            email = email,
            phone = phone,
            website = website,
            category = category
        )
        val (events, output) = commandStateMachine.run(cmd, state)
        assertTrue(events.any { it is FanPage.Error.AlreadyExists })
        assertTrue(output.isFailure)
    }
}

