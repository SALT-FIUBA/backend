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
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry

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
        val metricClient = !authStackMetrics
        val log = !authStackLog

        ktor.run {

            //Para que prometheus polle la data, ver de definirlo en otro lado, en el metric service
            routing {
                get("/metrics") {
                    call.respond(metricClient.scrape())
                }
            }

            install(MicrometerMetrics) {
                registry = metricClient
                meterBinders = listOf(
                    ClassLoaderMetrics(),
                    JvmMemoryMetrics(),
                    JvmGcMetrics(),
                    ProcessorMetrics(),
                    JvmThreadMetrics(),
                    FileDescriptorMetrics(),
                    UptimeMetrics()
                )
            }

            install(ContentNegotiation) { json(serialization) }

            install(StatusPages) {

                exception<ApiException> { call, value ->

                    log.error(value.stackTraceToString())

                    call.respond(
                        HttpStatusCode.BadRequest,
                        value
                    )

                }

                exception<Throwable> { call, value ->

                    log.error(value.stackTraceToString())

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

            !registerService(
                PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            )

            !setLogbackLevel(LoggerLevel.info)

            !registerService(log)

            !installKtorPlugins

            services.forEach {
                !it.start
            }

            log.info("Init app success")

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
