package io.kauth.monad.stack

import io.kauth.client.eventStore.EventStoreClientPersistenceSubs
import io.kauth.client.eventStore.model.Event
import io.kauth.client.eventStore.subscribeToStream
import io.kauth.serializer.UUIDSerializer
import io.kauth.serializer.UnitSerializer
import io.kauth.service.auth.AuthService
import io.kauth.service.auth.jwt.Jwt
import io.kauth.util.AppLogger
import io.kauth.util.Async
import io.kauth.util.MutableClassMap
import io.kauth.util.not
import io.ktor.server.application.*
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import kotlin.reflect.KClass

data class AppStack<T>(
    val run: context(AppContext)() -> Async<T>
) {
    data class AppStackScope(val ctx: AppContext) {
        suspend operator fun <T> AppStack<T>.not() = bind(this)
        suspend fun <T> bind(app: AppStack<T>): T =
            !app.run(ctx)
    }
    companion object {
        context(AppContext) val ctx get() = this@AppContext
        fun <T> Do(block: suspend context(AppContext)AppStackScope.() -> T): AppStack<T> =
            AppStack(
                run = {
                    Async {
                        block(ctx, AppStackScope(ctx))
                    }
                }
            )
    }
}

fun <T> AppStack<T>.catching(): AppStack<Result<T>> =
    AppStack.Do {
        runCatching {
            !this@catching
        }
    }

fun <T> List<AppStack<T>>.sequential(): AppStack<List<T>> =
    AppStack.Do {
        this@sequential.map { !it }
    }

fun <T> List<AppStack<T>>.parallel(): AppStack<List<T>> =
    AppStack.Do {
        this@parallel
            .map { async { !it } }
            .map { it.await() }
    }

fun <T : Any> registerService(service: T) =
    AppStack.Do {
        !services.set(service::class, service)
        service
    }

fun <T : Any> registerService(key: KClass<T>, service: T) =
    AppStack.Do {
        !services.set(key, service)
        service
    }


inline fun <reified T : Any> getService() = AppStack.Do {
    !services.get(T::class) ?: error("[${T::class}] Service not found")
}

val authStackMetrics = AppStack.Do {
    !getService<PrometheusMeterRegistry>()
}

val authStackLog = AppStack.Do {
    !getService<AppLogger>()
}

val authStackJson = AppStack.Do {
    serialization
}

val authStackJwt = AppStack.Do {
    !getService<Jwt>()
}

fun <T> appStackDbQuery(block: Async<T>): AppStack<T> =
    AppStack.Do {
        newSuspendedTransaction(Dispatchers.IO, db = db) { !block }
    }

inline fun <reified T> appStackSqlProjector(
    streamName: String,
    consumerGroup: String,
    tables: List<Table>,
    crossinline onEvent: (Event<T>) -> AppStack<Unit>
) = AppStack.Do {
    !appStackDbQuery {
        tables.forEach {
            SchemaUtils.create(it)
        }
    }
    !appStackEventHandler<T>(streamName, consumerGroup, onEvent)
}

inline fun <reified T> appStackEventHandler(
    streamName: String,
    consumerGroup: String,
    crossinline onEvent: (Event<T>) -> AppStack<Unit>
) = AppStack.Do {
    val client = !getService<EventStoreClientPersistenceSubs>()
    !client.subscribeToStream<T>(streamName, consumerGroup) { event ->
        Async {
            !onEvent(event)
        }
    }
}

fun Application.runAppStack(stack: AppStack<*>) {

    val dbHost = environment
        .config
        .propertyOrNull("postgres.host")
        ?.getString()
        ?: "localhost:5432/salt"

    val db = Database.connect(
        url = "jdbc:postgresql://$dbHost",
        driver = "org.postgresql.Driver",
        user = "salt",
        password = "1234"
    )

    val context = AppContext(
        ktor = this,
        serialization = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            serializersModule = SerializersModule {
                contextual(UUIDSerializer)
                contextual(UnitSerializer)
            }
        },
        services = !MutableClassMap.new,
        db = db
    )

    runBlocking(coroutineContext) {
        !stack.run(context)
    }

}