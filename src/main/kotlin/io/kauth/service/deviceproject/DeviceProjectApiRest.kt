package io.kauth.service.deviceproject

import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.apicall.KtorCall
import io.kauth.monad.apicall.runApiCall
import io.kauth.monad.stack.AppStack
import io.kauth.util.not
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

object DeviceProjectApiRest {

    @Serializable
    data class DeviceProjectCreateRequest(
        val name: String
    )

    @Serializable
    data class TuyaDeviceCreateRequest(
        val name: String,
        val tuyaId: String
    )

    @Serializable
    data class EnabledRequest(
        val enabled: Boolean
    )

    val api = AppStack.Do {
        ktor.routing {
            route("device-project") {

                post(path = "create") {
                    val command = call.receive<DeviceProjectCreateRequest>()
                    val response = !KtorCall(this@Do.ctx, call).runApiCall(DeviceProjectApi.create(command.name))
                    call.respond(response)
                }

                get(path = "list") {
                    val response = !KtorCall(this@Do.ctx, call).runApiCall(DeviceProjectApi.Query.list())
                    call.respond(response)
                }

                route("{id}") {

                    post(path = "tuya/device") {
                        val projectId = call.parameters["id"] ?: !ApiException("Id Not found")
                        val uuid = UUID.fromString(projectId)
                        val command = call.receive<TuyaDeviceCreateRequest>()
                        val response = !KtorCall(this@Do.ctx, call).runApiCall(
                            DeviceProjectApi.addTuyaDevice(
                                uuid,
                                command.name,
                                command.tuyaId
                            )
                        )
                        call.respond(response)
                    }

                    post(path = "enabled") {
                        val projectId = call.parameters["id"] ?: !ApiException("Id Not found")
                        val uuid = UUID.fromString(projectId)
                        val command = call.receive<EnabledRequest>()
                        val response = !KtorCall(this@Do.ctx, call).runApiCall(
                            DeviceProjectApi.setEnable(
                                uuid,
                                command.enabled
                            )
                        )
                        call.respond(response)
                    }

                    get(path = "devices") {
                        val projectId = call.parameters["id"] ?: !ApiException("Id Not found")
                        val uuid = UUID.fromString(projectId)
                        val response = !KtorCall(this@Do.ctx, call).runApiCall(DeviceProjectApi.Query.listDevices(uuid))
                        call.respond(response)
                    }

                }


            }
        }
    }
}