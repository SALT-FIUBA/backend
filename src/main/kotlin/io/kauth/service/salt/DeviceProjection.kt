package io.kauth.service.salt

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.appStackSqlProjector
import io.kauth.service.organism.OrganismApi
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.upsert
import java.util.*


object DeviceProjection {

    object DeviceTable: Table("devices") {
        val id = text("id").uniqueIndex()
        val organismId = text("organism_id")
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

    @Serializable
    data class Projection(
        val id: String,
        val organismId: String,
        val organismName: String? = null,
        val seriesNumber: String,
        val ports: List<String>,
        val status: String?,
        val createdBy: String,
        val createdAt: Instant,
        val commandTopic: String?,
        val statusTopic: String?,
        val stateTopic: String?
    )

    val ResultRow.toDeviceProjection get() =
        Projection(
            this[DeviceTable.id],
            this[DeviceTable.organismId],
            this[DeviceTable.organismName],
            this[DeviceTable.seriesNumber],
            this[DeviceTable.ports],
            this[DeviceTable.status],
            this[DeviceTable.createdBy],
            this[DeviceTable.createdAt],
            this[DeviceTable.command_topic],
            this[DeviceTable.status_topic],
            this[DeviceTable.state_topic],
        )

    val sqlEventHandler = appStackSqlProjector<Device.Event>(
        streamName = "\$ce-device",
        consumerGroup = "device-sql-projection",
        tables = listOf(DeviceTable)
    ) { event ->
        AppStack.Do {
            val entity = UUID.fromString(event.retrieveId("device"))
            val state = !DeviceApi.Query.readState(entity) ?: return@Do
            val organism = !OrganismApi.Query.readState(state.organismId)
            !appStackDbQuery {
                DeviceTable.upsert() {
                    it[id] = entity.toString()
                    it[organismId] = state.organismId.toString()
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