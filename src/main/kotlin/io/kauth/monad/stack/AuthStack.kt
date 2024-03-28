package io.kauth.monad.stack

import io.kauth.serializer.UUIDSerializer
import io.kauth.service.auth.jwt.Jwt
import io.kauth.util.Async
import io.kauth.util.LoggerLevel
import io.kauth.util.MutableClassMap
import io.kauth.util.not
import io.ktor.server.application.*
import io.ktor.util.logging.*
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.slf4j.Logger
import java.util.*
import kotlin.reflect.KClass

data class AuthStack<out T>(
    override val run: (context: Context) -> Async<T>
): Dependency<AuthStack.Companion.Context, T> {

    companion object {

        val <T> Dependency<Context, T>.asAuthStack: AuthStack<T> get() = AuthStack(run = run)

        data class Context(
            val ktor: Application,
            val serialization: Json,
            val services: MutableClassMap,
        ): CoroutineScope by ktor

        fun <T> Do(block: suspend DependencyContext<Context>.() -> T): AuthStack<T> =
            AuthStack(run = Dependency.Do(block).run)
    }

}

fun <T : Any> registerService(service: T) =
    AuthStack.Do {
        val services = !authStackServices
        !services.set(service::class, service)
        service
    }

fun <T : Any> registerService(key: KClass<T>, service: T) =
    AuthStack.Do {
        val services = !authStackServices
        !services.set(key, service)
        service
    }


inline fun <reified T : Any> getService() = AuthStack.Do {
    val services = !authStackServices
    !services.get(T::class) ?: error("[${T::class}] Service not found")
}

val authStackServices = AuthStack.Do {
    val context = !Dependency.read<AuthStack.Companion.Context>()
    context.services
}

val authStackKtor = AuthStack.Do {
    val context = !Dependency.read<AuthStack.Companion.Context>()
    context.ktor
}

val authStackSerialization = AuthStack.Do {
    val context = !Dependency.read<AuthStack.Companion.Context>()
    context.serialization
}

val authStackMetrics = AuthStack.Do {
    !getService<PrometheusMeterRegistry>()
}

val authStackLog = AuthStack.Do {
    !getService<ch.qos.logback.classic.Logger>()
}

val authStackJson = AuthStack.Do {
    val services = !authStackServices
    println(services)
    !getService<Json>()
}

val authStackJwt = AuthStack.Do {
    !getService<Jwt>()
}


fun Application.runAuthStack(stack: Dependency<AuthStack.Companion.Context, *>) {
    val context = AuthStack.Companion.Context(
        ktor = this,
        serialization =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                serializersModule = SerializersModule {
                    contextual(UUID::class, UUIDSerializer)
                }
            },
        services = !MutableClassMap.new
    )
    runBlocking(coroutineContext) {
        !stack.run(context)
    }
}