package io.kauth.service.salt

import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.stack.AppStack
import io.kauth.serializer.UUIDSerializer
import io.kauth.service.auth.AuthApi.auth
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.*

object DeviceApiRest {

    @Serializable
    data class CreateRequest(
        @Serializable(UUIDSerializer::class)
        val organismId: UUID,
        val seriesNumber: String,
        val ports: List<String>,
        val topics: Device.Topics
    )

    @Serializable
    data class MqttCommandRequest(
        @Serializable(UUIDSerializer::class)
        val deviceId: UUID,
        @Serializable(UUIDSerializer::class)
        val messageId: UUID,
        val message: String,
    )

    val api = AppStack.Do {

        ktor.routing {

            route("device")  {

                post(path = "/create") {
                    !call.auth
                    val command = call.receive<CreateRequest>()
                    val result = !DeviceApi.create(
                        command.organismId,
                        command.seriesNumber,
                        command.ports,
                        command.topics
                    )
                    call.respond(HttpStatusCode.Created, result)
                }

                post(path = "/command") {
                    !call.auth
                    val command = call.receive<MqttCommandRequest>()
                    val result = !DeviceApi.sendCommand(
                        command.deviceId,
                        command.messageId,
                        command.message,
                    )
                    call.respond(HttpStatusCode.Created, result)
                }

                get("/list") {
                    !call.auth
                    val result = !DeviceApi.Query.list()
                    call.respond(HttpStatusCode.OK, result)
                }

                get("/state/{id}") {
                    !call.auth
                    val id = call.parameters["id"] ?: !ApiException("Id Not found")
                    val device = !DeviceApi.Query.readState(UUID.fromString(id)) ?: !ApiException("Device not found")
                    call.respond(HttpStatusCode.OK, device)
                }

                get("/{id}") {
                    !call.auth
                    val id = call.parameters["id"] ?: !ApiException("Id Not found")
                    val device = !DeviceApi.Query.get(id) ?: !ApiException("Device not found")
                    call.respond(HttpStatusCode.OK, device)
                }

            }

        }

    }
}
