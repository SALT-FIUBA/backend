package io.kauth.service.fanpage

import io.kauth.exception.ApiException
import io.kauth.monad.apicall.KtorCall
import io.kauth.monad.apicall.runApiCall
import io.kauth.monad.stack.AppStack
import io.kauth.util.not
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.*

object FanPageApiRest {

    @Serializable
    data class CreateRequest(
      val  description: String,
      val  name: String,
      val  profilePhoto: String,
      val  location: String,
      val  email: String,
      val  phone: String,
      val  website: String
    )

    val api = AppStack.Do {
        ktor.routing {

            route("fanpage") {

                post("create") {
                    val request = call.receive<CreateRequest>()
                    val id = !KtorCall(this@Do.ctx, call).runApiCall(
                        FanPageApi.Command.create(
                            description = request.description,
                            name = request.name,
                            profilePhoto = request.profilePhoto,
                            location = request.location,
                            email = request.email,
                            phone = request.phone,
                            website = request.website
                        )
                    )
                    call.respond(HttpStatusCode.Created, mapOf("id" to id))
                }

                route("{id}") {
                    get() {
                        val idParam = call.parameters["id"] ?: throw ApiException("Id not found")
                        val id = UUID.fromString(idParam)
                        val fanpage = !FanPageApi.Query.readState(id) ?: throw ApiException("FanPage not found")
                        call.respond(HttpStatusCode.OK, fanpage)
                    }

                }

                get("/list") {
                    val fanpages = !FanPageApi.Query.list()
                    call.respond(HttpStatusCode.OK, fanpages)
                }

            }
        }
    }

}