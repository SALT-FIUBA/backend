package io.kauth.service.iotdevice

import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.stack.AppStack
import io.kauth.service.auth.AuthApi.auth
import io.kauth.service.iotdevice.model.iotdevice.CapabilitySchema
import io.kauth.service.iotdevice.model.iotdevice.TasmotaCapability
import io.kauth.service.iotdevice.model.iotdevice.TasmotaTopics
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.*

object IoTDeviceApiRest {

    @Serializable
    data class RegisterRequest(
        val name: String,
        val resource: String,
        val topics: TasmotaTopics,
        val caps: List<CapabilitySchema<TasmotaCapability>>
    )

    @Serializable
    data class RegisterTuyaRequest(
        val name: String,
        val resource: String,
        val deviceId: String
    )

    @Serializable
    data class CommandRequest(
        val message: String
    )

    @Serializable
    data class TuyaCommandRequest(
        val code: String,
        val value: String
    )

    @Serializable
    data class EnabledRequest(
        val enabled: Boolean
    )

    val api = AppStack.Do {

        ktor.routing {

            route("iotdevice") {

                get(path = "/list") {
                    !call.auth
                    val result = !IoTDeviceApi.Query.list()
                    call.respond(HttpStatusCode.OK, result)
                }

                post(path = "/register/tuya") {
                    !call.auth
                    val command = call.receive<RegisterTuyaRequest>()
                    val result = !IoTDeviceApi.registerTuyaIntegration(
                        name = command.name,
                        resource = command.resource,
                        tuyaDeviceId = command.deviceId
                    )
                    call.respond(HttpStatusCode.Created, result)
                }

                post(path = "/register/tasmota") {
                    !call.auth
                    val command = call.receive<RegisterRequest>()
                    val result = !IoTDeviceApi.registerTasmotaIntegration(
                        name = command.name,
                        resource = command.resource,
                        topics = command.topics,
                        caps = command.caps
                    )
                    call.respond(HttpStatusCode.Created, result)
                }

                route("{id}") {


                    post(path = "/command") {
                        !call.auth
                        val command = call.receive<TuyaCommandRequest>()
                        val result = !IoTDeviceApi.sendCommand(
                            deviceId = UUID.fromString(call.parameters["id"] ?: !ApiException("Id Not found")),
                            data = command.value,
                            code = command.code
                        )
                        call.respond(HttpStatusCode.Created, result)
                    }


                    post(path = "/enabled") {
                        !call.auth
                        val command = call.receive<EnabledRequest>()
                        val result = !IoTDeviceApi.setEnabled(
                            deviceId = UUID.fromString(call.parameters["id"] ?: !ApiException("Id Not found")),
                            enabled = command.enabled
                        )
                        call.respond(HttpStatusCode.Created, result)
                    }


                }


            }
        }
    }

}