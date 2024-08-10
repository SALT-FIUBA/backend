package io.kauth.service.organism

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.appStackSqlProjector
import io.kauth.service.auth.Auth
import io.kauth.service.auth.AuthApi
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.upsert
import java.util.*


object OrganismProjection {

    object OrganismTable: Table("organims") {
        val id = text("id").uniqueIndex()
        val name = text("name")
        val tag = text("tag")
        val description = text("description")
        val createdBy = text("created_by")
        val createdByEmail = text("created_by_email").nullable()
        val createdAt = timestamp("created_at")
    }

    object OrganismUserInfoTable: Table("organism_users") {
        val organismId = text("organism_id")
        val userId = text("user_id")
        val addedBy = text("added_by")
        val addedAt = timestamp("added_at")
        val role = text("role")

        override val primaryKey: PrimaryKey = PrimaryKey(organismId, userId, role)
    }

    @Serializable
    data class OrganismProjection(
        val id: String,
        val name: String,
        val tag: String,
        val description: String,
        val createdBy: String,
        val createdByEmail: String? = null,
        val createdAt: Instant
    )

    @Serializable
    data class OrganismUserInfoProjection(
        val role: Auth.Role,
        val userId: String,
        val organismId: String,
        val addedBy: String,
        val addedAt: Instant
    )

    val ResultRow.toOrganismUserInfoProjection get() =
        OrganismUserInfoProjection(
            Auth.Role.valueOf(this[OrganismUserInfoTable.role]),
            this[OrganismUserInfoTable.userId],
            this[OrganismUserInfoTable.organismId],
            this[OrganismUserInfoTable.addedBy],
            this[OrganismUserInfoTable.addedAt],
        )

    val ResultRow.toOrganismProjection get() =
        OrganismProjection(
            this[OrganismTable.id],
            this[OrganismTable.name],
            this[OrganismTable.tag],
            this[OrganismTable.description],
            this[OrganismTable.createdBy],
            this[OrganismTable.createdByEmail],
            this[OrganismTable.createdAt],
        )

    val sqlEventHandler = appStackSqlProjector<Organism.Event>(
        streamName = "\$ce-organism",
        consumerGroup = "organism-sql-projection",
        tables = listOf(OrganismTable, OrganismUserInfoTable)
    ) { event ->
        AppStack.Do {

            val entity = UUID.fromString(event.retrieveId("organism"))
            val state = !OrganismApi.Query.readState(entity) ?: return@Do
            val user = !AuthApi.Query.readState(UUID.fromString(state.createdBy))

            !appStackDbQuery {

                state.operators.forEach {
                    operator ->
                        OrganismUserInfoTable.upsert() {
                            it[userId] = operator.id.toString()
                            it[addedAt] = operator.addedAt
                            it[addedBy] = operator.addedBy.toString()
                            it[role] = Auth.Role.operators.name
                            it[organismId] = entity.toString()
                        }
                }

                state.supervisors.forEach { supervisor ->
                    OrganismUserInfoTable.upsert() {
                        it[userId] = supervisor.id.toString()
                        it[addedAt] = supervisor.addedAt
                        it[addedBy] = supervisor.addedBy.toString()
                        it[role] = Auth.Role.supervisor.name
                        it[organismId] = entity.toString()
                    }
                }

                OrganismTable.upsert() {
                    it[id] = entity.toString()
                    it[tag] = state.tag
                    it[name] = state.name
                    it[description] = state.description
                    it[createdBy] = state.createdBy
                    it[createdByEmail] = user?.email
                    it[createdAt] = state.createdAt
                }

            }
        }
    }

}