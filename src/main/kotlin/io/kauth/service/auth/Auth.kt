package io.kauth.service.auth

import io.kauth.monad.state.StateMonad
import io.kauth.util.ByteString
import io.kauth.util.ByteStringBase64Serializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec


object Auth {

    //Domain Model
    @Serializable
    data class User(
        val email: String,
        val credentials: Credentials,
        val personalData: PersonalData
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

        abstract fun hashString(value: String): HashedValue

        abstract fun checkValue(value: String, hashed: HashedValue): Boolean

        @Serializable
        @SerialName("Pbkdf2Sha256")
        data class Pbkdf2Sha256(
            val iterations: Int
        ) : HashAlgorithm() {

            override fun hashString(value: String): HashedValue {

                val derivedKeyLength = 64
                val saltSize = 16

                val salt = SecureRandom.getSeed(saltSize)

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
                val derivedKeyLength = 64

                val spec = PBEKeySpec(
                    value.toCharArray(),//utf-8
                    hashed.salt.byteArray,
                    iterations,
                    derivedKeyLength * 8
                )

                val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val secretCompare = keyFactory.generateSecret(spec).encoded
                val passwordHash = ByteString(secretCompare)
                return  hashed.value == passwordHash
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

    //Commands (Inputs)
    @Serializable
    sealed interface Command {

        @Serializable
        data class CreateUser(
            val email: String,
            val credentials: Credentials,
            val personalData: User.PersonalData
        ): Command

        @Serializable
        data class UpdatePersonalData(
            val personalData: User.PersonalData
        ): Command

    }

    //Cada comando tiene un evento asociado, son isomorficos ?

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

    }

    sealed interface Output

    data object Success : Output

    data class Error(val message: String) : Output

    val UserEvent.asCommand get() =
        when(this) {
            is UserEvent.UserCreated -> Command.CreateUser(
                email = user.email,
                credentials = user.credentials,
                personalData = user.personalData
            )
            is UserEvent.PersonalDataUpdated -> Command.UpdatePersonalData(
                personalData = personalData
            )
        }

    fun handleUpdatePersonalData(
        command: Command.UpdatePersonalData
    ) = StateMonad.Do<User?, UserEvent, Output> { exit ->
        val state = !getState ?: !exit(Error("User does not exists"))

        val personalData = command.personalData

        !emitEvents(
            UserEvent.PersonalDataUpdated(personalData)
        )

        !setState(
            state.copy(personalData = personalData)
        )

        Success

    }

    fun handleCreateUser(
        command: Command.CreateUser,
    ) =  StateMonad.Do<User?, UserEvent, Output> { exit ->
        val state = !getState

        if(state != null) !exit(Error("User already exists"))

        val user =
            User(
                email = command.email,
                personalData = command.personalData,
                credentials = command.credentials
            )

        !emitEvents(
            UserEvent.UserCreated(user)
        )

        !setState(
            user
        )

        Success

    }

    //TODO: Errors
    fun stateMachine(
        command: Command
    ) = StateMonad.Do<User?, UserEvent, Output> {
        !when(command) {
            is Command.CreateUser -> handleCreateUser(command)
            is Command.UpdatePersonalData -> handleUpdatePersonalData(command)
        }
    }

}
