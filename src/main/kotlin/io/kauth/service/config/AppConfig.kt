package io.kauth.service.config

import io.kauth.util.LoggerLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AppConfig(
    val infra: Infra,
    val log: Log,
    val services: List<Service>
)

@Serializable
data class Infra(
    val db: Db
)

@Serializable
data class Db(
    val postgres: Postgres,
    val neon: Postgres?,
    val eventstore: Eventstore
)

@Serializable
data class Postgres(
    val host: String,
    val driver: String,
    val user: String,
    val password: String
)

@Serializable
data class Eventstore(
    val host: String
)

@Serializable
data class Log(
    val level: LoggerLevel
)

@Serializable
data class Service(
    val name: String,
    val config: JsonObject
)