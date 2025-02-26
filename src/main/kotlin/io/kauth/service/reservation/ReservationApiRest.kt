package io.kauth.service.reservation

import io.kauth.monad.apicall.KtorCall
import io.kauth.monad.apicall.runApiCall
import io.kauth.monad.apicall.toApiCall
import io.kauth.monad.stack.AppStack
import io.kauth.service.iotdevice.IoTDeviceApiRest.RegisterRequest
import io.kauth.util.not
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

object ReservationApiRest {

    @Serializable
    data class TakeRequest(
        val id: String,
        val owner: String
    )

    val api = AppStack.Do {
        ktor.routing {
            route("reservation") {

                post(path = "/take") {
                    val command = call.receive<TakeRequest>()
                    val result = !KtorCall(this@Do.ctx, call).runApiCall(
                        ReservationApi.take(command.id, command.owner).toApiCall()
                    )
                    call.respond(HttpStatusCode.OK, result)
                }

                post(path = "/release") {
                    val result = !KtorCall(this@Do.ctx, call).runApiCall(
                        ReservationApi.release(call.parameters["id"] ?: error("No id found")).toApiCall()
                    )
                    call.respond(HttpStatusCode.OK, result)
                }

            }
        }
    }

}
