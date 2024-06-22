package io.kauth.service.auth

import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.stack.AppStack
import io.kauth.service.auth.AuthApi.auth
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

object AuthApiRest {

    @Serializable
    data class RegisterRequest(
        val email: String,
        val password: String,
        val personalData: Auth.User.PersonalData,
        val role: Auth.Role
    )

    @Serializable
    data class LoginRequest(
        val email: String,
        val password: String,
    )

    val api = AppStack.Do {

        ktor.routing {

            route("auth")  {

                route("internal") {

                    post(path = "/register/admin") {
                        !call.auth
                        //Do not expose this endpoint
                        val command = call.receive<RegisterRequest>()
                        val result = !AuthApi.register(
                            command.email,
                            command.password,
                            command.personalData,
                            listOf(Auth.InternalRole.admin.name)
                        )
                        call.respond(HttpStatusCode.Created, result)
                    }

                }

                post(path = "/register") {
                    val command = call.receive<RegisterRequest>()
                    val result = !AuthApi.register(
                        command.email,
                        command.password,
                        command.personalData,
                        listOf(command.role.name)
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

                get(path = "/user/{id}") {
                    !call.auth
                    val id = call.parameters["id"] ?: !ApiException("Id Not found")
                    val user = !AuthApi.Query.get(id) ?: !ApiException("User not found")
                    call.respond(HttpStatusCode.OK, user)
                }

                get(path = "/user/email/{email}") {
                    !call.auth
                    val id = call.parameters["email"] ?: !ApiException("Email Not found")
                    val user = !AuthApi.Query.getByEmail(id) ?: !ApiException("User not found")
                    call.respond(HttpStatusCode.OK, user)
                }

                get(path = "/user/list") {
                    !call.auth
                    val users = !AuthApi.Query.list()
                    call.respond(HttpStatusCode.OK, users)
                }

            }


        }

    }


}