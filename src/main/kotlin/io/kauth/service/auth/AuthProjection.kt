package io.kauth.service.auth

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.appStackDbQueryAll
import io.kauth.monad.stack.appStackSqlProjector
import io.kauth.service.organism.OrganismProjection
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.upsert
import java.util.*


object AuthProjection {

    object User: Table("users") {
        val id = text("id").uniqueIndex()
        val firstname = text("firstname")
        val lastname = text("lastname")
        val email = text("email")
        val roles = array<String>("roles")
    }

    @Serializable
    data class UserProjection(
        val id: String,
        val firstname: String,
        val lastname: String,
        val email: String,
        val roles: List<String>
    )

    val ResultRow.toUserProjection get() =
        UserProjection(
            this[User.id],
            this[User.firstname],
            this[User.lastname],
            this[User.email],
            this[User.roles],
        )

    val sqlEventHandler = appStackSqlProjector<Auth.UserEvent>(
        streamName = "\$ce-user",
        consumerGroup = "user-sql-projection",
        tables = listOf(User)
    ) { event ->
        AppStack.Do {

            val userId = UUID.fromString(event.retrieveId("user"))
            val state = !AuthApi.Query.readState(userId)?: return@Do

            !appStackDbQueryAll {
                User.upsert() {
                    it[id] = userId.toString()
                    it[firstname] = state.personalData.firstName
                    it[lastname] = state.personalData.lastName
                    it[email] = state.email
                    it[roles] = state.roles
                }
            }

        }
    }

}