package io.kauth.service.mqtt

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.appStackDbQueryNeon
import io.kauth.service.mqtt.MqttProjection.toMqttDataProjection
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.selectAll


object MqttApi {
    object Query {
        fun list(
            topic: List<String>? = null
        ) = AppStack.Do {
            !appStackDbQueryNeon {
                MqttProjection.MqttMessages.selectAll()
                    .where {
                        MqttProjection.MqttMessages.topic.eq("test/1")
                        topic?.let { MqttProjection.MqttMessages.topic.inList(it) } ?: Op.TRUE
                    }
                    .map { it.toMqttDataProjection }
            }
        }
    }
}