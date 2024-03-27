package io.kauth.service.organism

import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.stack.AuthStack
import io.kauth.monad.stack.authStackKtor
import io.kauth.service.auth.AuthApi.auth
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.*

object OrganismApiRest {

    @Serializable
    data class CreateRequest(
        val tag: String,
        val name: String,
        val description: String
    )

    val api = AuthStack.Do {

        val ktor = !authStackKtor

        ktor.routing {

            route("organism")  {

                post(path = "/create") {
                    !call.auth
                    val command = call.receive<CreateRequest>()
                    val result = !OrganismApi.create(
                        command.tag,
                        command.name,
                        command.description
                    )
                    call.respond(HttpStatusCode.Created, result)
                }

                get("{id}") {
                    !call.auth
                    val id = call.parameters["id"] ?: !ApiException("Id Not found")
                    val organism = !OrganismApi.readState(UUID.fromString(id)) ?: !ApiException("Organism not found")
                    call.respond(HttpStatusCode.OK, organism)
                }

            }

        }

    }
}