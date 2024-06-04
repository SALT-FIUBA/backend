package io.kauth.service.salt

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.appStackSqlProjector
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.upsert
import java.util.*


object DeviceProjection {

    object DeviceTable: Table("devices") {
        val id = text("id").uniqueIndex()
        val organismId = text("organism_id")
        val seriesNumber = text("series_number")
        val ports = array<String>("ports")
        val status = text("status").nullable()
        val createdBy = text("created_by")
        val createdAt = timestamp("created_at")
    }

    val sqlEventHandler = appStackSqlProjector<Device.Event>(
        streamName = "\$ce-device",
        consumerGroup = "device-sql-projection",
        tables = listOf(DeviceTable)
    ) { event ->
        AppStack.Do {
            val entity = UUID.fromString(event.retrieveId("device"))
            val state = !DeviceApi.readState(entity) ?: return@Do
            !appStackDbQuery {
                DeviceTable.upsert() {
                    it[id] = entity.toString()
                    it[organismId] = state.organismId.toString()
                    it[seriesNumber] = state.seriesNumber
                    it[ports] = state.ports
                    it[status] = state.status
                    it[createdBy] = state.createdBy
                    it[createdAt] = state.createdAt
                }
            }
        }
    }

}