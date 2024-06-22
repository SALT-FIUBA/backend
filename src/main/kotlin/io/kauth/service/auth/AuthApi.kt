package io.kauth.service.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.stack.*
import io.kauth.service.auth.AuthProjection.toUserProjection
import io.kauth.service.auth.jwt.Jwt
import io.kauth.service.reservation.ReservationApi
import io.kauth.service.salt.DeviceProjection
import io.kauth.service.salt.DeviceProjection.toDeviceProjection
import io.kauth.util.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

object AuthApi {

    fun register(
        email: String,
        password: String,
        personalData: Auth.User.PersonalData,
        roles: List<String>
    ) = AppStack.Do {

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
                ),
            )

        id

    }

    fun login(
        email: String,
        password: String
    ) = AppStack.Do {

        val log = !authStackLog

        log.info("Login user $email")

        val authService = !getService<AuthService.Interface>()

        val result = !ReservationApi.readState("user-$email") ?: !ApiException("User does not exists")

        val user = !Query.readState(UUID.fromString(result.ownerId)) ?: !ApiException("User does not exists")

        !authService.command
            .handle(UUID.fromString(result.ownerId))
            .throwOnFailureHandler(
                Auth.Command.UserLogin(
                    user.credentials.algorithm.hashString(password, user.credentials.salt.byteArray).value
                ),
            )

        val tokens = Auth.Tokens(
            access = !buildJwt(result.ownerId, user),
            refresh = null
        )

        tokens

    }

    val algorithm = AppStack.Do {
        val config = !config
        Algorithm.HMAC256(config.secret)
    }

    val config = AppStack.Do {
        val authService = !getService<AuthService.Interface>()
        authService.config
    }

    fun buildJwt(
        id: String,
        user: Auth.User
    ) = AppStack.Do {
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

    val ApplicationCall.auth get() = AppStack.Do {

        val authHeader = request.header("Authorization") ?: ""

        val token = "Bearer (?<token>.+)"
            .toRegex()
            .matchEntire(authHeader)
            ?.groups?.get("token")
            ?.value?.trim() ?: ""

        val jwt = !jwtVerify(token) ?: !ApiException("UnAuthorized ${request.uri}")

        !registerService(jwt)

    }

    fun jwtVerify(jwt: String) = AppStack.Do {

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

    val appStackAuthValidateSupervisor get() = AppStack.Do {
        val auth = !authStackJwt
        if("supervisor" !in auth.payload.roles) {
            !ApiException("UnAuthorized")
        }
    }

    val readStateFromSession get() = AppStack.Do {
        val jwt = !authStackJwt
        !Query.get(jwt.payload.id) ?: !ApiException("User not found")
    }

    object Query {

        fun readState(id: UUID) = AppStack.Do {
            val authService = !getService<AuthService.Interface>()
            !authService.query.readState(id)
        }

        fun get(id: String) = AppStack.Do {
            !appStackAuthValidateSupervisor
            !appStackDbQuery {
                AuthProjection.User.selectAll()
                    .where { AuthProjection.User.id eq id }
                    .singleOrNull()
                    ?.toUserProjection
            }
        }

        fun getByEmail(email: String) = AppStack.Do {
            !appStackAuthValidateSupervisor
            !appStackDbQuery {
                AuthProjection.User.selectAll()
                    .where { AuthProjection.User.email eq email }
                    .singleOrNull()
                    ?.toUserProjection
            }
        }

        fun list() = AppStack.Do {
            !appStackAuthValidateSupervisor
            !appStackDbQuery {
                AuthProjection.User.selectAll()
                    .map { it.toUserProjection }
            }
        }


    }

}