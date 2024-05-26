package io.kauth.service.auth

import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.StateMonad
import io.kauth.util.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec


object Auth {

    @Serializable
    enum class Role {
        supervisor,
        operators
    }

    @Serializable
    enum class InternalRole {
        admin
    }

    @Serializable
    data class User(
        val email: String,
        val credentials: Credentials,
        val personalData: PersonalData,
        val loginCount: Int? = null,
        val roles: List<String> = emptyList()
    ) {
        @Serializable
        data class PersonalData(
            val firstName: String,
            val lastName: String
        )
    }

    @Serializable
    sealed class HashAlgorithm {

        @Serializable
        data class HashedValue(
            @Serializable(ByteStringBase64Serializer::class)
            val value: ByteString,
            @Serializable(ByteStringBase64Serializer::class)
            val salt: ByteString
        )

        abstract fun hashString(value: String, salt: ByteArray? = null): HashedValue

        abstract fun checkValue(value: String, hashed: HashedValue): Boolean

        @Serializable
        @SerialName("Pbkdf2Sha256")
        data class Pbkdf2Sha256(
            val iterations: Int
        ) : HashAlgorithm() {

            //todo: withSalt
            override fun hashString(value: String, salt: ByteArray?): HashedValue {

                val derivedKeyLength = 64

                val salt = salt ?: SecureRandom.getSeed(16)

                val spec = PBEKeySpec(
                    value.toCharArray(),
                    salt,
                    iterations,
                    derivedKeyLength * 8
                )

                val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")

                val passwordHash = keyFactory.generateSecret(spec).encoded

                return HashedValue(ByteString(passwordHash), ByteString(salt))

            }

            override fun checkValue(value: String, hashed: HashedValue): Boolean {
                return hashString(value, hashed.salt.byteArray) == hashed
            }

        }

    }

    @Serializable
    data class Credentials(
        @Serializable(ByteStringBase64Serializer::class)
        val passwordHash: ByteString,
        @Serializable(ByteStringBase64Serializer::class)
        val salt: ByteString,
        val algorithm: HashAlgorithm
    )

    //Esto lo queremos logear en el eventStore ?
    @Serializable
    sealed interface Command {

        @Serializable
        data class CreateUser(
            val email: String,
            val credentials: Credentials,
            val personalData: User.PersonalData,
            val roles: List<String> = emptyList()
        ): Command

        @Serializable
        data class UpdatePersonalData(
            val personalData: User.PersonalData
        ): Command

        @Serializable
        data class UserLogin(
            @Serializable(ByteStringBase64Serializer::class)
            val passwordHash: ByteString
        ): Command

    }

    //Nos dice que paso en el sistema
    @Serializable
    sealed interface UserEvent {

        @Serializable
        data class UserCreated(
            val user: User
        ): UserEvent

        @Serializable
        data class PersonalDataUpdated(
            val personalData: User.PersonalData
        ): UserEvent

        @Serializable
        data class UserLoggedIn(
            @Serializable(ByteStringBase64Serializer::class)
            val passwordHash: ByteString,
            val success: Boolean
        ) : UserEvent

    }

    @Serializable
    data class Tokens(
        val access: String,
        val refresh: String?
    )

    //Hay eventos que dan comandos, y otros que no..
    val UserEvent.asCommand get() =
        when(this) {
            is UserEvent.UserLoggedIn -> Command.UserLogin(passwordHash = passwordHash)
            is UserEvent.UserCreated -> Command.CreateUser(
                email = user.email,
                credentials = user.credentials,
                personalData = user.personalData,
                roles = user.roles
            )
            is UserEvent.PersonalDataUpdated -> Command.UpdatePersonalData(
                personalData = personalData
            )
        }

    fun handleUserLogin(
        command: Command.UserLogin
    ) = StateMonad.Do<User?, UserEvent, Output> { exit ->
        val state = !getState ?: !exit(Failure("User does not exists"))

        if(state.credentials.passwordHash != command.passwordHash) {
            !emitEvents(UserEvent.UserLoggedIn(passwordHash = command.passwordHash, success = false))
            !exit(Failure("Invalid credentials"))
        }

        !setState(state.copy(loginCount = state.loginCount?.let { it + 1 } ?: 0))
        !emitEvents(UserEvent.UserLoggedIn(passwordHash = command.passwordHash, success = true))

        Ok
    }

    fun handleUpdatePersonalData(
        command: Command.UpdatePersonalData
    ) = StateMonad.Do<User?, UserEvent, Output> { exit ->
        val state = !getState ?: !exit(Failure("User does not exists"))

        val personalData = command.personalData

        !emitEvents(
            UserEvent.PersonalDataUpdated(personalData)
        )

        !setState(
            state.copy(personalData = personalData)
        )

        Ok

    }

    fun handleCreateUser(
        command: Command.CreateUser,
    ) =  StateMonad.Do<User?, UserEvent, Output> { exit ->
        val state = !getState

        if(state != null) !exit(Failure("User already exists"))

        val user =
            User(
                email = command.email,
                personalData = command.personalData,
                credentials = command.credentials,
                loginCount = 0,
                roles = command.roles
            )

        !emitEvents(
            UserEvent.UserCreated(user)
        )

        !setState(
            user
        )

        Ok

    }

    fun stateMachine(
        command: Command
    ) = when (command) {
        is Command.CreateUser -> handleCreateUser(command)
        is Command.UpdatePersonalData -> handleUpdatePersonalData(command)
        is Command.UserLogin -> handleUserLogin(command)
    }

}
