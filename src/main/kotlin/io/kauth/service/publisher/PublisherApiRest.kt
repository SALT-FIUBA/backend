package io.kauth.service.publisher

import io.kauth.abstractions.result.Output
import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.stack.AppStack
import io.kauth.service.auth.AuthApi.auth
import io.kauth.service.organism.OrganismApi
import io.kauth.service.organism.OrganismApiRest.CreateRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.util.*

object PublisherApiRest {

    @Serializable
    data class PublishRequest(
        @Contextual
        val messageId: UUID,
        val data: JsonElement,
        val resource: String,
        val channel: Publisher.Channel
    )

    val api = AppStack.Do {

        ktor.routing {

            route("publisher")  {

                post(path = "/publish") {
                    val command = call.receive<PublishRequest>()
                    val result = !PublisherApi.publish(
                        command.messageId,
                        command.data,
                        command.resource,
                        command.channel
                    )
                    call.respond(HttpStatusCode.Created, result)
                }


                get("{id}") {
                    val id = call.parameters["id"] ?: !ApiException("Id Not found")
                    val organism = !PublisherApi.readState(UUID.fromString(id)) ?: !ApiException("Message not found")
                    call.respond(HttpStatusCode.OK, organism)
                }

            }

        }

    }

}