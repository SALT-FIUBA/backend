package io.kauth.service.auth

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

            route("auth") {

                route("internal") {

                    route("register") {

                        post() {
                            val command = call.receive<RegisterRequest>()
                            val result = !KtorCall(this@Do.ctx, call).runApiCall(
                                AuthApi.register(
                                    command.email,
                                    command.password,
                                    command.personalData,
                                    listOf(command.role)
                                )
                            )
                            call.respond(HttpStatusCode.Created, result)
                        }

                        post(path = "admin") {
                            val command = call.receive<RegisterRequest>()
                            val result = !KtorCall(this@Do.ctx, call).runApiCall(
                                AuthApi.register(
                                    command.email,
                                    command.password,
                                    command.personalData,
                                    listOf("admin")
                                )
                            )
                            call.respond(HttpStatusCode.Created, result)
                        }

                    }

                }

                post(path = "/register") {
                    val command = call.receive<RegisterRequest>()
                    val result = !KtorCall(this@Do.ctx, call).runApiCall(
                        AuthApi.register(
                            command.email,
                            command.password,
                            command.personalData,
                            listOf(command.role),
                        )
                    )
                    call.respond(HttpStatusCode.Created, result)
                }

                post(path = "/login") {
                    val command = call.receive<LoginRequest>()
                    val result = !KtorCall(this@Do.ctx, call).runApiCall(
                        AuthApi.login(
                            command.email,
                            command.password,
                        )
                    )
                    call.respond(HttpStatusCode.OK, result)
                }

                route("user") {

                    get() {
                        val user = !KtorCall(this@Do.ctx, call).runApiCall(AuthApi.readStateFromSession)
                        call.respond(HttpStatusCode.OK, user)
                    }

                    get(path = "/{id}") {
                        val id = call.parameters["id"] ?: !ApiException("Id Not found")
                        val user = !KtorCall(this@Do.ctx, call).runApiCall(AuthApi.Query.get(id))
                        if (user == null) {
                            call.respond(HttpStatusCode.NotFound)
                        } else {
                            call.respond(HttpStatusCode.OK, user)
                        }
                    }

                    get(path = "/email/{email}") {
                        val id = call.parameters["email"] ?: !ApiException("Email Not found")
                        val user = !KtorCall(this@Do.ctx, call).runApiCall(AuthApi.Query.getByEmail(id))
                        if (user == null) {
                            call.respond(HttpStatusCode.NotFound)
                        } else {
                            call.respond(HttpStatusCode.OK, user)
                        }
                    }

                    get(path = "/list") {
                        val role = call.request.queryParameters["role"]
                        val user = !KtorCall(this@Do.ctx, call).runApiCall(AuthApi.Query.list(role))
                        call.respond(HttpStatusCode.OK, user)
                    }

                }


            }


        }

    }


}