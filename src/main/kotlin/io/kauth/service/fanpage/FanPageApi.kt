package io.kauth.service.fanpage

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.exception.ApiException
import io.kauth.exception.allowIf
import io.kauth.exception.not
import io.kauth.monad.apicall.*
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.getService
import io.kauth.service.fanpage.FanPageProjection.toFanPageProjection
import io.kauth.util.not
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.json.contains
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

object FanPageApi {

    object Command {

        fun create(
            name: String,
            profilePhoto: String,
            location: String,
            phone: String,
            website: String,
            category: String
        ) = ApiCall.Do {
            val log = !apiCallLog
            val jwt = jwt ?: !ApiException("UnAuth")

            !allowIf( FanPageRoles.CREATE in jwt.payload.roles) {
                "Not authorized"
            }

            val service = !apiCallGetService<FanPageService.Interface>()

            val id = UUID.randomUUID()

            log.info("Create fanpage $id")

            !service.command
                .handle(id)
                .throwOnFailureHandler(
                    FanPage.Command.Create(
                        createdAt = Clock.System.now(),
                        createdBy = jwt.payload.id,
                        name = name,
                        profilePhoto = profilePhoto,
                        location = location,
                        phone = phone,
                        website = website,
                        category = category,
                    )
                ).toApiCall()

            id.toString()
        }

    }


    object Query {

        fun readState(id: UUID) = AppStack.Do {
            val service = !getService<FanPageService.Interface>()
            !service.query.readState(id)
        }

        fun list() = ApiCall.Do {
            !apiCallStackDbQuery{
                val user = jwt?.payload?.id ?: !ApiException("UnAuth")
                FanPageProjection.FanPageTable.selectAll()
                    .where {
                        FanPageProjection.FanPageTable.admins.contains(listOf(user)) or
                                (FanPageProjection.FanPageTable.createdBy eq user)
                    }
                    .orderBy(FanPageProjection.FanPageTable.createdAt to SortOrder.DESC)
                    .map { it.toFanPageProjection }

            }
        }
    }

}