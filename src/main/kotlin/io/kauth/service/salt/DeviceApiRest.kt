package io.kauth.service.salt

import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.stack.AppStack
import io.kauth.serializer.UUIDSerializer
import io.kauth.service.auth.AuthApi.auth
import io.kauth.service.publisher.PublisherApi
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
        val messageId: UUID,
        val action: Device.Mqtt.SaltAction
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

                get("/list") {
                    !call.auth
                    val result = !DeviceApi.Query.list()
                    call.respond(HttpStatusCode.OK, result)
                }


                route("{id}") {

                    post(path = "/command") {
                        !call.auth
                        val command = call.receive<MqttCommandRequest>()
                        val id = call.parameters["id"] ?: !ApiException("Id Not found")
                        val result = !DeviceApi.sendCommand(
                            UUID.fromString(id),
                            command.messageId,
                            command.action
                        )
                        call.respond(HttpStatusCode.Created, result)
                    }

                    get("/state") {
                        !call.auth
                        val id = call.parameters["id"] ?: !ApiException("Id Not found")
                        val device = !DeviceApi.Query.readState(UUID.fromString(id)) ?: !ApiException("Device not found")
                        call.respond(HttpStatusCode.OK, device)
                    }

                    get {
                        !call.auth
                        val id = call.parameters["id"] ?: !ApiException("Id Not found")
                        val device = !DeviceApi.Query.get(id) ?: !ApiException("Device not found")
                        call.respond(HttpStatusCode.OK, device)
                    }

                    get("messages") {
                        !call.auth
                        val id = call.parameters["id"] ?: !ApiException("Id Not found")
                        val messages = !PublisherApi.getByResource("device-${id}")
                        call.respond(HttpStatusCode.OK, messages)
                    }
                }


            }

        }

    }
}
