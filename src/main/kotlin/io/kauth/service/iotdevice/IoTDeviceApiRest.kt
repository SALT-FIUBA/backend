package io.kauth.service.iotdevice

import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.apicall.KtorCall
import io.kauth.monad.apicall.runApiCall
import io.kauth.monad.stack.AppStack
import io.kauth.service.iotdevice.model.iotdevice.CapabilitySchema
import io.kauth.service.iotdevice.model.iotdevice.DeviceCommand
import io.kauth.service.iotdevice.model.iotdevice.TasmotaCapability
import io.kauth.service.iotdevice.model.iotdevice.TasmotaTopics
import io.kauth.util.not
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
        val caps: Map<String, CapabilitySchema<TasmotaCapability>>
    )

    @Serializable
    data class RegisterTuyaRequest(
        val name: String,
        val resource: String,
        val deviceId: String
    )

    @Serializable
    data class TuyaCommandRequest(
        val commands: List<DeviceCommand>
    )

    @Serializable
    data class EnabledRequest(
        val enabled: Boolean
    )

    val api = AppStack.Do {

        ktor.routing {

            route("iotdevice") {


                get(path = "/list") {
                    val result = !KtorCall(this@Do.ctx, call).runApiCall(IoTDeviceApi.Query.list())
                    call.respond(HttpStatusCode.OK, result)
                }

                post(path = "/register/tuya") {
                    val command = call.receive<RegisterTuyaRequest>()
                    val result = !KtorCall(this@Do.ctx, call).runApiCall(
                        IoTDeviceApi.registerTuyaIntegration(
                            name = command.name,
                            resource = command.resource,
                            tuyaDeviceId = command.deviceId
                        )
                    )
                    call.respond(HttpStatusCode.Created, result)
                }

                post(path = "/register/tasmota") {
                    val command = call.receive<RegisterRequest>()
                    val result = !KtorCall(this@Do.ctx, call).runApiCall(
                        IoTDeviceApi.registerTasmotaIntegration(
                            name = command.name,
                            resource = command.resource,
                            topics = command.topics,
                            caps = command.caps
                        )
                    )
                    call.respond(HttpStatusCode.Created, result)
                }

                route("{id}") {

                    get {
                        val deviceId = call.parameters["id"] ?: !ApiException("Id Not found")
                        val result = !KtorCall(this@Do.ctx, call).runApiCall(IoTDeviceApi.Query.get(deviceId))
                        if (result == null)
                            call.respond(HttpStatusCode.NotFound)
                        else
                            call.respond(HttpStatusCode.OK, result)
                    }

                    post(path = "/command") {
                        val command = call.receive<TuyaCommandRequest>()
                        val result = !KtorCall(this@Do.ctx, call).runApiCall(
                            IoTDeviceApi.sendCommand(
                                deviceId = UUID.fromString(call.parameters["id"] ?: !ApiException("Id Not found")),
                                cmds = command.commands
                            )
                        )
                        call.respond(HttpStatusCode.Created, result)
                    }


                    post(path = "/enabled") {
                        val command = call.receive<EnabledRequest>()
                        val result = !KtorCall(this@Do.ctx, call).runApiCall(
                            IoTDeviceApi.setEnabled(
                                deviceId = UUID.fromString(call.parameters["id"] ?: !ApiException("Id Not found")),
                                enabled = command.enabled
                            )
                        )
                        call.respond(HttpStatusCode.Created, result)
                    }
                }
            }
        }
    }

}