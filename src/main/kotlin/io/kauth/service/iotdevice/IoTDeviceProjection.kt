package io.kauth.service.iotdevice

import io.kauth.monad.apicall.ApiCall
import io.kauth.monad.apicall.apiCallJson
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.appStackSqlProjector
import io.kauth.monad.stack.authStackJson
import io.kauth.service.iotdevice.model.iotdevice.Integration
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.upsert
import java.util.*

object IoTDeviceProjection {

    object IoTDeviceTable : Table("iot_devices") {
        val id = text("id").uniqueIndex()
        val name = text("name")
        val createdAt = timestamp("created_at")
        val resource = text("resource")
        val enabled = bool("enabled").nullable()
        val integration = text("integration")
        val state = text("state")
        val status = text("status").nullable()
    }

    @Serializable
    data class Projection(
        val id: String,
        val name: String,
        val resource: String,
        val createdAt: Instant,
        val enabled: Boolean?,
        val integration: Integration,
        val state: Map<String, IoTDevice.StateData<String>>,
        val status: DeviceStatus?
    )

    @Serializable
    enum class DeviceStatus {
        Online,
        Offline
    }

    val ResultRow.toMqttDeviceProjection: ApiCall<Projection>
        get() {
            val row = this
            return ApiCall.Do {
                val josn = !apiCallJson
                Projection(
                    row[IoTDeviceTable.id],
                    row[IoTDeviceTable.name],
                    row[IoTDeviceTable.resource],
                    row[IoTDeviceTable.createdAt],
                    row[IoTDeviceTable.enabled],
                    row[IoTDeviceTable.integration].let { josn.decodeFromString(it) },
                    row[IoTDeviceTable.state].let { josn.decodeFromString<Map<String, IoTDevice.StateData<String>>>(it)  },
                    row[IoTDeviceTable.status]?.let { DeviceStatus.valueOf(it) }
                )

            }
        }


    val sqlEventHandler = appStackSqlProjector<IoTDevice.Event>(
        streamName = "\$ce-${IoTDeviceService.STREAM_NAME}",
        consumerGroup = "iotdevice-sql-projection",
        tables = listOf(IoTDeviceTable)
    ) { event ->
        AppStack.Do {
            val entity = UUID.fromString(event.retrieveId(IoTDeviceService.STREAM_NAME))
            val actualState = !IoTDeviceApi.Query.readState(entity) ?: return@Do
            val json = !authStackJson

            val status = when (actualState.integration) {
                is Integration.Tasmota ->
                    actualState
                        .capabilitiesValues
                        .get(actualState.integration.topics.status)
                        ?.value
                        ?.let { json.decodeFromString<DeviceStatus>(it) }

                is Integration.Tuya -> null
            }

            !appStackDbQuery {
                IoTDeviceTable.upsert() { it ->
                    it[id] = entity.toString()
                    it[name] = actualState.name
                    it[resource] = actualState.resource
                    it[createdAt] = actualState.createdAt
                    it[enabled] = actualState.enabled
                    it[integration] = json.encodeToString(Integration.serializer(), actualState.integration)
                    it[state] = json.encodeToString(actualState.capabilitiesValues)
                    it[this.status] = status?.name
                }
            }
        }
    }
}