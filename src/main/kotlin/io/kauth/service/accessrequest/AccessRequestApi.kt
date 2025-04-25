package io.kauth.service.accessrequest

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.exception.ApiException
import io.kauth.exception.allowIf
import io.kauth.exception.not
import io.kauth.monad.apicall.*
import io.kauth.monad.stack.*
import io.kauth.service.accessrequest.AccessRequestProjection.toProjection
import io.kauth.service.occasion.OccasionRoles
import io.kauth.service.occasion.OccasionService
import io.kauth.service.reservation.ReservationApi
import io.kauth.util.not
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.Op
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
        ) = ApiCall.Do {
            val log = !apiCallLog
            val jwt = jwt ?: !ApiException("UnAuth")

            val email = userId ?: jwt.payload.email

            val occasionService = !apiCallGetService<OccasionService.Interface>()
            val service = !apiCallGetService<AccessRequestService.Interface>()

            val occasion = !occasionService.query.readState(occasionId) ?: !ApiException("Occasion not found")

            if (occasion.categories.none { it.name == categoryName }) {
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
                    )
                ).toApiCall()

            id.toString()
        }

        fun accept(
            id: UUID
        ) = ApiCall.Do {
            val jwt = jwt ?: !ApiException("UnAuth")
            !allowIf("role:write:access-request" in jwt.payload.roles) {
                "Not authorized"
            }
            val service = !apiCallGetService<AccessRequestService.Interface>()
            !service.command
                .handle(id)
                .throwOnFailureHandler(
                    AccessRequest.Command.AcceptRequest(Clock.System.now(), jwt.payload.id)
                ).toApiCall()
            id
        }

        fun confirm(
            id: UUID
        ) = ApiCall.Do {
            val jwt = jwt ?: !ApiException("UnAuth")
            !allowIf("role:write:access-request" in jwt.payload.roles) {
                "Not authorized"
            }
            val service = !apiCallGetService<AccessRequestService.Interface>()
            !service.command
                .handle(id)
                .throwOnFailureHandler(
                    AccessRequest.Command.ConfirmRequest(Clock.System.now(),jwt.payload.id)
                ).toApiCall()

            "Confirmed"
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

            val writeRole = session.payload.roles.contains(OccasionRoles.WRITE_ALL)

            !apiCallStackDbQuery {
                AccessRequestProjection.AccessRequestTable.selectAll()
                    .where {
                        (if (writeRole) Op.TRUE else AccessRequestProjection.AccessRequestTable.userId eq session.payload.email).and(
                            occasionId?.let { AccessRequestProjection.AccessRequestTable.occasionId.eq(it.toString()) }
                                ?: Op.TRUE
                        )
                    }
                    .map { it.toProjection }
            }

        }
    }
}
