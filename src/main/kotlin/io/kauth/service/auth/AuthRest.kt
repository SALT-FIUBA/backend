package io.kauth.service.auth

import io.kauth.monad.stack.AuthStack
import io.kauth.monad.stack.authStackKtor
import io.kauth.service.auth.AuthApi.auth
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

object AuthRest {

    @Serializable
    data class RegisterRequest(
        val email: String,
        val password: String,
        val personalData: Auth.User.PersonalData
    )

    @Serializable
    data class LoginRequest(
        val email: String,
        val password: String,
    )

    val api = AuthStack.Do {

        val ktor = !authStackKtor

        ktor.routing {

            route("auth")  {

                post(path = "/register") {
                    val command = call.receive<RegisterRequest>()
                    val result = !AuthApi.register(
                        command.email,
                        command.password,
                        command.personalData
                    )
                    call.respond(HttpStatusCode.Created, result)
                }

                post(path = "/login") {
                    val command = call.receive<LoginRequest>()
                    val result = !AuthApi.login(
                        command.email,
                        command.password,
                    )
                    call.respond(HttpStatusCode.OK, result)
                }

                get(path = "/user") {
                    !call.auth
                    val user = !AuthApi.readStateFromSession
                    call.respond(HttpStatusCode.OK, user)
                }

            }


        }

    }


}