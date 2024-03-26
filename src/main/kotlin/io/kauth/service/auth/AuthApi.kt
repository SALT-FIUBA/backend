package io.kauth.service.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.stack.AuthStack
import io.kauth.monad.stack.authStackLog
import io.kauth.monad.stack.getService
import io.kauth.service.auth.jwt.Jwt
import io.kauth.service.reservation.Reservation
import io.kauth.service.reservation.ReservationApi
import io.kauth.util.not
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

object AuthApi {

    fun register(
        id: UUID,
        email: String,
        password: String,
        personalData: Auth.User.PersonalData
    ) = AuthStack.Do {

        val log = !authStackLog

        log.info("Register user $email")

        val authService = !getService<AuthService.Interface>()

        val result = !ReservationApi.take(email, id.toString())

        log.info("Reservation take result $result")

        if(result !is Reservation.Success) !ApiException("$email is used")

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

        val log = !authStackLog

        log.info("Login user $email")

        val authService = !getService<AuthService.Interface>()

        val result = !ReservationApi.readState(email) ?: !ApiException("User does not exists")

        val user = !readState(UUID.fromString(result.ownerId)) ?: !ApiException("User does not exists")

        val authResult = !authService.command.handle(UUID.fromString(result.ownerId))(
            Auth.Command.UserLogin(
                user.credentials.algorithm.hashString(password, user.credentials.salt.byteArray).value
            )
        )

        when(authResult) {
            is Auth.Success -> Unit
            is Auth.Error -> !ApiException(authResult.message)
        }

        //Esto lo pdoria hacer otro servicio para que quede registro de los tokens
        val tokens = Auth.Tokens(
            access = !buildJwt(result.ownerId, user),
            refresh = null
        )

        tokens

    }

    val algorithm: AuthStack<Algorithm?> = AuthStack.Do {
        val config = !config
        Algorithm.HMAC256(config.secret)
    }

    val config: AuthStack<AuthService.Config> = AuthStack.Do {
        val authService = !getService<AuthService.Interface>()
        authService.config
    }

    fun buildJwt(
        id: String,
        user: Auth.User
    ) = AuthStack.Do {
        JWT
            .create()
            .withHeader(
                mapOf(
                    "typ" to "Access",
                )
            )
            .withAudience("salt")
            .withIssuer("salt")
            .withClaim("email", user.email)
            .withClaim("id", id)
            .withExpiresAt((Clock.System.now() + 35.minutes).toJavaInstant())
            .sign(!algorithm)
    }

    fun jwtVerify(jwt: String) = AuthStack.Do {

        val verifier = JWT
            .require(!algorithm)
            .withIssuer("salt")
            .build()

        try {
            val claims = verifier.verify(jwt).claims
            Jwt(
                payload = Jwt.Payload(
                    email = claims["email"]?.asString() ?: error("Invalid"),
                    id = claims["id"]?.asString() ?: error("Invalid")
                )
            )
        } catch (e: Throwable) {
            null
        }

    }

    fun readState(id: UUID) = AuthStack.Do {
        val authService = !getService<AuthService.Interface>()
        !authService.query.readState(id)
    }

}