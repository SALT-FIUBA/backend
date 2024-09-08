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
        val role: String
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

                    route("register") {

                        post() {
                            //Internal register for authenticated users
                            val jwt = !call.auth
                            val command = call.receive<RegisterRequest>()
                            val result = !AuthApi.register(
                                command.email,
                                command.password,
                                command.personalData,
                                listOf(command.role),
                                jwt.payload.uuid
                            )
                            call.respond(HttpStatusCode.Created, result)
                        }

                        post(path = "admin") {
                            //!call.auth
                            //Do not expose this endpoint
                            val command = call.receive<RegisterRequest>()
                            val result = !AuthApi.register(
                                command.email,
                                command.password,
                                command.personalData,
                                listOf("admin")
                            )
                            call.respond(HttpStatusCode.Created, result)
                        }

                    }

                }

                post(path = "/register") {
                    val command = call.receive<RegisterRequest>()
                    val result = !AuthApi.register(
                        command.email,
                        command.password,
                        command.personalData,
                        listOf(command.role)
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

                route("user") {

                    get() {
                        !call.auth
                        val user = !AuthApi.readStateFromSession
                        call.respond(HttpStatusCode.OK, user)
                    }

                    get(path = "/{id}") {
                        !call.auth
                        val id = call.parameters["id"] ?: !ApiException("Id Not found")
                        val user = !AuthApi.Query.get(id) ?: !ApiException("User not found")
                        call.respond(HttpStatusCode.OK, user)
                    }

                    get(path = "/email/{email}") {
                        !call.auth
                        val id = call.parameters["email"] ?: !ApiException("Email Not found")
                        val user = !AuthApi.Query.getByEmail(id) ?: !ApiException("User not found")
                        call.respond(HttpStatusCode.OK, user)
                    }

                    get(path = "/list") {
                        !call.auth
                        val role = call.request.queryParameters["role"]
                        val users = !AuthApi.Query.list(role)
                        call.respond(HttpStatusCode.OK, users)
                    }

                }


            }


        }

    }


}