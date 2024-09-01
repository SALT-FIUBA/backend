package io.kauth.service.organism

import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.stack.AppStack
import io.kauth.service.auth.Auth
import io.kauth.service.auth.AuthApi.auth
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.*

object OrganismApiRest {

    @Serializable
    data class CreateRequest(
        val tag: String,
        val name: String,
        val description: String
    )

    @Serializable
    data class UserRequest(
        @Contextual
        val user: UUID,
        val role: Auth.Role,
        @Contextual
        val organism: UUID
    )

    val api = AppStack.Do {

        ktor.routing {

            route("organism")  {

                post(path = "/create") {
                    !call.auth
                    val command = call.receive<CreateRequest>()
                    val result = !OrganismApi.Command.create(
                        command.tag,
                        command.name,
                        command.description
                    )
                    call.respond(HttpStatusCode.Created, result)
                }

                post(path = "/user") {
                    !call.auth
                    val command = call.receive<UserRequest>()
                    val result = !OrganismApi.Command.addUser(
                        organism = command.organism,
                        role = command.role,
                        user = command.user
                    )
                    call.respond(HttpStatusCode.OK, result)
                }

                get("/list") {
                    !call.auth
                    val result = !OrganismApi.Query.organismsList()
                    call.respond(HttpStatusCode.OK, result)
                }

                route("{id}") {

                    get() {
                        !call.auth
                        val id = call.parameters["id"] ?: !ApiException("Id Not found")
                        val organism = !OrganismApi.Query.organism(UUID.fromString(id)) ?: !ApiException("Organism not found")
                        call.respond(HttpStatusCode.OK, organism)
                    }

                    get("/supervisors") {
                        !call.auth
                        val id = call.parameters["id"] ?: !ApiException("Id Not found")
                        val result = !OrganismApi.Query.supervisorList(UUID.fromString(id))
                        call.respond(HttpStatusCode.OK, result)
                    }

                    get("/operators") {
                        !call.auth
                        val id = call.parameters["id"] ?: !ApiException("Id Not found")
                        val result = !OrganismApi.Query.operatorList(UUID.fromString(id))
                        call.respond(HttpStatusCode.OK, result)
                    }

                }


            }

        }

    }
}