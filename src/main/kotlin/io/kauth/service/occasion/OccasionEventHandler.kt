package io.kauth.service.occasion

import io.kauth.client.eventStore.EventStoreClientPersistenceSubs
import io.kauth.client.eventStore.subscribeToAllStream
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.getService
import io.kauth.service.accessrequest.AccessRequest
import io.kauth.service.accessrequest.AccessRequestApi
import io.kauth.service.accessrequest.AccessRequestService
import io.kauth.service.consumerGroupName
import io.kauth.util.Async
import io.kauth.util.not

object OccasionEventHandler {

    val accessRequestHandler = AppStack.Do {
        val client = !getService<EventStoreClientPersistenceSubs>()
        !client.subscribeToAllStream<AccessRequest.Event, AccessRequestService>(
            AccessRequestService,
            consumerGroupName(OccasionService, AccessRequestService)
        ) { event, requestId  ->
            Async {
                val request = !AccessRequestApi.Query.readState(requestId) ?: error("Request not found")
                if (event.value is AccessRequest.Event.RequestPendingAccept) {
                    try {
                        !OccasionApi.Command.reservePlace(
                            id = request.occasionId,
                            categoryName = request.categoryName,
                            resource = event.streamName,
                            places = request.places
                        )
                    } catch (error: Throwable) {
                        !AccessRequestApi.Command.acceptResult(
                            requestId,
                            false,
                            error.message ?: "Unknown error",
                        )
                    }
                }
                if (event.value is AccessRequest.Event.RequestPendingConfirmation) {
                    !OccasionApi.Command.confirmPlace(
                        request.occasionId,
                        event.streamName,
                    )
                }
            }
        }
    }

    val start = AppStack.Do {
        !accessRequestHandler
    }

}