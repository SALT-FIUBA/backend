package io.kauth.service.auth

import io.kauth.client.google.Google
import io.kauth.client.google.exchangeCodeForToken
import io.kauth.client.google.fetchUserData
import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.apicall.KtorCall
import io.kauth.monad.apicall.runApiCall
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.findConfig
import io.kauth.monad.stack.getService
import io.kauth.service.auth.AuthService.name
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
    data class AddRolesRequest(
        val email: String,
        val roles: List<String>
    )

    @Serializable
    data class LoginRequest(
        val email: String,
        val password: String,
    )

    val api = AppStack.Do {


        ktor.routing {

            route("auth") {

                route("google") {

                    get {

                        val google = !getService<Google.Client>()
                        val scope = "openid%20email%20profile"
                        val authUrl = "https://accounts.google.com/o/oauth2/v2/auth" +
                                "?client_id=${google.clientId}" +
                                "&redirect_uri=${google.redirectUri}" +
                                "&response_type=code" +
                                "&scope=$scope" +
                                "&access_type=offline"

                        call.respondRedirect(authUrl)
                    }

                    get("/callback") {
                        val authConfig = !findConfig<AuthConfig>(name)
                        try {
                            val code = call.parameters["code"] ?: return@get call.respondText("No code", status = HttpStatusCode.BadRequest)
                            val result = !KtorCall(this@Do.ctx, call).runApiCall(AuthApi.googleLogin(code = code))
                            call.response.cookies.append(
                                Cookie(
                                    name = "token",
                                    value = result.access,
                                    httpOnly = true,
                                    secure = true,
                                    path = "/",
                                    extensions = mapOf("SameSite" to "None")
                                )
                            )
                            call.respondRedirect((authConfig?.frontend ?: "http://localhost:5173/"))
                        } catch (e: Throwable) {
                            call.respondRedirect((authConfig?.frontend ?: "http://localhost:5173/") + "login")
                        }
                    }

                }

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

                    post(path = "roles") {
                        val command = call.receive<AddRolesRequest>()
                        val result = !KtorCall(this@Do.ctx, call).runApiCall(
                            AuthApi.addRoles(
                                command.email,
                                command.roles
                            )
                        )
                        call.respond(HttpStatusCode.Created, result)
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
                    call.response.cookies.append(Cookie(name = "token", value = result.access, httpOnly = true, secure = false, path = "/"))
                    call.respond(HttpStatusCode.OK, result)
                }

                post(path = "/logout") {
                    call.response.cookies.append(Cookie(name = "token", value = "", httpOnly = true, secure = false, path = "/"))
                    call.respond(HttpStatusCode.OK)
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
                        val role = call.request.queryParameters["role"]?.split(",")
                        val user = !KtorCall(this@Do.ctx, call).runApiCall(AuthApi.Query.list(role))
                        call.respond(HttpStatusCode.OK, user)
                    }

                }


            }


        }

    }


}