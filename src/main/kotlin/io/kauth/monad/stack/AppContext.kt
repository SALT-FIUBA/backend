package io.kauth.monad.stack

import io.kauth.service.config.AppConfig
import io.kauth.util.MutableClassMap
import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database

data class AppContext(
    val ktor: Application,
    val serialization: Json,
    val services: MutableClassMap,
    val db: Database,
    val neonDb: Database? = null,
    val appConfig: AppConfig
): CoroutineScope by ktor