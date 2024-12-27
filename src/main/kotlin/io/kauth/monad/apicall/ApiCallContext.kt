package io.kauth.monad.apicall

import io.kauth.monad.stack.AppContext
import io.kauth.service.auth.jwt.Jwt
import io.ktor.server.routing.*

data class ApiCallContext(
    val app: AppContext,
    val jwt: Jwt?
)
