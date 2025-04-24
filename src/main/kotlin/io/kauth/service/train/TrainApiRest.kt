package io.kauth.service.train

import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.apicall.KtorCall
import io.kauth.monad.apicall.runApiCall
import io.kauth.monad.stack.AppStack
import io.kauth.serializer.UUIDSerializer
import io.kauth.service.auth.Auth
import io.kauth.service.salt.DeviceApi
import io.kauth.service.train.Train
import io.kauth.service.train.TrainApi
import io.kauth.util.not
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.*

object TrainApiRest {

    @Serializable
    data class CreateRequest(
        val name: String,
        val description: String,
        val seriesNumber: String,
        @Serializable(UUIDSerializer::class)
        val organism: UUID
    )

    val api = AppStack.Do {

        ktor.routing {

            route("train")  {

                post(path = "/create") {
                    val command = call.receive<CreateRequest>()
                    val result = !KtorCall(this@Do.ctx, call).runApiCall(
                        TrainApi.Command.create(
                            command.name,
                            command.description,
                            command.seriesNumber,
                            command.organism
                        )
                    )
                    call.respond(HttpStatusCode.Created, result)
                }

                get("/list") {
                    val result = !TrainApi.Query.trainList()
                    call.respond(HttpStatusCode.OK, result)
                }

                route("{id}") {

                    get() {
                        val id = call.parameters["id"] ?: !ApiException("Id Not found")
                        val organism = !TrainApi.Query.train(UUID.fromString(id)) ?: !ApiException("Organism not found")
                        call.respond(HttpStatusCode.OK, organism)
                    }

                    get("devices") {
                        val id = call.parameters["id"] ?: !ApiException("Id Not found")
                        val devices = !DeviceApi.Query.list(trainId = id)
                        call.respond(HttpStatusCode.OK, devices)
                    }

                }


            }

        }

    }
}