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
import io.kauth.util.not
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

object OccasionApi {

    object Command {

        fun takePlace(
            id: UUID,
            categoryName: String,
            accessRequestId: UUID
        ) = AppStack.Do {
            val occasionService = !getService<OccasionService.Interface>()
            !AccessRequestApi.Query.readState(accessRequestId) ?: !ApiException("Request not found")
            !occasionService.command
                .handle(id)
                .throwOnFailureHandler(
                    Occasion.Command.TakePlace(
                        categoryName = categoryName,
                        takenAt = Clock.System.now(),
                        accessRequestId = accessRequestId.toString(),
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

            if (fanId != null) {
                val fanPageData = !FanPageApi.Query.readState(fanId).toApiCall() ?: !ApiException("FanPage not found")
                !allowIf(jwt.payload.id in (fanPageData.admins + fanPageData.createdBy)) {
                    "Not authorized"
                }
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
            fanPageId: UUID,
            categories: List<Occasion.Category>,
            description: String,
            name: String? = null,
            totalCapacity: Int? = null,
            uniqueDateTime: LocalDateTime? = null,
            startDateTime: LocalDateTime? = null,
            endDateTime: LocalDateTime? = null,
            weekdays : List<DayOfWeek>? = null
        ) = ApiCall.Do {
            val log = !apiCallLog
            val jwt = jwt ?: !ApiException("UnAuth")

            val fanPageData = !FanPageApi.Query.readState(fanPageId).toApiCall() ?: !ApiException("FanPage not found")

            !allowIf(jwt.payload.id in (fanPageData.admins + fanPageData.createdBy)) {
                "Not authorized"
            }

            if (categories.isEmpty()) {
                !ApiException("Categories cannot be empty")
            }

            if (description.isBlank()) {
                !ApiException("Description cannot be empty")
            }

            val service = !apiCallGetService<OccasionService.Interface>()

            val id = UUID.randomUUID()

            log.info("Create occasion $id")

            val occasionType = if (uniqueDateTime != null) {
                Occasion.OccasionType.UniqueDate(uniqueDateTime)
            } else if (startDateTime != null && endDateTime != null && weekdays != null) {
                Occasion.OccasionType.RecurringEvent(startDateTime, endDateTime, weekdays)
            } else {
                null
            }

            !service.command
                .handle(id)
                .throwOnFailureHandler(
                    Occasion.Command.CreateOccasion(
                        fanPageId = fanPageId,
                        categories = categories,
                        description = description,
                        createdAt = Clock.System.now(),
                        name = name,
                        totalCapacity = totalCapacity,
                        occasionType = occasionType
                    )
                ).toApiCall()

            id.toString()
        }
    }

    object Query {

        fun readState(id: UUID) = AppStack.Do {
            val service = !getService<OccasionService.Interface>()
            !service.query.readState(id)
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
    }
}
