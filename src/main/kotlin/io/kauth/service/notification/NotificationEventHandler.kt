package io.kauth.service.notification

import io.kauth.client.brevo.Brevo
import io.kauth.client.brevo.sendEmail
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.getService
import io.kauth.service.notification.Notification.Event
import io.kauth.service.notification.Notification.Command
import io.kauth.service.notification.NotificationService
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.util.UUID
import io.kauth.client.eventStore.EventStoreClientPersistenceSubs
import io.kauth.client.eventStore.subscribeToAllStream
import io.kauth.service.consumerGroupName
import io.kauth.util.Async
import io.kauth.util.not

object NotificationEventHandler {
    private val log = LoggerFactory.getLogger("NotificationEventHandler")

    val notificationHandler = AppStack.Do {
        val client = !getService<EventStoreClientPersistenceSubs>()
        !client.subscribeToAllStream<Notification.Event, NotificationService>(
            NotificationService,
            consumerGroupName(NotificationService, NotificationService)
        ) { event, notificationId ->
            Async {
                if (event.value is Event.SendNotificationEvent) {
                    val sendEvent = event.value
                    val now = Clock.System.now()
                    try {
                        if (sendEvent.channel == Notification.Channel.email) {
                            val brevoClient = !getService<Brevo.Client>()
                            !brevoClient.sendEmail(
                               to = listOf(Brevo.BrevoUser(sendEvent.recipient, sendEvent.recipient)),
                                 subject = "test",
                                sender = Brevo.BrevoUser(sendEvent.sender, sendEvent.sender),
                                htmlContent = sendEvent.content
                            )
                        } else {
                        }

                        !NotificationApi.Command.sendResult(
                            id = notificationId,
                            sentAt = now,
                            success = true,
                        )

                    } catch (ex: Exception) {
                        !NotificationApi.Command.sendResult(
                            id = notificationId,
                            sentAt = now,
                            success = false,
                            error = ex.message ?: "Unknown error"
                        )
                    }
                }
            }
        }
    }

    val start = AppStack.Do {
        !notificationHandler
    }
}

