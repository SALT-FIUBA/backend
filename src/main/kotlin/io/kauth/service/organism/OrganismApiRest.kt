package io.kauth.service.organism

import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.apicall.KtorCall
import io.kauth.monad.apicall.runApiCall
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.authStackLog
import io.kauth.service.auth.Auth
import io.kauth.service.auth.AuthApi
import io.kauth.service.salt.DeviceApi
import io.kauth.service.train.TrainApi
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
    data class AddUser(
        @Contextual
        val organism: UUID,
        val email: String,
        val roles: List<Organism.Role>
    )

    val api = AppStack.Do {

        val log = !authStackLog

        ktor.routing {

            route("organism")  {

                post(path = "/create") {
                    val command = call.receive<CreateRequest>()
                    val result = !KtorCall(this@Do.ctx, call).runApiCall(
                        OrganismApi.Command.create(
                            command.tag,
                            command.name,
                            command.description
                        )
                    )
                    call.respond(HttpStatusCode.Created, result)
                }


                route("users") {

                    post(path = "/add") {
                        val command = call.receive<AddUser>()
                        val result = !KtorCall(this@Do.ctx, call).runApiCall(
                            OrganismApi.Command.addUser(
                                command.email,
                                command.organism,
                                command.roles
                            )
                        )
                        call.respond(HttpStatusCode.Created, result)
                    }

                }


                get("/list") {
                    log.info("List organisms")
                    val result = !OrganismApi.Query.organismsList()
                    call.respond(HttpStatusCode.OK, result)
                }

                route("{id}") {

                    get() {
                        val id = call.parameters["id"] ?: !ApiException("Id Not found")
                        val organism = !OrganismApi.Query.organism(UUID.fromString(id)) ?: !ApiException("Organism not found")
                        call.respond(HttpStatusCode.OK, organism)
                    }

                    get("trains") {
                        val id = call.parameters["id"] ?: !ApiException("Id Not found")
                        val devices = !TrainApi.Query.trainList(organismId = id)
                        call.respond(HttpStatusCode.OK, devices)
                    }

                    get("users") {
                        val id = call.parameters["id"] ?: !ApiException("Id Not found")
                        val result = !KtorCall(this@Do.ctx, call).runApiCall(
                            AuthApi.Query.list(
                                role =
                                    Organism.Role.entries.map { Organism.OrganismRole(it, UUID.fromString(id)).string }
                            )
                        )
                        call.respond(HttpStatusCode.OK, result)
                    }

                    post("delete") {
                        val id = UUID.fromString(call.parameters["id"] ?: throw ApiException("Missing id"))
                        val result = !KtorCall(this@Do.ctx, call).runApiCall(
                            OrganismApi.Command.delete(id)
                        )
                        call.respond(HttpStatusCode.OK, result)
                    }

                }


            }

        }

    }
}