package io.kauth.service.salt

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.appStackSqlProjector
import io.kauth.service.organism.OrganismApi
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.json
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.upsert
import java.util.*


object DeviceProjection {

    object DeviceTable: Table("devices") {
        val id = text("id").uniqueIndex()
        val organismId = text("organism_id").nullable()
        val trainId = text("train_id").nullable()
        val organismName = text("organism_name").nullable()
        val seriesNumber = text("series_number")
        val ports = array<String>("ports")
        val status = text("status").nullable()
        val createdBy = text("created_by")
        val createdAt = timestamp("created_at")
        val command_topic = text("command_topic").nullable()
        val status_topic = text("status_topic").nullable()
        val state_topic = text("state_topic").nullable()
    }

    object DeviceCurrentDataTable: Table("devices_data") {
        val id = text("id").uniqueIndex()
        val organismId = text("organism_id")
        val speed = double("device_speed").nullable()
        val device_config = json<Device.Mqtt.SaltConfig>("device_config", Json).nullable()
        val current_action = json<Device.Mqtt.SaltAction>("current_action ", Json).nullable()
    }

    @Serializable
    data class Projection(
        val id: String,
        val organismId: String?,
        val trainId: String?,
        val organismName: String? = null,
        val seriesNumber: String,
        val ports: List<String>,
        val status: String?,
        val createdBy: String,
        val createdAt: Instant,
        val commandTopic: String?,
        val statusTopic: String?,
        val stateTopic: String?,
    )

    @Serializable
    data class DeviceCurrentDataProjection(
        val id: String,
        val organismId: String,
        val speed: Double?,
        val deviceConfig: Device.Mqtt.SaltConfig?,
        val deviceCurrentAction: Device.Mqtt.SaltAction?
    )

    val ResultRow.toDeviceProjection get() =
        Projection(
            this[DeviceTable.id],
            this[DeviceTable.organismId],
            this[DeviceTable.trainId],
            this[DeviceTable.organismName],
            this[DeviceTable.seriesNumber],
            this[DeviceTable.ports],
            this[DeviceTable.status],
            this[DeviceTable.createdBy],
            this[DeviceTable.createdAt],
            this[DeviceTable.command_topic],
            this[DeviceTable.status_topic],
            this[DeviceTable.state_topic]
        )

    //@Mati: DEVICE REAL TIME DATA -> Tiene que ser un stream aparte!
    val ResultRow.toDeviceCurrentDataProjection get() =
        DeviceCurrentDataProjection(
            this[DeviceCurrentDataTable.id],
            this[DeviceCurrentDataTable.organismId],
            this[DeviceCurrentDataTable.speed],
            this[DeviceCurrentDataTable.device_config],
            this[DeviceCurrentDataTable.current_action],
        )

    val sqlEventHandler = appStackSqlProjector<Device.Event>(
        streamName = "\$ce-device",
        consumerGroup = "device-sql-projection",
        tables = listOf(DeviceTable)
    ) { event ->
        AppStack.Do {
            val entity = UUID.fromString(event.retrieveId("device"))
            val state = !DeviceApi.Query.readState(entity) ?: return@Do
            val organism = state.organismId?.let { !OrganismApi.Query.readState(it) }

            !appStackDbQuery {
                DeviceTable.upsert() {
                    it[id] = entity.toString()
                    it[organismId] = state.organismId?.toString()
                    it[trainId] = state.trainId?.toString()
                    it[organismName] = organism?.name
                    it[seriesNumber] = state.seriesNumber
                    it[ports] = state.ports
                    it[status] = state.status
                    it[createdBy] = state.createdBy
                    it[createdAt] = state.createdAt
                    it[command_topic] = state.topics?.command
                    it[status_topic] = state.topics?.status
                    it[state_topic] = state.topics?.state
                }
            }
        }
    }

}