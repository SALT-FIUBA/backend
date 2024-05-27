package io.kauth.monad.stack

import io.kauth.util.MutableClassMap
import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

data class AppContext(
    val ktor: Application,
    val serialization: Json,
    val services: MutableClassMap,
): CoroutineScope by ktor