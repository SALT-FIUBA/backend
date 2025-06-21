package io.kauth

import io.kauth.client.brevo.Brevo
import io.kauth.client.eventStore.eventStoreClientNew
import io.kauth.client.eventStore.eventStoreClientPersistenceSubsNew
import io.kauth.exception.ApiException
import io.kauth.monad.stack.*
import io.kauth.service.accessrequest.AccessRequestService
import io.kauth.service.auth.AuthService
import io.kauth.service.deviceproject.DeviceProjectService
import io.kauth.service.fanpage.FanPageService
import io.kauth.service.salt.DeviceService
import io.kauth.service.mqtt.MqttConnectorService
import io.kauth.service.iotdevice.IoTDeviceService
import io.kauth.service.notification.NotificationService
import io.kauth.service.occasion.OccasionService
import io.kauth.service.organism.OrganismService
import io.kauth.service.organism.TrainService
import io.kauth.service.ping.PingService
import io.kauth.service.publisher.PublisherService
import io.kauth.service.reservation.ReservationService
import io.kauth.service.runServices
import io.kauth.util.AppLogger
import io.kauth.util.not
import io.kauth.util.setLogbackLevel
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
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

val runServices get() =
    runServices(
        ReservationService,
        AuthService,
        PingService,
        OrganismService,
        DeviceService,
        MqttConnectorService,
        PublisherService,
        IoTDeviceService,
        DeviceProjectService,
        TrainService,
        OccasionService,
        AccessRequestService,
        FanPageService,
        NotificationService
    )

val installKtorPlugins =
    AppStack.Do {

        val metricClient = !authStackMetrics
        val log = !authStackLog

        ktor.run {

            //Para que prometheus polle la data, ver de definirlo en otro lado, en el metric service
            routing {
                get("/metrics") {
                    call.respond(metricClient.scrape())
                }
            }

            //TODO config
            install(CORS) {
                anyHost()
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Options)
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
                allowCredentials = true
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
                    //send metrics

                    call.respond(
                        HttpStatusCode.BadRequest,
                        value
                    )

                }

                exception<Throwable> { call, value ->

                    log.error(value.stackTraceToString())
                    //send metrics

                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiException(value.message ?: value.localizedMessage)
                    )

                }
            }


        }


    }

fun Application.kauthApp() {
    runAppStack(
        AppStack.Do {

            !setLogbackLevel(appConfig.log.level)

            val eventstoreDbHost = appConfig.infra.db.eventstore.host

            //Root services (?
            !registerService(!eventStoreClientNew(eventstoreDbHost, serialization))
            !registerService(!eventStoreClientPersistenceSubsNew(eventstoreDbHost, serialization))
            !registerService(PrometheusMeterRegistry(PrometheusConfig.DEFAULT))
            !registerService(AppLogger(log))


            !installKtorPlugins

            !runServices

            log.info("Init app success")

        }
    )
}


fun main(args: Array<String>): Unit = EngineMain.main(args)