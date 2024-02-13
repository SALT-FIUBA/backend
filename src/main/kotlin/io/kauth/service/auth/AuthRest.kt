package io.kauth.service.auth

import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.stack.AuthStack
import io.kauth.monad.stack.authStackKtor
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.*

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

    val ApplicationCall.jwt get() = AuthStack.Do {
        val authHeader = request.header("Authorization") ?: ""
        val token = "Bearer (?<token>.+)".toRegex().matchEntire(authHeader)?.groups?.get("token")?.value?.trim() ?: ""
        !AuthApi.jwtVerify(token) ?: !ApiException("UnAuthorized")
    }

    val api = AuthStack.Do {

        val ktor = !authStackKtor

        ktor.routing {

            route("auth")  {

                post(path = "/register") {
                    val command = call.receive<RegisterRequest>()
                    val id = UUID.randomUUID()
                    val result = !AuthApi.register(
                        id,
                        command.email,
                        command.password,
                        command.personalData
                    )
                    when(result) {
                        is Auth.Success -> call.respond(HttpStatusCode.Created, id)
                        is Auth.Error -> !ApiException(result.message)
                    }
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
                    val token = !call.jwt
                    val user = !AuthApi.readState(UUID.fromString(token.payload.id)) ?: !ApiException("User not found")
                    call.respond(HttpStatusCode.OK, user)
                }

            }


        }

    }


}