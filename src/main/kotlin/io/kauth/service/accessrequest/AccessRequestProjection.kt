package io.kauth.service.accessrequest

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.appStackSqlProjector
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.upsert
import java.util.*

object AccessRequestProjection {

    object AccessRequestTable : Table("access_requests") {
        val id = text("id").uniqueIndex()
        val occasionId = text("occasion_id")
        val userId = text("user_id")
        val status = text("status")
        val categoryName = text("category_name").nullable()
        val createdAt = timestamp("created_at")
        val updatedAt = timestamp("updated_at").nullable()
        val updatedBy = text("updated_by").nullable()
        val description = text("description").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    @Serializable
    data class Projection(
        val id: String,
        val occasionId: String,
        val userId: String,
        val status: String,
        val createdAt: Instant,
        val updatedAt: Instant?,
        val updatedBy: String?,
        val categoryName: String?,
        val description: String?,
    )

    val ResultRow.toProjection get() = Projection(
        id = this[AccessRequestTable.id],
        occasionId = this[AccessRequestTable.occasionId],
        userId = this[AccessRequestTable.userId],
        status = this[AccessRequestTable.status],
        createdAt = this[AccessRequestTable.createdAt],
        updatedAt = this[AccessRequestTable.updatedAt],
        updatedBy = this[AccessRequestTable.updatedBy],
        categoryName = this[AccessRequestTable.categoryName],
        description = this[AccessRequestTable.description],
    )

    val sqlEventHandler = appStackSqlProjector<AccessRequest.Event>(
        streamName = "\$ce-access_request",
        consumerGroup = "access_request-sql-projection",
        tables = listOf(AccessRequestTable)
    ) { event ->
        AppStack.Do {
            val id = UUID.fromString(event.retrieveId("access_request"))
            val state = !AccessRequestApi.Query.readState(id) ?: return@Do

            !appStackDbQuery {
                AccessRequestTable.upsert() {
                    it[AccessRequestTable.id] = id.toString()
                    it[userId] = state.userId
                    it[occasionId] = state.occasionId.toString()
                    it[categoryName] = state.categoryName
                    it[status] = when (state.status) {
                        is AccessRequest.Status.Accepted -> "accepted"
                        is AccessRequest.Status.Confirmed -> "confirmed"
                        AccessRequest.Status.Pending -> "pending"
                        is AccessRequest.Status.Rejected -> "rejected"
                    }
                    it[createdAt] = state.createdAt
                    it[updatedAt] = when (val status = state.status) {
                        is AccessRequest.Status.Accepted -> status.acceptedAt
                        is AccessRequest.Status.Confirmed -> status.confirmedAt
                        AccessRequest.Status.Pending -> state.createdAt
                        is AccessRequest.Status.Rejected -> status.rejectedAt
                    }
                    it[updatedBy] = when (val status = state.status) {
                        is AccessRequest.Status.Accepted -> status.acceptedBy
                        is AccessRequest.Status.Confirmed -> status.confirmedBy
                        AccessRequest.Status.Pending -> null
                        is AccessRequest.Status.Rejected -> status.rejectedBy
                    }
                    it[description] = state.description
                }
            }

        }
    }
}
