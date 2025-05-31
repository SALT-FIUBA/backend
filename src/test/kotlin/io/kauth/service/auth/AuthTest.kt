package io.kauth.service.auth

import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.isFailure
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID
import io.kauth.service.auth.Auth.Command
import io.kauth.service.auth.Auth.UserEvent
import io.kauth.service.auth.Auth.User
import io.kauth.service.auth.Auth.stateMachine

class AuthTest {
    @Test
    fun `create user should produce UserCreated event`() {
        val email = "test@example.com"
        val personalData = User.PersonalData("John", "Doe")
        val roles = listOf("user")
        val createdBy = UUID.randomUUID()
        val cmd = Command.CreateUser(email, null, null, personalData, roles, createdBy)
        val (events, output) = stateMachine.run(cmd, null)
        assertTrue(output == Ok)
        assertEquals(1, events.size)
        val expected = UserEvent.UserCreated(
            User(
                email = email,
                credentials = null,
                credentialSource = null,
                personalData = personalData,
                loginCount = 0,
                roles = roles,
                createdBy = createdBy
            )
        )
        assertEquals(expected, events[0])
    }

    @Test
    fun `cannot create user if already exists`() {
        val email = "test@example.com"
        val personalData = User.PersonalData("John", "Doe")
        val roles = listOf("user")
        val createdBy = UUID.randomUUID()
        val state = User(email, null, null, personalData, 0, roles, createdBy)
        val cmd = Command.CreateUser(email, null, null, personalData, roles, createdBy)
        val (events, output) = stateMachine.run(cmd, state)
        assertTrue(events.any { it is Auth.Error.UserAlReadyExists })
        assertTrue(output.isFailure)
    }

    @Test
    fun `update personal data should produce PersonalDataUpdated event`() {
        val email = "test@example.com"
        val personalData = User.PersonalData("John", "Doe")
        val newPersonalData = User.PersonalData("Jane", "Smith")
        val roles = listOf("user")
        val createdBy = UUID.randomUUID()
        val state = User(email, null, null, personalData, 0, roles, createdBy)
        val cmd = Command.UpdatePersonalData(newPersonalData)
        val (events, output) = stateMachine.run(cmd, state)
        assertTrue(output == Ok)
        assertEquals(1, events.size)
        val expected = UserEvent.PersonalDataUpdated(newPersonalData)
        assertEquals(expected, events[0])
    }

    @Test
    fun `cannot update personal data if user not found`() {
        val newPersonalData = User.PersonalData("Jane", "Smith")
        val cmd = Command.UpdatePersonalData(newPersonalData)
        val (events, output) = stateMachine.run(cmd, null)
        assertTrue(output.isFailure)
    }

    @Test
    fun `add roles should produce RolesAdded event`() {
        val email = "test@example.com"
        val personalData = User.PersonalData("John", "Doe")
        val roles = listOf("user")
        val newRoles = listOf("admin", "editor")
        val createdBy = UUID.randomUUID()
        val state = User(email, null, null, personalData, 0, roles, createdBy)
        val cmd = Command.AddRoles(newRoles)
        val (events, output) = stateMachine.run(cmd, state)
        assertTrue(output == Ok)
        assertEquals(1, events.size)
        val expected = UserEvent.RolesAdded(newRoles)
        assertEquals(expected, events[0])
    }

    @Test
    fun `cannot add roles if user not found`() {
        val newRoles = listOf("admin", "editor")
        val cmd = Command.AddRoles(newRoles)
        val (events, output) = stateMachine.run(cmd, null)
        assertTrue(output.isFailure)
    }

    @Test
    fun `user login should produce UserLoggedIn event with success true`() {
        val email = "test@example.com"
        val personalData = User.PersonalData("John", "Doe")
        val passwordHash = io.kauth.util.ByteString(byteArrayOf(1,2,3))
        val credentials = Auth.Credentials(passwordHash, passwordHash, Auth.HashAlgorithm.Pbkdf2Sha256(1000))
        val roles = listOf("user")
        val createdBy = UUID.randomUUID()
        val state = User(email, credentials, null, personalData, 0, roles, createdBy)
        val cmd = Command.UserLogin(passwordHash)
        val (events, output) = stateMachine.run(cmd, state)
        assertTrue(output == Ok)
        assertEquals(1, events.size)
        val expected = UserEvent.UserLoggedIn(passwordHash, true)
        assertEquals(expected, events[0])
    }

    @Test
    fun `user login should produce UserLoggedIn event with success false if password does not match`() {
        val email = "test@example.com"
        val personalData = User.PersonalData("John", "Doe")
        val passwordHash = io.kauth.util.ByteString(byteArrayOf(1,2,3))
        val wrongPasswordHash = io.kauth.util.ByteString(byteArrayOf(4,5,6))
        val credentials = Auth.Credentials(passwordHash, passwordHash, Auth.HashAlgorithm.Pbkdf2Sha256(1000))
        val roles = listOf("user")
        val createdBy = UUID.randomUUID()
        val state = User(email, credentials, null, personalData, 0, roles, createdBy)
        val cmd = Command.UserLogin(wrongPasswordHash)
        val (events, output) = stateMachine.run(cmd, state)
        assertTrue(output.isFailure)
        assertEquals(1, events.size)
        val expected = UserEvent.UserLoggedIn(wrongPasswordHash, false)
        assertEquals(expected, events[0])
    }

    @Test
    fun `user login should fail if user not found`() {
        val passwordHash = io.kauth.util.ByteString(byteArrayOf(1,2,3))
        val cmd = Command.UserLogin(passwordHash)
        val (events, output) = stateMachine.run(cmd, null)
        assertTrue(output.isFailure)
    }
}

