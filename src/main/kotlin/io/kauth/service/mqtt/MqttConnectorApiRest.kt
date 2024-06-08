package io.kauth.service.mqtt

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.getService
import io.kauth.service.auth.AuthApi.auth
import io.kauth.util.not
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

object MqttConnectorApiRest {

    val api = AppStack.Do {

        ktor.routing {

            route("mqtt")  {

                get("subscriptions") {
                    !call.auth
                    val mqtt = !getService<MqttConnectorService.Interface>()
                    val subs = !mqtt.mqtt.getSubscriptions
                    call.respond(HttpStatusCode.OK, subs.map { it.topicFilter })
                }

            }

        }

    }


}