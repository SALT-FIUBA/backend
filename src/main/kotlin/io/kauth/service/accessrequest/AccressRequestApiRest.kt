package io.kauth.service.accessrequest

import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.apicall.KtorCall
import io.kauth.monad.apicall.runApiCall
import io.kauth.monad.stack.AppStack
import io.kauth.util.not
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID

object AccessRequestApiRest {

    @Serializable
    data class CreateRequest(
        @Contextual
        val occasionId: UUID,
        val categoryName: String,
        val description: String,
        val places: Int
    )

    @Serializable
    data class AcceptRequest(
        @Contextual
        val id: UUID
    )

    @Serializable
    data class ConfirmRequest(
        @Contextual
        val id: UUID
    )

    @Serializable
    data class CancelRequest(
        @Contextual
        val id: UUID
    )

    val api = AppStack.Do {
        ktor.routing {
            route("access-request") {

                post("/create") {
                    val request = call.receive<CreateRequest>()
                    val result = !KtorCall(this@Do.ctx, call).runApiCall(
                        AccessRequestApi.Command.create(
                            occasionId = request.occasionId,
                            userId = null,
                            categoryName = request.categoryName,
                            description = request.description,
                            places = request.places
                        )
                    )
                    call.respond(HttpStatusCode.Created, result)
                }

                post("/accept") {
                    val request = call.receive<AcceptRequest>()
                    val result = !KtorCall(this@Do.ctx, call).runApiCall(
                        AccessRequestApi.Command.accept(
                            id = request.id
                        )
                    )
                    call.respond(HttpStatusCode.OK, result)
                }

                post("/confirm") {
                    val request = call.receive<ConfirmRequest>()
                    val result = !KtorCall(this@Do.ctx, call).runApiCall(
                        AccessRequestApi.Command.confirm(id = request.id)
                    )
                    call.respond(HttpStatusCode.OK, result)
                }

                post("/cancel") {
                    val request = call.receive<CancelRequest>()
                    val result = !KtorCall(this@Do.ctx, call).runApiCall(
                        AccessRequestApi.Command.cancel(
                            id = request.id
                        )
                    )
                    call.respond(HttpStatusCode.OK, result)
                }

                get("/list") {
                    val result = !KtorCall(this@Do.ctx, call).runApiCall(
                        AccessRequestApi.Query.list()
                    )
                    call.respond(HttpStatusCode.OK, result)
                }

                get("/{id}") {
                    val idParam = call.parameters["id"] ?: throw ApiException("Id Not found")
                    val result = !AccessRequestApi.Query.readState(UUID.fromString(idParam)) ?: !ApiException("Request Not found")
                    call.respond(HttpStatusCode.OK, result)
                }
            }
        }
    }
}
