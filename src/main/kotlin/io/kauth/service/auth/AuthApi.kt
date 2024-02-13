package io.kauth.service.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.stack.AuthStack
import io.kauth.monad.stack.getService
import io.kauth.service.auth.jwt.Jwt
import io.kauth.service.reservation.Reservation
import io.kauth.service.reservation.ReservationApi
import io.kauth.util.not
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

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

        val authService = !getService<AuthService.Interface>()

        val result = !ReservationApi.readState(email) ?: !ApiException("User does not exists")

        val user = !readState(UUID.fromString(result.ownerId)) ?: !ApiException("User does not exists")

        val passwordMatch = user.credentials.algorithm.checkValue(
            password,
            Auth.HashAlgorithm.HashedValue(user.credentials.passwordHash, user.credentials.salt)
        )

        if(!passwordMatch) !ApiException("Invalid credentials")

        val tokens = Auth.Tokens(
            access = !jwt(result.ownerId, user),
            refresh = null
        )

        !authService.command.handle(UUID.fromString(result.ownerId))(Auth.Command.UserLogin(tokens))

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

    fun jwt(
        id: String,
        user: Auth.User
    ) = AuthStack.Do {
        //TODO refreshtoken
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

    fun jwtVerify(
        jwt: String
    ) = AuthStack.Do {

        val verifier = JWT
            .require(!algorithm)
            .withIssuer("salt")
            .build()

        try {
            val jwt = verifier.verify(jwt)
            val claims = jwt.claims
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