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
import kotlinx.datetime.DayOfWeek
import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Contextual
import java.util.UUID

object OccasionApiRest {

    @Serializable
    data class CreateRequest(
        val categories: List<Occasion.Category>,
        val date: LocalDateTime? = null,
        val description: String,
        val name: String,
        @Contextual
        val fanPageId: UUID,
        val startDateTime: LocalDateTime? = null,
        val endDateTime: LocalDateTime? = null,
        val weekdays: List<DayOfWeek>? = null,
        val recurringEndDateTime: LocalDateTime? = null,
        val totalCapacity: Int? = null,
    )

    @Serializable
    data class VisibilityRequest(
        @Contextual
        val id: UUID,
        val disabled: Boolean,
    )

    val api = AppStack.Do {
        ktor.routing {
            route("occasion") {
                post("create") {
                    val request = call.receive<CreateRequest>()
                    val id = !KtorCall(this@Do.ctx, call).runApiCall(
                        Command.create(
                            categories = request.categories,
                            description = request.description,
                            name = request.name,
                            fanPageId = request.fanPageId,
                            startDateTime = request.startDateTime,
                            endDateTime = request.endDateTime,
                            weekdays = request.weekdays,
                            recurringEndDateTime = request.recurringEndDateTime,
                            totalCapacity = request.totalCapacity,
                        )
                    )
                    call.respond(HttpStatusCode.Created, mapOf("id" to id))
                }

                route("{id}") {

                    get() {
                        val idParam = call.parameters["id"] ?: throw ApiException("Id not found")
                        val id = UUID.fromString(idParam)
                        val occasion = !Query.get(id) ?: throw ApiException("Occasion not found")
                        call.respond(HttpStatusCode.OK, occasion)
                    }

                    get("access-requests") {
                        val idParam = call.parameters["id"] ?: throw ApiException("Id not found")
                        val id = UUID.fromString(idParam)
                        val requests = !KtorCall(this@Do.ctx, call).runApiCall(
                            AccessRequestApi.Query.list(occasionId = id)
                        )
                        call.respond(HttpStatusCode.OK, requests)
                    }

                    post("visibility") {
                        val request = call.receive<VisibilityRequest>()
                        val occasion = !KtorCall(this@Do.ctx, call).runApiCall(
                            Command.visibility(request.id, request.disabled)
                        )
                        call.respond(HttpStatusCode.OK, occasion)
                    }


                }

                get("/list") {
                    val fanPageId = call.request.queryParameters["fanPageId"]?.let { UUID.fromString(it) }
                    val occasions = !Query.list(fanPageId = fanPageId)
                    call.respond(HttpStatusCode.OK, occasions)
                }

            }
        }
    }
}
