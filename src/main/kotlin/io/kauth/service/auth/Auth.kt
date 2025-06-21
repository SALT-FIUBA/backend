package io.kauth.service.auth

import io.kauth.abstractions.reducer.Reducer
import io.kauth.abstractions.reducer.reducerOf
import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.CommandMonad
import io.kauth.util.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec


object Auth {

    @Serializable
    data class User(
        val email: String,
        val credentials: Credentials?,
        val credentialSource: CredentialSource? = null,
        val personalData: PersonalData,
        val loginCount: Int? = null,
        val roles: List<String> = emptyList(),
        @Contextual
        val createdBy: UUID? = null
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
    sealed interface CredentialSource

    @Serializable
    data class Credentials( //TODO: Sealed class, Google, Password, etc!
        @Serializable(ByteStringBase64Serializer::class)
        val passwordHash: ByteString,
        @Serializable(ByteStringBase64Serializer::class)
        val salt: ByteString,
        val algorithm: HashAlgorithm
    ) : CredentialSource

    @Serializable
    data class Google(
        val googleId: String
    ) : CredentialSource

    //Esto lo queremos logear en el eventStore ?
    @Serializable
    sealed interface Command {

        @Serializable
        data class CreateUser(
            val email: String,
            val credentials: Credentials?,
            val credentialSource: CredentialSource? = null,
            val personalData: User.PersonalData,
            val roles: List<String> = emptyList(),
            @Contextual
            val createdBy: UUID? = null
        ): Command

        @Serializable
        data class UpdatePersonalData(
            val personalData: User.PersonalData
        ): Command

        @Serializable
        data class UserLogin(
            @Serializable(ByteStringBase64Serializer::class)
            val passwordHash: ByteString?
        ): Command

        @Serializable
        data class AddRoles(
            val roles: List<String>,
        ): Command

    }

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
            val passwordHash: ByteString?,
            val success: Boolean
        ) : UserEvent

        @Serializable
        data class RolesAdded(
            val roles: List<String>
        ) : UserEvent
    }

    @Serializable
    sealed interface Error : UserEvent {
        @Serializable
        data object UserAlReadyExists : Error
    }

    @Serializable
    data class Tokens(
        val access: String,
        val refresh: String?
    )

    val handleUserLoggedIn get() = Reducer<User?, UserEvent.UserLoggedIn> { state, event ->
        state?.copy(loginCount = state.loginCount?.let { it + 1 } ?: 0)
    }

    val handleUpdatedPersonalData get() = Reducer<User?, UserEvent.PersonalDataUpdated> { state, event ->
        state?.copy(personalData = event.personalData)
    }

    val handleRolesAdded get() = Reducer<User?, UserEvent.RolesAdded> { state, event ->
        state?.copy(roles = (state.roles) + event.roles)
    }

    val handleCreatedUser get() = Reducer<User?, UserEvent.UserCreated> { state, event ->
        event.user
    }

    val handleAddRoles get() = CommandMonad.Do<Command.AddRoles, User?, UserEvent, Output> { exit ->
        !getState ?: !exit(Failure("User does not exists"))
        !emitEvents(UserEvent.RolesAdded(command.roles))
        Ok
    }

    val handleUserLogin get() = CommandMonad.Do<Command.UserLogin, User?, UserEvent, Output> { exit ->
        val state = !getState ?: !exit(Failure("User does not exists"))

        if((state.credentials?: state.credentialSource as? Credentials)?.passwordHash != command.passwordHash) {
            !emitEvents(UserEvent.UserLoggedIn(passwordHash = command.passwordHash, success = false))
            !exit(Failure("Invalid credentials"))
        }

        !emitEvents(UserEvent.UserLoggedIn(passwordHash = command.passwordHash, success = true))
        Ok
    }

    val handleUpdatePersonalData get() = CommandMonad.Do<Command.UpdatePersonalData, User?, UserEvent, Output> { exit ->
        !getState ?: !exit(Failure("User does not exists"))
        val personalData = command.personalData
        !emitEvents(UserEvent.PersonalDataUpdated(personalData))
        Ok
    }

    val handleCreateUser get() = CommandMonad.Do<Command.CreateUser, User?, UserEvent, Output> { exit ->
        val state = !getState

        if(state != null) {
            !emitEvents(Error.UserAlReadyExists)
            !exit(Failure("User already exists"))
        }

        val user =
            User(
                email = command.email,
                personalData = command.personalData,
                credentials = null,
                loginCount = 0,
                roles = command.roles,
                createdBy = command.createdBy,
                credentialSource = command.credentialSource
            )

        !emitEvents(
            UserEvent.UserCreated(user)
        )

        Ok

    }

    val stateMachine get() =
        CommandMonad.Do<Command, User?, UserEvent, Output> { exit ->
            val command = !getCommand
            !when (command) {
                is Command.CreateUser -> handleCreateUser
                is Command.UpdatePersonalData -> handleUpdatePersonalData
                is Command.UserLogin -> handleUserLogin
                is Command.AddRoles -> handleAddRoles
            }
        }

    val eventStateMachine get() =
        reducerOf(
            UserEvent.UserCreated::class to handleCreatedUser,
            UserEvent.UserLoggedIn::class to handleUserLoggedIn,
            UserEvent.PersonalDataUpdated::class to handleUpdatedPersonalData,
            UserEvent.RolesAdded::class to handleRolesAdded
        )

}
