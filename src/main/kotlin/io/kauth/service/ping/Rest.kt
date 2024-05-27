package io.kauth.service.ping

import io.kauth.monad.stack.AppStack
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

object Rest {

    val api = AppStack.Do {

        ktor.routing {
            get("/ping") {
                val response = !Api.ping
                call.respond(response)
            }
        }

    }
}