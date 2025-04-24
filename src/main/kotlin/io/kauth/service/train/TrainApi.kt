package io.kauth.service.train

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.exception.ApiException
import io.kauth.exception.allowIf
import io.kauth.exception.not
import io.kauth.monad.apicall.ApiCall
import io.kauth.monad.apicall.apiCallGetService
import io.kauth.monad.apicall.apiCallLog
import io.kauth.monad.apicall.toApiCall
import io.kauth.monad.stack.*
import io.kauth.service.organism.TrainService
import io.kauth.service.reservation.ReservationApi
import io.kauth.service.train.TrainProjection.toTrainProjection
import io.kauth.util.not
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.selectAll
import java.util.*

object TrainApi {

    object Command {

        fun create(
            name: String,
            description: String,
            seriesNumber: String,
            organism: UUID
        ) = ApiCall.Do {

            val log = !apiCallLog
            val jwt = jwt ?: !ApiException("UnAuth")

            if (name.isBlank()) {
                !ApiException("Invalid name")
            }

            /*
            !allowIf("admin" in jwt.payload.roles) {
                "Not authorized"
            }
             */

            val service = !apiCallGetService<TrainService.Interface>()

            val id = !ReservationApi.takeIfNotTaken("train-${seriesNumber}") { UUID.randomUUID().toString() }.toApiCall()

            log.info("Create train $seriesNumber")

            !service.command
                .handle(UUID.fromString(id))
                .throwOnFailureHandler(
                    Train.Command.CreateTrain(
                        seriesNumber = seriesNumber,
                        organism = organism,
                        name = name,
                        description = description,
                        createdBy = jwt.payload.id,
                        createdAt = Clock.System.now()
                    ),
                ).toApiCall()

            id

        }

    }

    object Query {

        fun readState(id: UUID) = AppStack.Do {
            val service = !getService<TrainService.Interface>()
            !service.query.readState(id)
        }

        fun train(id: UUID) = AppStack.Do {
            !appStackDbQuery {
                TrainProjection.TrainTable.selectAll()
                    .where { TrainProjection.TrainTable.id eq id.toString() }
                    .singleOrNull()
                    ?.toTrainProjection
            }
        }

        fun trainList(
            organismId: String? = null
        ) = AppStack.Do {
            !appStackDbQuery {
                TrainProjection.TrainTable.selectAll()
                    .where {
                        organismId?.let { TrainProjection.TrainTable.organismId.eq(it) } ?: Op.TRUE
                    }
                    .map { it.toTrainProjection }
            }
        }

    }

}

