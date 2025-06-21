package io.kauth.service.occasion

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.appStackSqlProjector
import io.kauth.service.fanpage.FanPageApi
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.json
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.upsert
import java.util.*

object OccasionProjection {

    object OccasionTable : Table("occasions") {
        val id = text("id").uniqueIndex()
        val resource = text("resource")
        val description = text("description")
        val categories = json<List<Occasion.CategoryState>>("categories", Json)
        val createdAt = timestamp("created_at")
        val name = text("name")
        val disabled = bool("disabled")
        val fanPageId = text("fan_page_id")
        val startDateTime = timestamp("start_date_time")
        val endDateTime = timestamp("end_date_time")
        val status  = text("status")
    }

    @Serializable
    data class OccasionProjection(
        val id: String,
        val description: String,
        val categories: List<Occasion.CategoryState>,
        val createdAt: Instant,
        val name: String,
        val fanPageId: String,
        val disabled: Boolean,
        val status: String,
        val startDateTime: Instant,
        val endDateTime: Instant,
        val resource: String,
    )

    val ResultRow.toOccasionProjection get() = OccasionProjection(
        id = this[OccasionTable.id],
        description = this[OccasionTable.description],
        categories = this[OccasionTable.categories],
        createdAt = this[OccasionTable.createdAt],
        name = this[OccasionTable.name],
        fanPageId = this[OccasionTable.fanPageId],
        disabled = this[OccasionTable.disabled],
        startDateTime = this[OccasionTable.startDateTime],
        endDateTime = this[OccasionTable.endDateTime],
        resource = this[OccasionTable.resource],
        status = this[OccasionTable.status]
    )

    val sqlEventHandler = appStackSqlProjector<Occasion.Event>(
        streamName = "\$ce-${OccasionService.name}",
        consumerGroup = "${OccasionService.name}-sql-projection",
        tables = listOf(OccasionTable)
    ) { event ->
        AppStack.Do {
            val occasionId = UUID.fromString(event.retrieveId(OccasionService.name))
            val state = !OccasionApi.Query.readState(occasionId) ?: return@Do
            !appStackDbQuery {
                OccasionTable.upsert { it ->
                    it[id] = occasionId.toString()
                    it[description] = state.description
                    it[categories] = state.categories
                    it[createdAt] = state.createdAt
                    it[name] = state.name
                    it[disabled] = state.disabled
                    it[fanPageId] = state.fanPageId.toString()
                    it[startDateTime] = state.startDateTime
                    it[endDateTime] = state.endDateTime
                    it[status] = state.status.name
                    it[resource] = state.resource
                }
            }
        }
    }

}
