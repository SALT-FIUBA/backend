package io.kauth.service.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.client.google.Google
import io.kauth.client.google.exchangeCodeForToken
import io.kauth.client.google.fetchUserData
import io.kauth.exception.ApiException
import io.kauth.exception.allowIf
import io.kauth.exception.not
import io.kauth.monad.apicall.*
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.getService
import io.kauth.service.auth.AuthProjection.toUserProjection
import io.kauth.service.reservation.ReservationApi
import io.kauth.util.*
import io.ktor.http.*
import io.ktor.server.response.*
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.CustomOperator
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.json.contains
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

object AuthApi {

    fun addRoles(
        email: String,
        roles: List<String>
    ) = ApiCall.Do {
        val data = !ReservationApi.readState("user-${email}").toApiCall() ?: error("Not found user")
        val authService = !apiCallGetService<AuthService.Interface>()
        !authService.command
            .handle(UUID.fromString(data.ownerId))
            .throwOnFailureHandler(Auth.Command.AddRoles(roles))
            .toApiCall()
        email
    }

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
                    credentials = null,
                    credentialSource = Auth.Credentials(
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

    private fun registerGoogle(
        email: String,
        personalData: Auth.User.PersonalData,
        roles: List<String>,
        googleId: String
    ) = ApiCall.Do {
        val log = !apiCallLog
        log.info("Register user $email")
        val authService = !apiCallGetService<AuthService.Interface>()
        val id = !ReservationApi.takeIfNotTaken("user-${email}") { UUID.randomUUID().toString() }.toApiCall()
        !authService.command
            .handle(UUID.fromString(id))
            .throwOnFailureHandler(
                Auth.Command.CreateUser(
                    email = email,
                    credentials = null,
                    credentialSource = Auth.Google(googleId = googleId),
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

        val credentials = user.credentials ?: user.credentialSource as? Auth.Credentials ?: !ApiException("Invalid login")

        !authService.command
            .handle(UUID.fromString(result.ownerId))
            .throwOnFailureHandler(
                Auth.Command.UserLogin(
                    credentials.algorithm.hashString(password,credentials.salt.byteArray).value
                ),
            )
            .toApiCall()

        val tokens = Auth.Tokens(
            access = !buildJwt(result.ownerId, user.email, user.roles),
            refresh = null
        )

        tokens

    }

    fun googleLogin(
        code: String
    ) = ApiCall.Do {

        val google = !apiCallGetService<Google.Client>()

        val tokenResponse = !google.exchangeCodeForToken(code)
        val userInfo = !google.fetchUserData(tokenResponse.accessToken)

        val email = userInfo.email

        val authService = !apiCallGetService<AuthService.Interface>()

        val result = !ReservationApi.readState("user-$email").toApiCall()

        val id = result?.ownerId ?: run {
            !registerGoogle(
               email = email,
                personalData = Auth.User.PersonalData(
                    firstName = userInfo.name,
                    lastName = userInfo.family_name,
                ),
                roles = emptyList(),
                googleId = userInfo.sub
            )
        }

        val user = !Query.readState(UUID.fromString(id)).toApiCall() ?: !ApiException("User does not exists")

        if (user.credentialSource !is Auth.Google) {
            !ApiException("Invalid login")
        }

        !authService.command
            .handle(UUID.fromString(id))
            .throwOnFailureHandler(
                Auth.Command.UserLogin(null),
            )
            .toApiCall()

        val tokens = Auth.Tokens(
            access = !buildJwt(id, user.email, user.roles),
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
        email: String,
        roles: List<String>
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
            .withClaim("email",email)
            .withClaim("id", id)
            .withClaim("roles",roles)
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
            val id = id ?: !ApiException("Id Not found")
            //!apiCallAuthValidateAdmin
            !apiCallStackDbQuery {
                AuthProjection.User.selectAll()
                    .where { AuthProjection.User.id eq id }
                    .singleOrNull()
                    ?.toUserProjection
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

        fun list(role: List<String>? = null) = ApiCall.Do {
            //!apiCallAuthValidateAdmin
            !apiCallStackDbQuery {
                AuthProjection.User.selectAll().where {
                    if (role.isNullOrEmpty()) {
                        Op.TRUE
                    } else {
                        object : Op<Boolean>() {
                            override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                                val arrayLiteral = role.joinToString(",") { "'$it'" }
                                queryBuilder.append("roles && ARRAY[$arrayLiteral]::text[]")
                            }
                        }
                    }
                }.map { it.toUserProjection }
            }
        }
    }

}