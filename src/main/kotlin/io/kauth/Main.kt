package io.kauth

import io.kauth.client.eventStore.eventStoreClientNew
import io.kauth.client.eventStore.eventStoreClientPersistenceSubsNew
import io.kauth.exception.ApiException
import io.kauth.monad.stack.*
import io.kauth.service.AppService
import io.kauth.service.auth.AuthService
import io.kauth.service.ping.PingService
import io.kauth.service.reservation.ReservationService
import io.kauth.util.LoggerLevel
import io.kauth.util.not
import io.kauth.util.setLogbackLevel
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlin.jvm.Throws

val services: List<AppService> =
    listOf(
        ReservationService,
        AuthService,
        PingService
    )

val installKtorPlugins =
    AuthStack.Do {

        val ktor = !authStackKtor
        val serialization = !authStackSerialization

        ktor.run {

            install(ContentNegotiation) { json(serialization) }

            install(StatusPages) {

                exception<ApiException> { call, value ->

                    println(value.stackTraceToString())

                    call.respond(
                        HttpStatusCode.BadRequest,
                        value
                    )

                }

                exception<Throwable> { call, value ->

                    println(value.stackTraceToString())

                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiException(value.message ?: value.localizedMessage)
                    )

                }
            }


        }


    }

fun Application.kauthApp() {
    runAuthStack(
        Dependency.Do {

            //Setup clients
            !registerService(
                !eventStoreClientNew("esdb://localhost:2113?tls=false")
            )

            !registerService(
                !eventStoreClientPersistenceSubsNew("esdb://localhost:2113?tls=false")
            )

            !setLogbackLevel(LoggerLevel.warn)

            !installKtorPlugins

            services.forEach {
                !it.start
            }

            println("OK")

        }
    )
}

fun main() {
    embeddedServer(
        factory = CIO,
        port = 8080,
        host = "0.0.0.0",
        module = Application::kauthApp
    )
        .start(wait = true)
}
