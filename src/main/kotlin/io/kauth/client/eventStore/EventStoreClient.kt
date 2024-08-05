package io.kauth.client.eventStore

import ch.qos.logback.classic.Logger
import com.eventstore.dbclient.*
import io.kauth.client.eventStore.model.*
import io.kauth.client.eventStore.model.ReadResult
import io.kauth.util.Async
import io.kauth.util.IO
import io.kauth.util.not
import io.kauth.util.toAsync
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.datetime.Clock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import java.util.concurrent.ExecutionException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.suspendCoroutine
import kotlin.math.log
import kotlin.reflect.jvm.jvmName

data class EventStoreClient(
    val client: EventStoreDBClient,
    val json: Json
)

data class EventStoreClientPersistenceSubs(
    val client: EventStoreDBPersistentSubscriptionsClient,
    val json: Json
)

fun eventStoreClientNew(
    conectionString: String,
    json: Json
) = IO {
    val config = EventStoreDBConnectionString.parseOrThrow(conectionString)
    EventStoreClient(EventStoreDBClient.create(config), json)
}

fun eventStoreClientPersistenceSubsNew(
    conectionString: String,
    json: Json
) = IO {
    val config = EventStoreDBConnectionString.parseOrThrow(conectionString)
    EventStoreClientPersistenceSubs(EventStoreDBPersistentSubscriptionsClient.create(config), json)
}

inline fun <reified T> EventStoreClient.readFromStream(
    streamName: String,
    forward: Boolean = true,
    revision: Long? = 0,
    limit: Long? = null,
) = Async<ReadResult<T>?> {

    val options = ReadStreamOptions
        .get()
        .let { if(forward) it.forwards() else it.backwards() }
        .let { if(limit != null) it.maxCount(limit) else it }
        .let {
            when {
                revision?.toInt() == 0 -> it.fromStart()
                revision != null -> it.fromRevision(revision)
                else -> it.fromEnd()
            }
        }

    val response = try {
        !client.readStream(streamName, options).toAsync()
    } catch (e: StreamNotFoundException) {
        return@Async null
    }

    ReadResult(
        events = response.events.map {
            Event(
                id = it.event.eventId,
                value = json.decodeFromString(it.event.eventData.decodeToString()),
                metadata = json.decodeFromString<EventMetadata>(it.event.userMetadata.decodeToString()),
                revision = it.event.revision,
                streamName = it.event.streamId
            )
        },
        lastStreamPosition = response.lastStreamPosition,
        firstStreamPosition = response.firstStreamPosition
    )

}

inline fun <reified  T> EventStoreClient.encodeToBytes(value: T): ByteArray =
    json.encodeToString(value).toByteArray(Charsets.UTF_8)

inline fun <reified T> EventStoreClient.appendToStream(
    streamName: String,
    data: T,
    revision: StreamRevision = StreamRevision.AnyRevision
) = Async {
    !appendToStream(
        streamName,
        listOf(
            Event(
                id = UUID.randomUUID(),
                value = data,
                metadata = EventMetadata(Clock.System.now()),
                revision = revision.toRevision.toRawLong(),
                streamName = streamName
            )
        ),
        revision
    )
}


inline fun <reified T> EventStoreClient.appendToStream(
    streamName: String,
    data: Event<T>,
    revision: StreamRevision = StreamRevision.AnyRevision
) = appendToStream(streamName, listOf(data), revision)

@JvmName("appendListToStream")
inline fun <reified T> EventStoreClient.appendToStream(
    streamName: String,
    dataList: List<T>,
    revision: StreamRevision = StreamRevision.AnyRevision
): Async<WriteResult>  = Async {
    !appendToStream(
        streamName,
        dataList.map { Event(
            id = UUID.randomUUID(),
            value = it,
            metadata = EventMetadata(Clock.System.now()),
            streamName = streamName,
            revision = revision.toRevision.toRawLong()
        ) },
        revision
    )
}

//TODO: Esto no mantiene el orden por lo que entiendo, hay que usar el otro type de subscription
inline fun <reified T> EventStoreClientPersistenceSubs.subscribeToStream(
    stream: String,
    consumerGroup: String,
    crossinline onEvent: (Event<T>) -> Async<Unit>
) = Async {

    runCatching {
        client.createToStream(
            stream,
            consumerGroup,
            CreatePersistentSubscriptionToStreamOptions
                .get()
                .resolveLinkTos()
                .fromStart()
                .namedConsumerStrategy(NamedConsumerStrategy.PINNED)
        ).await()
    }

    //TODO parametrize buffer size
    !client.subscribeToStream(
        stream,
        consumerGroup,
        SubscribePersistentSubscriptionOptions.get().bufferSize(1000),
        object : PersistentSubscriptionListener() {
            override fun onEvent(subscription: PersistentSubscription?, retryCount: Int, event: ResolvedEvent?) {
                val recordedEvent = event?.event ?: return
                val sub = subscription ?: return
                runBlocking {
                    try {
                        !onEvent(
                            Event(
                                id = recordedEvent.eventId,
                                value = json.decodeFromString<T>(recordedEvent.eventData.decodeToString()),
                                metadata = json.decodeFromString<EventMetadata>(recordedEvent.userMetadata.decodeToString()),
                                streamName = recordedEvent.streamId,
                                revision = recordedEvent.revision
                            )
                        )
                        sub.ack(event)
                    } catch (e: SerializationException) {
                        println("[Serialization error: ${retryCount} ${subscription.subscriptionId} ${recordedEvent.eventId}] ${e.message} ${e.localizedMessage}")
                        sub.nack(NackAction.Skip, e.stackTraceToString(), event)
                    } catch (e: Throwable) {
                        println("[EVENT HANDLER ERROR: ${retryCount} ${subscription.subscriptionId} ${recordedEvent.eventId}] ${e.message} ${e.localizedMessage}")
                        sub.nack(NackAction.Retry, e.stackTraceToString(), event)
                    }
                }
            }
        }
    ).toAsync()

    Unit

}

inline fun <reified T> EventStoreClient.appendToStream(
    streamName: String,
    dataList: List<Event<T>>,
    revision: StreamRevision = StreamRevision.AnyRevision
): Async<WriteResult>  = Async {

    val options = AppendToStreamOptions.get()
        .expectedRevision(revision.toRevision);


    val eventData = dataList.map { data ->
        EventData
            .builderAsBinary(data.id, T::class.jvmName, encodeToBytes(data.value))
            .metadataAsBytes(encodeToBytes(data.metadata))
            .build()
    }

   !client
        .appendToStream(streamName, options, *eventData.toTypedArray())
        .toAsync()

}