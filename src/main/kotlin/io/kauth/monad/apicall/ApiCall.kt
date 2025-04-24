package io.kauth.monad.apicall

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kauth.exception.ApiException
import io.kauth.exception.allowIf
import io.kauth.exception.not
import io.kauth.monad.stack.AppContext
import io.kauth.monad.stack.AppStack
import io.kauth.service.auth.AuthService
import io.kauth.service.auth.jwt.Jwt
import io.kauth.util.AppLogger
import io.kauth.util.Async
import io.kauth.util.not
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

data class ApiCall<T>(
    val run: context(ApiCallContext)() -> Async<T>
) {
    data class AppStackScope(val ctx: ApiCallContext) {
        suspend operator fun <T> ApiCall<T>.not() = bind(this)
        suspend fun <T> bind(app: ApiCall<T>): T =
            !app.run(ctx)
    }
    companion object {
        context(ApiCallContext) val ctx get() = this@ApiCallContext
        fun <T> Do(block: suspend context(ApiCallContext)AppStackScope.() -> T): ApiCall<T> =
            ApiCall(
                run = {
                    Async {
                        block(ctx, AppStackScope(ctx))
                    }
                }
            )
    }
}

inline fun <reified T : Any> apiCallGetService() = ApiCall.Do {
    !ctx.app.services.get(T::class) ?: error("[${T::class}] Service not found")
}

val apiCallLog = ApiCall.Do {
    !apiCallGetService<AppLogger>()
}

val apiCallJwt = ApiCall.Do {
    jwt ?: !ApiException("No token found!")
}

val apiCallJson = ApiCall.Do {
    app.serialization
}

fun <T> AppStack<T>.toApiCall(): ApiCall<T> =
    ApiCall.Do {
        !this@toApiCall.run(ctx.app)
    }


fun <T : Any> registerService(service: T) =
    AppStack.Do {
        !services.set(service::class, service)
        service
    }

fun <T> apiCallStackDbQuery(block: Async<T>): ApiCall<T> =
     ApiCall.Do {
        newSuspendedTransaction(Dispatchers.IO, db = ctx.app.db) { !block }
    }

data class KtorCall(
    val ctx: AppContext,
    val call: RoutingCall
)

fun jwtVerify(jwt: String, alg: Algorithm): Jwt? {

    val verifier = JWT
        .require(alg)
        .withIssuer("salt")
        .build()

    try {
        val claims = verifier.verify(jwt).claims

        return Jwt(
            payload = Jwt.Payload(
                email = claims["email"]?.asString() ?: error("Invalid jwt, no email"),
                id = claims["id"]?.asString() ?: error("Invalid jwt, no id"),
                roles = claims["roles"]?.asList(String::class.java) ?: emptyList()
            )
        )

    } catch (e: Throwable) {
        return null
    }

}

private val regex = "Bearer (?<token>.+)".toRegex()

//Esto podria estar en el AuthApi... runAuthenticated
fun <T> KtorCall.runApiCall(call: ApiCall<T>) = Async {
    val authService = !this.ctx.services.get(AuthService.Interface::class)
    var mutableJwt: Jwt? = null
    //Esto me queda duda si tiene que estar aca
    if (authService != null) {
        val authHeader = this.call.request.header("Authorization") ?: ""

        val authCookie = this.call.request.cookies["token"]

        val token = authCookie ?: regex
            .matchEntire(authHeader)
            ?.groups?.get("token")
            ?.value?.trim() ?: ""

        val alg = Algorithm.HMAC256(authService.config.secret)
        val jwt = jwtVerify(token, alg)
        mutableJwt = jwt
    }
    !call.run(ApiCallContext(ctx, mutableJwt))
}

fun <T> KtorCall.runAdminCall(call: ApiCall<T>) =
    runApiCall(ApiCall.Do {
        val token = jwt ?: !ApiException("Un Authorized")
        !allowIf("admin" in token.payload.roles)
        !call
    })
