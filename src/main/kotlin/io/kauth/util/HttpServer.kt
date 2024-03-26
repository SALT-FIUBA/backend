package io.kauth.util

import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.stack.AuthStack
import io.kauth.service.auth.AuthApi
import io.ktor.server.application.*
import io.ktor.server.request.*


object HttpServer {

    val ApplicationCall.auth get() = AuthStack.Do {
        val authHeader = request.header("Authorization") ?: ""
        val token = "Bearer (?<token>.+)"
            .toRegex()
            .matchEntire(authHeader)
            ?.groups?.get("token")
            ?.value?.trim() ?: ""
        !AuthApi.jwtVerify(token) ?: !ApiException("UnAuthorized")
    }

}