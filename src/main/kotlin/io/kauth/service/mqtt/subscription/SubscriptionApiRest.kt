package io.kauth.service.mqtt.subscription

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.sequential
import io.kauth.service.auth.AuthApi.auth
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable


object SubscriptionApiRest {

    @Serializable
    data class SubscribeRequest(
        val topic: String,
        val resource: String
    )

    @Serializable
    data class UnsubscribeRequest(
        val topics: List<String>,
    )

    val api = AppStack.Do {

        ktor.routing {

            route("mqtt/subscription")  {

                post("subscribe") {
                    !call.auth
                    val command = call.receive<SubscribeRequest>()
                    !SubscriptionApi.subscribeToTopic(command.topic, command.resource)
                    call.respond(HttpStatusCode.OK)
                }

                post("unsubscribe") {
                    !call.auth
                    val command = call.receive<UnsubscribeRequest>()
                    !command.topics
                        .map { SubscriptionApi.unsubscribeToTopic(it) }
                        .sequential()
                    call.respond(HttpStatusCode.OK)
                }

            }

        }

    }


}