package io.kauth.client.eventStore

import com.eventstore.dbclient.ExpectedRevision
import com.eventstore.dbclient.WriteResult
import io.kauth.abstractions.command.CommandHandler
import io.kauth.abstractions.reducer.Reducer
import io.kauth.abstractions.reducer.runMany
import io.kauth.client.eventStore.model.*
import io.kauth.client.eventStore.model.toStreamRevision
import io.kauth.monad.stack.AppStack
import io.kauth.monad.state.CommandMonad
import io.kauth.util.Async
import io.kauth.util.not
import kotlinx.datetime.Clock
import java.util.*

//StreamStrategy ? --> Puede o no tener snapshot
data class EventStoreStreamSnapshot<E,S>(
    val stream: EventStoreStream<E>,
    val snapshot: EventStoreStream<S>?
)

//Este stream guarda eventos de tipo E
data class EventStoreStream<E>(
    val client: EventStoreClient,
    val name: String,
)

@JvmName("appendData")
inline fun <reified E> EventStoreStream<E>.append(
    data: E,
    revision: StreamRevision = StreamRevision.AnyRevision,
    idempotenceId: (E) -> UUID = { UUID.randomUUID() }
) = append(listOf(data), revision, idempotenceId)

@JvmName("appendEvent")
inline fun <reified E>  EventStoreStream<E>.append(
    data: Event<E>,
    revision: StreamRevision = StreamRevision.AnyRevision
) = append(listOf(data), revision)

@JvmName("appendList")
inline fun <reified E>  EventStoreStream<E>.append(
    data: List<E>,
    revision: StreamRevision = StreamRevision.AnyRevision,
    idempotenceId: (E) -> UUID = { UUID.randomUUID() }
) = append(
    data.map { Event(
        id = idempotenceId(it),
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
    snapshotName: String? = null
) = EventStoreStreamSnapshot<E,S>(
    stream = stream(client, streamName),
    snapshot = if(snapshotName != null) stream(client, snapshotName) else null
)

fun <E> stream(
    client: EventStoreClient,
    streamName: String,
) = EventStoreStream<E>(client, streamName)


inline fun <C, reified S, reified E, O>  EventStoreStreamSnapshot<E, S>.commandHandlerIdempotent(
    commandStateMachine: CommandMonad<C,S?,E,O>,
    eventStateMachine: Reducer<S?,E>,
    crossinline idempotenceId: (E) -> UUID,
): CommandHandler<C, O> = { command: C ->
    AppStack.Do {

        //actual state computation
        val (state, revision) = !computeState<E,S>(eventStateMachine)

        //new events generated
        val (newEvents, output) = commandStateMachine.run(command, state)

        val result = !stream.append<E>(newEvents, revision ?: StreamRevision.NoStream, idempotenceId)

        if(snapshot != null) {
            //new state computation
            val newState = eventStateMachine.runMany(newEvents, state)
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
        }

        output

    }
}

inline fun <C, reified S, reified E, O>  EventStoreStreamSnapshot<E, S>.commandHandler(
    commandStateMachine: CommandMonad<C,S?,E,O>,
    eventStateMachine: Reducer<S?,E>,
): CommandHandler<C, O> = commandHandlerIdempotent(commandStateMachine,eventStateMachine) { UUID.randomUUID() }

inline fun <reified E, reified S> EventStoreStreamSnapshot<E, S>.computeState(
    eventReducer: Reducer<S?,E>,
) = Async {

    val snapshotResult = if(snapshot != null) !snapshot.readLast() else null
    val state = snapshotResult?.value
    val snapshotRevision = snapshotResult?.metadata?.snapshottedStreamRevision?.toRevision?.toRawLong()

    val streamResult = !stream.read(
        revision = if(snapshotRevision != null) snapshotRevision + 1L else 0L
    )

    val events = streamResult?.events?.mapNotNull { it.value } ?: emptyList()

    val newState = eventReducer.runMany(events, state)

    newState to (streamResult?.lastStreamPosition?.let { ExpectedRevision.fromRawLong(it) }?.toStreamRevision )

}


inline fun <reified E, reified S> EventStoreStreamSnapshot<E, S>.computeStateResult(
    eventReducer: Reducer<S?,E>,
) = Async {
    val (fst, snd) = !computeState(eventReducer)
    return@Async fst
}


