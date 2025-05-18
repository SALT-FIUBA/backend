package io.kauth.service

import io.kauth.monad.stack.AppStack
import java.util.*

interface AppService {
    val name: String get() = this::class.simpleName ?: "Unknown"
    val start: AppStack<*>
}

fun runServices(vararg services: AppService): AppStack<*> = AppStack.Do {
    services.filter { it.name in appConfig.services.map { it.name } }.forEach {
        !it.start
    }
}


interface EventStoreService : AppService {
    val STREAM_PREFIX: String
        get() = "${name}-"
    val SNAPSHOT_STREAM_PREFIX : String
        get() = "${name}_snapshot-"

    val UUID.streamName get() = STREAM_PREFIX + this.toString()
    val UUID.snapshotName get() = SNAPSHOT_STREAM_PREFIX + this.toString()
}


fun consumerGroupName(vararg service: EventStoreService): String =
    "${service.joinToString("-") { it.name }}-consumer-group"

fun allStreamsName(service: EventStoreService): String = "\$ce-${service.name}"