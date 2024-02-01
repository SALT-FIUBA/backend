package io.kauth.service.ping

import io.kauth.monad.stack.AuthStack
import io.kauth.monad.stack.authStackKtor
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

object Rest {

    val api = AuthStack.Do {

        val ktor = !authStackKtor

        ktor.routing {
            get("/ping") {
                val response = !Api.ping
                call.respond(response)
            }
        }

    }
}