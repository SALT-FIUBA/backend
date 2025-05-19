package io.kauth.service.accessrequest

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.exception.ApiException
import io.kauth.exception.allowIf
import io.kauth.exception.not
import io.kauth.monad.apicall.*
import io.kauth.monad.stack.*
import io.kauth.service.accessrequest.AccessRequestProjection.toProjection
import io.kauth.service.fanpage.FanPageApi
import io.kauth.service.fanpage.FanPageProjection
import io.kauth.service.occasion.OccasionApi
import io.kauth.service.occasion.OccasionRoles
import io.kauth.service.occasion.OccasionService
import io.kauth.service.reservation.ReservationApi
import io.kauth.util.not
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

object AccessRequestApi {

    object Command {

        fun create(
            occasionId: UUID,
            userId: String?,
            categoryName: String,
            description: String,
            places: Int
        ) = ApiCall.Do {

            val log = !apiCallLog

            val jwt = jwt ?: !ApiException("UnAuth")

            val email = userId ?: jwt.payload.email

            val occasionService = !apiCallGetService<OccasionService.Interface>()
            val service = !apiCallGetService<AccessRequestService.Interface>()

            val occasion = !occasionService.query.readState(occasionId) ?: !ApiException("Occasion not found")

            if (occasion.categories.isNotEmpty() && occasion.categories.none { it.name == categoryName }) {
                !ApiException("Category not found")
            }

            val id = !ReservationApi.takeIfNotTaken("request-${occasionId}-${email}") { UUID.randomUUID().toString() }.toApiCall()

            log.info("Create access request $id for occasion $occasionId by user $userId")

            !service.command
                .handle(UUID.fromString(id))
                .throwOnFailureHandler(
                    AccessRequest.Command.CreateRequest(
                        occasionId = occasionId,
                        userId = email,
                        createAt = Clock.System.now(),
                        categoryName = categoryName,
                        description = description,
                        places = places
                    )
                ).toApiCall()

            id.toString()
        }

        fun accept(id: UUID) = ApiCall.Do {
            val jwt = jwt ?: !ApiException("UnAuth")

            val reserve = !Query.readState(id).toApiCall() ?: !ApiException("Reserve not found")
            val occasion = !OccasionApi.Query.readState(reserve.occasionId).toApiCall() ?: !ApiException("Occasion not found")
            val fanPage = !FanPageApi.Query.readState(occasion.fanPageId).toApiCall() ?: !ApiException("FanPage not found")

            !allowIf(jwt.payload.id in (fanPage.admins + fanPage.createdBy)) {
                "Not authorized"
            }

            val service = !apiCallGetService<AccessRequestService.Interface>()
            !service.command
                .handle(id)
                .throwOnFailureHandler(
                    AccessRequest.Command.AcceptRequest(
                        Clock.System.now(),
                        jwt.payload.id
                    )
                ).toApiCall()
            id
        }

        fun confirm(
            id: UUID
        ) = ApiCall.Do {
            val jwt = jwt ?: !ApiException("UnAuth")

            val reserve = !Query.readState(id).toApiCall() ?: !ApiException("Reserve not found")
            val occasion = !OccasionApi.Query.readState(reserve.occasionId).toApiCall() ?: !ApiException("Occasion not found")
            val fanPage = !FanPageApi.Query.readState(occasion.fanPageId).toApiCall() ?: !ApiException("FanPage not found")

            !allowIf(jwt.payload.id in (fanPage.admins + fanPage.createdBy)) {
                "Not authorized"
            }

            val service = !apiCallGetService<AccessRequestService.Interface>()

            !service.command
                .handle(id)
                .throwOnFailureHandler(
                    AccessRequest.Command.ConfirmRequest(Clock.System.now(), jwt.payload.id)
                ).toApiCall()

            id.toString()
        }

        fun confirmationResult(
            id: UUID,
            confirmed: Boolean,
            reason: String
        ) = AppStack.Do {
            val service = !getService<AccessRequestService.Interface>()
            !service.command
                .handle(id)
                .throwOnFailureHandler(
                    AccessRequest.Command.ConfirmRequestResult(
                        confirmed = confirmed,
                        reason = reason,
                        at = Clock.System.now(),
                    )
                )
            id.toString()
        }

        fun acceptResult(
            id: UUID,
            accepted: Boolean,
            reason: String
        ) = AppStack.Do {
            val service = !getService<AccessRequestService.Interface>()
            !service.command
                .handle(id)
                .throwOnFailureHandler(
                    AccessRequest.Command.AcceptRequestResult(
                        accepted = accepted,
                        reason = reason,
                        at = Clock.System.now(),
                    )
                )
            id.toString()
        }

    }

    object Query {

        fun readState(id: UUID) = AppStack.Do {
            val service = !getService<AccessRequestService.Interface>()
            !service.query.readState(id)
        }

        fun list(
            occasionId: UUID? = null,
        ) = ApiCall.Do {
            val session = jwt ?: !ApiException("UnAuth")
            !apiCallStackDbQuery {

                var writeAccess = false

                if (occasionId != null) {
                    val occasion = !OccasionApi.Query.get(occasionId).toApiCall()
                        ?: !ApiException("Occasion not found")
                    val fanPage = !FanPageApi.Query.state(UUID.fromString(occasion.fanPageId))
                        ?: !ApiException("FanPage not found")
                    writeAccess = session.payload.id in (fanPage.admins + fanPage.createdBy)
                }

                AccessRequestProjection.AccessRequestTable.selectAll()
                    .where {
                        (if (writeAccess) Op.TRUE else AccessRequestProjection.AccessRequestTable.userId eq session.payload.email).and(
                            occasionId?.let { AccessRequestProjection.AccessRequestTable.occasionId.eq(it.toString()) }
                                ?: Op.TRUE
                        )
                    }
                    .orderBy(
                        AccessRequestProjection.AccessRequestTable.createdAt to SortOrder.DESC
                    )
                    .map { it.toProjection }
            }
        }
    }
}
