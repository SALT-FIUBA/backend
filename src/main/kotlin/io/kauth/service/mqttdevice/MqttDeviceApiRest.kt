package io.kauth.service.mqttdevice

import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.stack.AppStack
import io.kauth.service.auth.AuthApi.auth
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.*

object MqttDeviceApiRest {

    @Serializable
    data class RegisterRequest(
        val name: String,
        val resource: String,
        val topics: MqttDevice.Topics
    )

    @Serializable
    data class CommandRequest(
        val message: String
    )

    val api = AppStack.Do {

        ktor.routing {

            route("mqttdevice") {

                post(path = "/register") {
                    !call.auth
                    val command = call.receive<RegisterRequest>()
                    val result = !MqttDeviceApi.register(
                        name = command.name,
                        resource = command.resource,
                        topics = command.topics
                    )
                    call.respond(HttpStatusCode.Created, result)
                }

                route("{id}") {

                    post(path = "/command") {
                        !call.auth
                        val command = call.receive<CommandRequest>()
                        val result = !MqttDeviceApi.sendCommand(
                            deviceId = UUID.fromString(call.parameters["id"] ?: !ApiException("Id Not found")),
                            data = command.message
                        )
                        call.respond(HttpStatusCode.Created, result)
                    }

                }


            }
        }
    }

}