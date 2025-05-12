package io.kauth.service.occasion

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.appStackSqlProjector
import io.kauth.service.fanpage.FanPageApi
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
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
        val description = text("description")
        val date = date("date").nullable()
        val categories = json<List<Occasion.Category>>("categories", Json)
        val owners = json<List<String>>("owners", Json).nullable()
        val createdAt = timestamp("created_at")
        val name = text("name").nullable()
        val disabled = bool("disabled").default(false)
        val fanPageId = text("fan_page_id").nullable()
        val uniqueDateTime = datetime("unique_date_time").nullable()
        val startDateTime = datetime("start_date_time").nullable()
        val endDateTime = datetime("end_date_time").nullable()
        val weekdays = json<List<DayOfWeek>>("weekdays", Json).nullable()
        val totalCapacity = integer("total_capacity").nullable()
    }

    @Serializable
    data class OccasionProjection(
        val id: String,
        val description: String,
        val date: LocalDate?,
        val categories: List<Occasion.Category>,
        val owners: List<String>? = null,
        val createdAt: Instant,
        val name: String? = null,
        val fanPageId: String? = null,
        val disabled: Boolean = false,
        val uniqueDateTime: LocalDateTime? = null,
        val startDateTime: LocalDateTime? = null,
        val endDateTime: LocalDateTime? = null,
        val weekdays: List<DayOfWeek>? = null,
        val totalCapacity: Int? = null,
    )

    val ResultRow.toOccasionProjection get() = OccasionProjection(
        id = this[OccasionTable.id],
        description = this[OccasionTable.description],
        date = this[OccasionTable.date],
        categories = this[OccasionTable.categories],
        owners = this[OccasionTable.owners],
        createdAt = this[OccasionTable.createdAt],
        name = this[OccasionTable.name],
        fanPageId = this[OccasionTable.fanPageId],
        disabled = this[OccasionTable.disabled],
        uniqueDateTime = this[OccasionTable.uniqueDateTime],
        startDateTime = this[OccasionTable.startDateTime],
        endDateTime = this[OccasionTable.endDateTime],
        weekdays = this[OccasionTable.weekdays],
        totalCapacity = this[OccasionTable.totalCapacity]
    )

    val sqlEventHandler = appStackSqlProjector<Occasion.Event>(
        streamName = "\$ce-occasion",
        consumerGroup = "occasion-sql-projection",
        tables = listOf(OccasionTable)
    ) { event ->
        AppStack.Do {
            val occasionId = UUID.fromString(event.retrieveId("occasion"))
            val state = !OccasionApi.Query.readState(occasionId) ?: return@Do
            !appStackDbQuery {
                OccasionTable.upsert { it ->
                    it[id] = occasionId.toString()
                    it[description] = state.description
                    it[date] = state.date
                    it[categories] = state.categories.map { cat -> Occasion.Category(cat.name, cat.capacity) }
                    it[createdAt] = state.createdAt
                    it[name] = state.name
                    it[disabled] = state.disabled
                    it[fanPageId] = state.fanPageId?.toString()
                    it[owners] = state.owners ?: emptyList()
                    it[uniqueDateTime] = state.occasionType?.let { type ->
                        if (type is Occasion.OccasionType.UniqueDate) {
                            type.date
                        } else {
                            null
                        }
                    }
                    it[startDateTime] = state.occasionType?.let { type ->
                        if (type is Occasion.OccasionType.RecurringEvent) {
                            type.startDateTime
                        } else {
                            null
                        }
                    }
                    it[endDateTime] = state.occasionType?.let { type ->
                        if (type is Occasion.OccasionType.RecurringEvent) {
                            type.endDateTime
                        } else {
                            null
                        }
                    }
                    it[weekdays] = state.occasionType?.let { type ->
                        if (type is Occasion.OccasionType.RecurringEvent) {
                            type.weekdays
                        } else {
                            null
                        }
                    }
                    it[totalCapacity] = state.totalCapacity
                }
            }
        }
    }

}
