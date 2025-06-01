package io.kauth.service.accessrequest

import io.kauth.client.eventStore.EventStoreClientPersistenceSubs
import io.kauth.client.eventStore.model.retrieveId
import io.kauth.client.eventStore.subscribeToAllStream
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.getService
import io.kauth.service.consumerGroupName
import io.kauth.service.notification.Notification
import io.kauth.service.notification.NotificationApi
import io.kauth.service.occasion.Occasion
import io.kauth.service.occasion.OccasionService
import io.kauth.util.Async
import io.kauth.util.not
import java.util.*

object AccessRequestEventHandler {


    val accessRequestEventHandler = AppStack.Do {
        val client = !getService<EventStoreClientPersistenceSubs>()
        !client.subscribeToAllStream<AccessRequest.Event, AccessRequestService>(
            AccessRequestService,
            consumerGroupName(AccessRequestService, AccessRequestService)
        ) { event, accessRequestId ->
            Async {
                if (event.value is AccessRequest.Event.RequestConfirmed) {
                    val accessRequest = !AccessRequestApi.Query.readState(accessRequestId) ?: return@Async
                    !NotificationApi.Command.sendNotification(
                        id = event.id,
                        channel = Notification.Channel.email,
                        recipient =  accessRequest.userId,
                        content = "Your access request for occasion ${accessRequest.occasionId} has been confirmed.",
                        sender = "Occasion Service",
                        resource = event.streamName
                    )
                }

            }
        }
    }

    val occasionEventHandler = AppStack.Do {
        val client = !getService<EventStoreClientPersistenceSubs>()
        !client.subscribeToAllStream<Occasion.Event, OccasionService>(
            OccasionService,
            consumerGroupName(AccessRequestService, OccasionService)
        ) { event, occasionId ->
            Async {
                if (event.value is Occasion.Event.PlaceReserved) {
                    val requestId = event.value.resource.retrieveId(AccessRequestService.name)
                    !AccessRequestApi.Command.acceptResult(
                        id = UUID.fromString( requestId),
                        accepted = true,
                        reason = "Accepted",
                    )
                }
                if (event.value is Occasion.Event.PlaceConfirmed) {
                    val requestId = event.value.resource.retrieveId(AccessRequestService.name)
                    !AccessRequestApi.Command.confirmationResult(
                        id = UUID.fromString( requestId),
                        confirmed = true,
                        reason = "Accepted",
                    )
                }

                if (event.value is Occasion.Error.CategoryFull) {
                    val requestId = event.value.resource.retrieveId(AccessRequestService.name)
                    !AccessRequestApi.Command.acceptResult(
                        id = UUID.fromString( requestId),
                        accepted = false,
                        reason = "Category is full, cannot reserve more places.",
                    )
                }

            }
        }
    }

    val start = AppStack.Do {
        !occasionEventHandler
        !accessRequestEventHandler
    }

}
