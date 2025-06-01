package io.kauth.service.occasion

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.exception.ApiException
import io.kauth.exception.allowIf
import io.kauth.exception.not
import io.kauth.monad.apicall.ApiCall
import io.kauth.monad.apicall.apiCallGetService
import io.kauth.monad.apicall.apiCallLog
import io.kauth.monad.apicall.toApiCall
import io.kauth.monad.stack.*
import io.kauth.service.accessrequest.AccessRequestApi
import io.kauth.service.accessrequest.AccessRequestService
import io.kauth.service.fanpage.FanPageApi
import io.kauth.service.occasion.OccasionProjection.toOccasionProjection
import io.kauth.service.ping.Api
import io.kauth.util.not
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

object OccasionApi {

    object Command {

        fun confirmPlace(
            id: UUID,
            resource: String,
        ) = AppStack.Do {
            val occasionService = !getService<OccasionService.Interface>()
            !occasionService.command
                .handle(id)
                .throwOnFailureHandler(
                    Occasion.Command.ConfirmPlace(
                        confirmedAt = Clock.System.now(),
                        resource = resource,
                    )
                )
            id
        }

        fun reservePlace(
            id: UUID,
            resource: String,
            places: Int,
            categoryName: String
        ) = AppStack.Do {
            val occasionService = !getService<OccasionService.Interface>()
            !occasionService.command
                .handle(id)
                .throwOnFailureHandler(
                    Occasion.Command.ReservePlace(
                        categoryName = categoryName,
                        takenAt = Clock.System.now(),
                        places = places,
                        resource = resource,
                    )
                )
            id
        }

        fun visibility(
            id: UUID,
            disabled: Boolean
        ) = ApiCall.Do {
            val jwt = jwt ?: !ApiException("UnAuth")
            val service = !apiCallGetService<OccasionService.Interface>()
            val occasion = !Query.readState(id).toApiCall() ?: !ApiException("Occasion not found")
            val fanId = occasion.fanPageId
            val fanPageData = !FanPageApi.Query.readState(fanId).toApiCall() ?: !ApiException("FanPage not found")
            !allowIf(jwt.payload.id in (fanPageData.admins + fanPageData.createdBy)) {
                "Not authorized"
            }
            !service.command
                .handle(id)
                .throwOnFailureHandler(
                    Occasion.Command.Visibility(
                        disabled = disabled
                    )
                ).toApiCall()
            id.toString()
        }

        fun create(
            resource: String,
            fanPageId: UUID,
            categories: List<Occasion.Category>,
            description: String,
            name: String,
            startDateTime: Instant,
            endDateTime: Instant,
            location: String?
        ) = ApiCall.Do {

            val log = !apiCallLog
            val jwt = jwt ?: !ApiException("UnAuth")

            val fanPageData = !FanPageApi.Query.readState(fanPageId).toApiCall() ?: !ApiException("FanPage not found")

            !allowIf(jwt.payload.id in (fanPageData.admins + fanPageData.createdBy)) {
                "Not authorized"
            }

            if (description.isBlank()) {
                !ApiException("Description cannot be empty")
            }

            val service = !apiCallGetService<OccasionService.Interface>()

            val id = UUID.randomUUID()

            log.info("Create occasion $id")

            !service.command
                .handle(id)
                .throwOnFailureHandler(
                    Occasion.Command.CreateOccasion(
                        resource = resource,
                        fanPageId = fanPageId,
                        categories = categories,
                        description = description,
                        createdAt = Clock.System.now(),
                        name = name,
                        createdBy = jwt.payload.id,
                        startDateTime = startDateTime,
                        endDateTime = endDateTime,
                        location = location,
                    )
                ).toApiCall()

            id.toString()
        }

        fun cancel(id: UUID) =ApiCall.Do {
            val occasionService = !apiCallGetService<OccasionService.Interface>()
            !occasionService.command
                .handle(id)
                .throwOnFailureHandler(
                    Occasion.Command.Cancel(
                        cancelledAt = Clock.System.now()
                    )
                )
                .toApiCall()
            id
        }

        fun cancelPlace(
            id: UUID,
            categoryName: String,
            resource: String
        ) = AppStack.Do {
            val occasionService = !getService<OccasionService.Interface>()
            !occasionService.command
                .handle(id)
                .throwOnFailureHandler(
                    Occasion.Command.CancelPlace(
                        categoryName = categoryName,
                        resource = resource,
                        cancelledAt = Clock.System.now()
                    )
                )
            id
        }
    }

    object Query {

        fun readState(id: UUID) = AppStack.Do {
            val service = !getService<OccasionService.Interface>()
            !service.query.readState(id)
        }

        fun get(id: UUID) = AppStack.Do {
            !appStackDbQuery {
                OccasionProjection.OccasionTable.selectAll()
                    .where {
                        (OccasionProjection.OccasionTable.id eq id.toString())
                    }
                    .firstOrNull()
                    ?.toOccasionProjection
            }
        }

        fun list(
            fanPageId: UUID? = null
        ) = AppStack.Do {
            !appStackDbQuery {
                OccasionProjection.OccasionTable.selectAll()
                    .where {
                        (OccasionProjection.OccasionTable.disabled eq false) and
                                (fanPageId?.let { OccasionProjection.OccasionTable.fanPageId eq it.toString() }
                                    ?: Op.TRUE)
                    }
                    .orderBy(OccasionProjection.OccasionTable.createdAt to SortOrder.DESC)
                    .map { it.toOccasionProjection }
            }
        }

        fun userOccasions() = ApiCall.Do {
            val jwt = jwt ?: !ApiException("UnAuth")
            val userId = jwt.payload.id
            // 1. Occasions where user is admin/creator of the fanpage
            val myFanPages = !FanPageApi.Query.listByAdminOrCreator(userId)
            val myFanPageIds = myFanPages.map { it.id }
            val adminOccasions = !appStackDbQuery {
                OccasionProjection.OccasionTable.selectAll()
                    .where { (OccasionProjection.OccasionTable.fanPageId inList myFanPageIds.map { it.toString() }) and (OccasionProjection.OccasionTable.disabled eq false) }
                    .map { it.toOccasionProjection }
            }.toApiCall()
            // 2. Occasions where user has a reservation (via AccessRequestApi)
            val myRequests = !AccessRequestApi.Query.listReservedOccasionIds(jwt.payload.email)
            val reservedOccasionIds = myRequests.map { it.occasionId }
            val reservedOccasions = if (reservedOccasionIds.isNotEmpty()) {
                !appStackDbQuery {
                    OccasionProjection.OccasionTable.selectAll()
                        .where { (OccasionProjection.OccasionTable.id inList reservedOccasionIds.map { it.toString() }) and (OccasionProjection.OccasionTable.disabled eq false) }
                        .map { it.toOccasionProjection }
                }.toApiCall()
            } else emptyList()
            // Merge and deduplicate
            (adminOccasions + reservedOccasions).distinctBy { it.id }
        }
    }
}
