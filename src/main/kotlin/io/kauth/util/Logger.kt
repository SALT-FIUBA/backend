package io.kauth.util

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory

enum class LoggerLevel {
    error,
    warn
}

fun setLogbackLevel(level: LoggerLevel = LoggerLevel.warn) = IO {
    val rootLogger: Logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
    rootLogger.setLevel(
        Level.toLevel(
            when(level) {
                LoggerLevel.error -> "error"
                LoggerLevel.warn -> "warn"
            }
        )
    )
}

