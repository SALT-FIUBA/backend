package io.kauth.service.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.abstractions.result.throwOnFailure
import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.stack.*
import io.kauth.service.auth.jwt.Jwt
import io.kauth.service.reservation.ReservationApi
import io.kauth.util.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

object AuthApi {

    fun register(
        email: String,
        password: String,
        personalData: Auth.User.PersonalData,
        roles: List<String>
    ) = AuthStack.Do {

        val log = !authStackLog

        log.info("Register user $email")

        val authService = !getService<AuthService.Interface>()

        val id = !ReservationApi.takeIfNotTaken("user-${email}") { UUID.randomUUID().toString() }

        val hashAlgorithm = authService.config.hashAlgorithm
        val hashedValue = hashAlgorithm.hashString(password)

        !authService.command
            .handle(UUID.fromString(id))
            .throwOnFailureHandler(
                Auth.Command.CreateUser(
                    email = email,
                    credentials = Auth.Credentials(
                        passwordHash = hashedValue.value,
                        salt = hashedValue.salt,
                        algorithm = hashAlgorithm
                    ),
                    roles = roles,
                    personalData = personalData
                )
            )

        id

    }

    fun login(
        email: String,
        password: String
    ) = AuthStack.Do {

        val log = !authStackLog

        log.info("Login user $email")

        val authService = !getService<AuthService.Interface>()

        val result = !ReservationApi.readState("user-$email") ?: !ApiException("User does not exists")

        val user = !readState(UUID.fromString(result.ownerId)) ?: !ApiException("User does not exists")

        !authService.command
            .handle(UUID.fromString(result.ownerId))
            .throwOnFailureHandler(
                Auth.Command.UserLogin(
                    user.credentials.algorithm.hashString(password, user.credentials.salt.byteArray).value
                )
            )

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
            .withClaim("roles", user.roles)
            .withExpiresAt((Clock.System.now() + 35.minutes).toJavaInstant())
            .sign(!algorithm)
    }

    val ApplicationCall.auth get() = AuthStack.Do {

        val authHeader = request.header("Authorization") ?: ""

        val token = "Bearer (?<token>.+)"
            .toRegex()
            .matchEntire(authHeader)
            ?.groups?.get("token")
            ?.value?.trim() ?: ""

        val jwt = !jwtVerify(token) ?: !ApiException("UnAuthorized")

        !registerService(jwt)

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
                    email = claims["email"]?.asString() ?: error("Invalid jwt, no email"),
                    id = claims["id"]?.asString() ?: error("Invalid jwt, no id"),
                    roles = claims["roles"]?.asList(String::class.java) ?: emptyList()
                )
            )

        } catch (e: Throwable) {
            null
        }

    }

    val readStateFromSession get() = AuthStack.Do {
        val jwt = !authStackJwt
        !readState(UUID.fromString(jwt.payload.id)) ?: !ApiException("User not found")
    }

    fun readState(id: UUID) = AuthStack.Do {
        val authService = !getService<AuthService.Interface>()
        !authService.query.readState(id)
    }

}