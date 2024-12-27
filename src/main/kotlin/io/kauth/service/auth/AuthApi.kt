package io.kauth.service.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.exception.ApiException
import io.kauth.exception.allowIf
import io.kauth.exception.not
import io.kauth.monad.apicall.*
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.getService
import io.kauth.service.auth.AuthProjection.toUserProjection
import io.kauth.service.organism.OrganismProjection
import io.kauth.service.organism.OrganismProjection.toOrganismUserInfoProjection
import io.kauth.service.reservation.ReservationApi
import io.kauth.util.*
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

object AuthApi {

    fun register(
        email: String,
        password: String,
        personalData: Auth.User.PersonalData,
        roles: List<String>
    ) = ApiCall.Do {

        !allowIf("admin" !in roles) {
            "Invalid role!"
        }

        val log = !apiCallLog

        log.info("Register user $email")

        val authService = !apiCallGetService<AuthService.Interface>()

        val id = !ReservationApi.takeIfNotTaken("user-${email}") { UUID.randomUUID().toString() }.toApiCall()

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
                    personalData = personalData,
                    createdBy = jwt?.payload?.uuid
                ),
            )
            .toApiCall()

        id

    }

    fun login(
        email: String,
        password: String
    ) = ApiCall.Do {

        val log = !apiCallLog

        log.info("Login user $email")

        val authService = !apiCallGetService<AuthService.Interface>()

        val result = !ReservationApi.readState("user-$email").toApiCall() ?: !ApiException("User does not exists")

        val user = !Query.readState(UUID.fromString(result.ownerId)).toApiCall() ?: !ApiException("User does not exists")

        !authService.command
            .handle(UUID.fromString(result.ownerId))
            .throwOnFailureHandler(
                Auth.Command.UserLogin(
                    user.credentials.algorithm.hashString(password, user.credentials.salt.byteArray).value
                ),
            )
            .toApiCall()

        val tokens = Auth.Tokens(
            access = !buildJwt(result.ownerId, user),
            refresh = null
        )

        tokens

    }

    val algorithm = ApiCall.Do {
        val config = !config
        Algorithm.HMAC256(config.secret)
    }

    val config = ApiCall.Do {
        val authService = !apiCallGetService<AuthService.Interface>()
        authService.config
    }

    fun buildJwt(
        id: String,
        user: Auth.User
    ) = ApiCall.Do {
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

    val apiCallAuthValidateAdmin get() = ApiCall.Do {
        val auth = !apiCallJwt
        !allowIf( "admin" in auth.payload.roles)
    }

    val readStateFromSession get() = ApiCall.Do {
        val jwt = !apiCallJwt
        !Query.get(jwt.payload.id) ?: !ApiException("User not found")
    }

    object Query {

        fun readState(id: UUID) = AppStack.Do {
            val authService = !getService<AuthService.Interface>()
            !authService.query.readState(id)
        }

        fun get(id: String? = null) = ApiCall.Do {
            val id = id  ?: !ApiException("Id Not found")
            !apiCallAuthValidateAdmin
            !apiCallStackDbQuery {

                val data = AuthProjection.User.selectAll()
                    .where { AuthProjection.User.id eq id }
                    .singleOrNull()
                    ?.toUserProjection

                val userInfo = OrganismProjection.OrganismUserInfoTable
                    .selectAll()
                    .where { OrganismProjection.OrganismUserInfoTable.userId eq id }
                    .map { it.toOrganismUserInfoProjection }

                data?.let {
                    AuthProjection.Aggregated(
                        data = it,
                        userInfo = userInfo
                    )
                }
            }
        }

        fun getByEmail(email: String) = ApiCall.Do {
            !apiCallAuthValidateAdmin
            !apiCallStackDbQuery {
                AuthProjection.User.selectAll()
                    .where { AuthProjection.User.email eq email }
                    .singleOrNull()
                    ?.toUserProjection
            }
        }

        fun list(role: String? = null) = ApiCall.Do {
            !apiCallAuthValidateAdmin
            !apiCallStackDbQuery {
                AuthProjection.User.selectAll()
                    .where {
                        role?.let { AuthProjection.User.roles inList listOf(listOf(it)) } ?:
                        Op.TRUE
                    }
                    .map { it.toUserProjection }
            }
        }


    }

}