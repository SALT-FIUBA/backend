package io.kauth.service.auth

import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.stack.AuthStack
import io.kauth.monad.stack.getService
import io.kauth.service.reservation.Reservation
import io.kauth.service.reservation.ReservationApi
import io.kauth.util.not
import java.util.UUID

object AuthApi {

    fun register(
        id: UUID,
        email: String,
        password: String,
        personalData: Auth.User.PersonalData
    ) = AuthStack.Do {

        val authService = !getService<AuthService.Interface>()

        val result = !ReservationApi.take(email, id.toString())

        if(result !is Reservation.Success) !ApiException("${email} is used")

        val hashAlgorithm = authService.config.hashAlgorithm

        val hashedValue = hashAlgorithm.hashString(password)

        !authService.command.handle(id)(
            Auth.Command.CreateUser(
                email = email,
                credentials = Auth.Credentials(
                    passwordHash = hashedValue.value,
                    salt = hashedValue.salt,
                    algorithm = hashAlgorithm
                ),
                personalData
            )
        )

    }

    fun login(
        email: String,
        password: String
    ) = AuthStack.Do {

        val result = !ReservationApi.readState(email) ?: !ApiException("User does not exists")

        val user = !readState(UUID.fromString(result.ownerId)) ?: !ApiException("User does not exists")

        val passwordMatch = user.credentials.algorithm.checkValue(
            password,
            Auth.HashAlgorithm.HashedValue(user.credentials.passwordHash, user.credentials.salt)
        )

        if(!passwordMatch) !ApiException("Invalid credentials")

        //Generate JWT

        "JWT"

    }

    fun readState(id: UUID) = AuthStack.Do {
        val authService = !getService<AuthService.Interface>()
        !authService.query.readState(id)
    }


}