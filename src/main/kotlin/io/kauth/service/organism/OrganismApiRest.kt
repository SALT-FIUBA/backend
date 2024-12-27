package io.kauth.service.organism

import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.apicall.KtorCall
import io.kauth.monad.apicall.runApiCall
import io.kauth.monad.stack.AppStack
import io.kauth.service.auth.Auth
import io.kauth.util.not
import io.ktor.http.*
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
    data class CreateUsersRequest(
        @Contextual
        val organism: UUID,
        @Contextual
        val user: UUID,
        val role: Organism.Role,
        val email: String,
        val password: String,
        val personalData: Auth.User.PersonalData
    )

    val api = AppStack.Do {

        ktor.routing {

            route("organism")  {

                post(path = "/create") {
                    val command = call.receive<CreateRequest>()
                    val result = !OrganismApi.Command.create(
                        command.tag,
                        command.name,
                        command.description
                    )
                    call.respond(HttpStatusCode.Created, result)
                }


                route("users") {

                    post(path = "/create") {
                        val command = call.receive<CreateUsersRequest>()
                        val result = !KtorCall(this@Do.ctx, call).runApiCall(
                            OrganismApi.Command.createUser(
                                command.organism,
                                command.role,
                                command.email,
                                command.password,
                                command.personalData
                            )
                        )
                        call.respond(HttpStatusCode.Created, result)
                    }

                }


                get("/list") {
                    val result = !OrganismApi.Query.organismsList()
                    call.respond(HttpStatusCode.OK, result)
                }

                route("{id}") {

                    get() {
                        val id = call.parameters["id"] ?: !ApiException("Id Not found")
                        val organism = !OrganismApi.Query.organism(UUID.fromString(id)) ?: !ApiException("Organism not found")
                        call.respond(HttpStatusCode.OK, organism)
                    }

                    get("/supervisors") {
                        val id = call.parameters["id"] ?: !ApiException("Id Not found")
                        val result = !OrganismApi.Query.supervisorList(UUID.fromString(id))
                        call.respond(HttpStatusCode.OK, result)
                    }

                    get("/operators") {
                        val id = call.parameters["id"] ?: !ApiException("Id Not found")
                        val result = !OrganismApi.Query.operatorList(UUID.fromString(id))
                        call.respond(HttpStatusCode.OK, result)
                    }

                }


            }

        }

    }
}