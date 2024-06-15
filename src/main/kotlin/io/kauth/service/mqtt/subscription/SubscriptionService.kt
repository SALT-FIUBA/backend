package io.kauth.service.mqtt.subscription

import io.kauth.abstractions.command.CommandHandler
import io.kauth.abstractions.result.Output
import io.kauth.client.eventStore.EventStoreClient
import io.kauth.client.eventStore.commandHandler
import io.kauth.client.eventStore.computeStateResult
import io.kauth.client.eventStore.stream
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.getService
import io.kauth.monad.stack.registerService
import io.kauth.service.AppService
import io.kauth.util.Async

object SubscriptionService : AppService {

    //LIST OF TOPICS
    val STREAM_NAME = "subscription"
    val STREAM_PREFIX = "$STREAM_NAME-"
    val SNAPSHOT_STREAM_PREFIX = "subscription_snapshot-"
    val streamName get() = STREAM_PREFIX + "UNIT"
    val snapshotName get() = SNAPSHOT_STREAM_PREFIX + "UNIT"

    //INDIVIDUAL TOPIC
    val TOPIC_STREAM_NAME = "subscription_topic"
    val TOPIC_STREAM_PREFIX = "$TOPIC_STREAM_NAME-"
    val String.topicStreamName get() = TOPIC_STREAM_PREFIX + this

    data class Command(
        val handle: () -> CommandHandler<Subscription.Command, Output>,
        val handleTopic: (topic: String) -> CommandHandler<SubscriptionTopic.Command, Output>
    )

    data class Query(
        val readState: () -> Async<Subscription.State?>,
        val readStateTopic: (topic: String) -> Async<SubscriptionTopic.State?>
    )

    data class Interface(
        val command: Command,
        val query: Query,
    )

    override val start =
        AppStack.Do {

            val client = !getService<EventStoreClient>()

            val commands = Command(
                handle = {
                    stream<Subscription.Event, Subscription.State>(client, streamName, snapshotName)
                        .commandHandler(Subscription.stateMachine, Subscription.eventStateMachine)
                },
                handleTopic = { id ->
                    stream<SubscriptionTopic.Event, SubscriptionTopic.State>(client, id.topicStreamName)
                        .commandHandler(SubscriptionTopic.stateMachine, SubscriptionTopic.eventStateMachine)
                }
            )

            val query = Query(
                readState = {
                    stream<Subscription.Event, Subscription.State>(client, streamName, snapshotName)
                        .computeStateResult(Subscription.eventStateMachine)
                },
                readStateTopic = { id ->
                    stream<SubscriptionTopic.Event, SubscriptionTopic.State>(client, id.topicStreamName)
                        .computeStateResult(SubscriptionTopic.eventStateMachine)
                }
            )

            !registerService(
                Interface(
                    query = query,
                    command = commands,
                )
            )

            !SubscriptionEventHandler.subscriptionHandler

            !SubscriptionEventHandler.topicListSubscription

            !SubscriptionProjection.sqlEventHandler

            !SubscriptionApiRest.api

        }

}
