package io.kauth.service.train

import io.kauth.monad.stack.*
import io.kauth.service.auth.AuthApi
import io.kauth.service.salt.DeviceProjection
import io.kauth.service.train.Train
import io.kauth.service.train.TrainApi
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.upsert
import java.util.*


object TrainProjection {

    object TrainTable: Table("trains") {
        val id = text("id").uniqueIndex()
        val seriesNumber = text("series_number")
        val name = text("name")
        val description = text("description")
        val createdBy = text("created_by").nullable()
        val createdAt = timestamp("created_at")
        val organismId = text("organism_id")
        val deleted = bool("deleted").default(false)
    }

    @Serializable
    data class TrainProjection(
        val id: String,
        val name: String,
        val description: String,
        val seriesNumber: String,
        val organismId: String,
        val createdBy: String?,
        val createdAt: Instant,
        val deleted: Boolean = false
    )

    val ResultRow.toTrainProjection get() =
        TrainProjection(
            this[TrainTable.id],
            this[TrainTable.name],
            this[TrainTable.description],
            this[TrainTable.seriesNumber],
            this[TrainTable.organismId],
            this[TrainTable.createdBy],
            this[TrainTable.createdAt],
            this[TrainTable.deleted]
        )

    val sqlEventHandler = appStackSqlProjectorNeon<Train.Event>(
        streamName = "\$ce-train",
        consumerGroup = "train-sql-projection",
        tables = listOf(TrainTable)
    ) { event ->
        AppStack.Do {
            val entity = UUID.fromString(event.retrieveId("train"))
            val state = !TrainApi.Query.readState(entity) ?: return@Do
            !appStackDbQueryNeon {
                TrainTable.upsert() {
                    it[id] = entity.toString()
                    it[seriesNumber] = state.seriesNumber
                    it[name] = state.name
                    it[description] = state.description
                    it[createdBy] = state.createdBy
                    it[createdAt] = state.createdAt
                    it[organismId] = state.organism.toString()
                    it[deleted] = state.deleted
                }
            }
        }
    }

}