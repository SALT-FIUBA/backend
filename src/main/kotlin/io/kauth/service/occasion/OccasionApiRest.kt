package io.kauth.service.occasion

import io.kauth.monad.apicall.runApiCall
import io.kauth.monad.stack.AppStack
import io.kauth.service.occasion.OccasionApi.Command
import io.kauth.service.occasion.OccasionApi.Query
import io.kauth.exception.ApiException
import io.kauth.monad.apicall.KtorCall
import io.kauth.service.accessrequest.AccessRequestApi
import io.kauth.util.not
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDate

object OccasionApiRest {

    @Serializable
    data class CreateRequest(
        val categories: List<Occasion.Category>,
        val date: LocalDate,
        val description: String,
        val name: String? = null
    )

    val api = AppStack.Do {
        ktor.routing {
            route("occasion") {
                post("create") {
                    val request = call.receive<CreateRequest>()
                    val id = !KtorCall(this@Do.ctx, call).runApiCall(
                        Command.create(
                            categories = request.categories,
                            date = request.date,
                            description = request.description,
                            name = request.name
                        )
                    )
                    call.respond(HttpStatusCode.Created, mapOf("id" to id))
                }

                route("{id}") {

                    get() {
                        val idParam = call.parameters["id"] ?: throw ApiException("Id not found")
                        val id = java.util.UUID.fromString(idParam)
                        val occasion = !Query.readState(id) ?: throw ApiException("Occasion not found")
                        call.respond(HttpStatusCode.OK, occasion)
                    }

                    get("access-requests") {
                        val idParam = call.parameters["id"] ?: throw ApiException("Id not found")
                        val id = java.util.UUID.fromString(idParam)
                        val requests = !KtorCall(this@Do.ctx, call).runApiCall(
                            AccessRequestApi.Query.list(occasionId = id)
                        )
                        call.respond(HttpStatusCode.OK, requests)
                    }
                }

                get("/list") {
                    val occasions = !Query.list()
                    call.respond(HttpStatusCode.OK, occasions)
                }

            }
        }
    }
}
