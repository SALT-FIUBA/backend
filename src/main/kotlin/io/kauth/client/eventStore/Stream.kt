package io.kauth.client.eventStore

import com.eventstore.dbclient.ExpectedRevision
import com.eventstore.dbclient.WriteResult
import io.kauth.abstractions.command.CommandHandler
import io.kauth.abstractions.state.StateMachine
import io.kauth.abstractions.state.cmap
import io.kauth.abstractions.state.runMany
import io.kauth.client.eventStore.model.*
import io.kauth.client.eventStore.model.toStreamRevision
import io.kauth.util.Async
import io.kauth.util.not
import kotlinx.datetime.Clock
import java.util.*

//StreamStrategy ? --> Puede o no tener snapshot
data class EventStoreStreamSnapshot<E,S>(
    //comanStream ?
    //logStream ?
    val stream: EventStoreStream<E>,
    val snapshot: EventStoreStream<S>
)

//Este stream guarda eventos de tipo E
data class EventStoreStream<E>(
    val client: EventStoreClient,
    val name: String,
)

@JvmName("appendData")
inline fun <reified E> EventStoreStream<E>.append(
    data: E,
    revision: StreamRevision = StreamRevision.AnyRevision
) = append(listOf(data), revision)

@JvmName("appendEvent")
inline fun <reified E>  EventStoreStream<E>.append(
    data: Event<E>,
    revision: StreamRevision = StreamRevision.AnyRevision
) = append(listOf(data), revision)

@JvmName("appendList")
inline fun <reified E>  EventStoreStream<E>.append(
    data: List<E>,
    revision: StreamRevision = StreamRevision.AnyRevision
) = append(
    data.map { Event(
        id = UUID.randomUUID(),
        value = it,
        metadata = EventMetadata(Clock.System.now()),
        streamName = this.name,
        revision = revision.toRevision.toRawLong()
    ) },
    revision
)

@JvmName("appendListEvent")
inline fun <reified E> EventStoreStream<E>.append(
    dataList: List<Event<E>>,
    revision: StreamRevision = StreamRevision.AnyRevision
): Async<WriteResult> = Async {
    !client.appendToStream(
        name,
        dataList,
        revision
    )
}

inline fun <reified E> EventStoreStream<E>.read(
    forward: Boolean = true,
    revision: Long? = 0,
    limit: Long? = null,
) = Async {
    !client.readFromStream<E>(
        name,
        forward,
        revision,
        limit
    )
}

inline fun <reified E> EventStoreStream<E>.readLast() = Async {
    val result = !read(forward = false, limit = 1)
    result?.events?.firstOrNull()
}

fun <E,S> stream(
    client: EventStoreClient,
    streamName: String,
    snapshotName: String
) = EventStoreStreamSnapshot<E,S>(
    stream = EventStoreStream(client, streamName),
    snapshot = EventStoreStream(client, snapshotName)
)



inline fun <C, reified S, reified E, O>  EventStoreStreamSnapshot<E, S>.commandHandler(
    noinline stateMachine: StateMachine<C,S,E,O>,
    crossinline eventToCommand: (E) -> C? // Esto te lo podes ahorrar si de alguna froma persistis los commands
): CommandHandler<C, O> = { command: C ->
    Async {

        val (state, revision) = !computeState<E,S,O,C>(stateMachine, eventToCommand)

        val (newState, newEvents, output) = stateMachine(command).run(state)

        val result = !stream.append<E>(newEvents, revision ?: StreamRevision.NoStream)

        if (newState != null && newState != state) {
            !snapshot.append(
                Event(
                    id = UUID.randomUUID(),
                    value = newState,
                    metadata = EventMetadata(
                        timestamp = Clock.System.now(),
                        snapshottedStreamRevision = result.nextExpectedRevision.toStreamRevision
                    ),
                    streamName = snapshot.name,
                    revision = StreamRevision.AnyRevision.toRevision.toRawLong()
                ),
            )
        }

        output

    }
}

inline fun <reified E, reified S, O, C> EventStoreStreamSnapshot<E, S>.computeState(
    noinline stateMachine: StateMachine<C, S, E, O>,
    crossinline eventsToCommand: (E) -> C?
) = Async {

    val snapshotResult = !snapshot.readLast()
    val state = snapshotResult?.value

    val streamResult = !stream.read(revision = (snapshotResult?.metadata?.snapshottedStreamRevision?.toRevision?.toRawLong() ?: 0L) + 1)

    val events = streamResult?.events?.mapNotNull { eventsToCommand(it.value) } ?: emptyList()

    val (newState, _) = stateMachine.runMany(events, state)

    newState to (streamResult?.lastStreamPosition?.let { ExpectedRevision.fromRawLong(it) }?.toStreamRevision )

}


inline fun <reified E, reified S, O, C> EventStoreStreamSnapshot<E, S>.computeStateResult(
    noinline stateMachine: StateMachine<C, S, E, O>,
    crossinline eventsToCommand: (E) -> C?
) = Async {
    val (fst, snd) = !computeState(stateMachine, eventsToCommand)
    return@Async fst
}


