package io.kauth.monad.state

import kotlin.experimental.ExperimentalTypeInference

data class CommandMonad<out C,in S,out E,out T>(
    val run: (@UnsafeVariance C, S) -> Pair<List<E>, T>
) {

    companion object {
        class MonadContext<S,C,E>(
            var events: MutableList<E>,
            val command: C,
            val state: S
        ) {
            operator fun <T> CommandMonad<C,S,E, T>.not() = bind(this)
            fun <T> bind(monad: CommandMonad<C,S,E,T>): T {
                val (newEvents, output) = monad.run(command, state)
                events += newEvents
                return output
            }
            fun emitEvents(vararg event: E)= emit<C,S,E>(*event)
            val getState get() = get<C,S>()
            val getCommand get() = getCommand<C,S>()
        }

        private class Ref
        private class MonadException(val ref: Ref, val value: Any?): Throwable()

        fun <C,S,E> emit(vararg event: E): CommandMonad<C, S, E, Unit> =
            CommandMonad { _, _ -> Pair(event.toList(), Unit) }

        fun <C,S> get(): CommandMonad<C, S, Nothing, S> =
            CommandMonad { _, state -> Pair(emptyList(), state) }

        fun <C,S> getCommand(): CommandMonad<C,S, Nothing, C> =
            CommandMonad { command, _ -> Pair(emptyList(), command) }

        @OptIn(ExperimentalTypeInference::class)
        fun <C, S, E, T> Do(
            @BuilderInference body: MonadContext<S,C,E>.(exit: (value: T) -> CommandMonad<C,S,E,Nothing>) -> T
        ):CommandMonad<C, S, E, T> =
            CommandMonad { command, state ->
                val context = MonadContext(mutableListOf<E>(),command,state)
                val ref = Ref()
                val output = try {
                    context.body { value -> throw MonadException(ref, value) }
                } catch (e: MonadException) {
                    if(e.ref === ref) e.value as T else throw e
                }
                Pair(
                    context.events,
                    output
                )
            }

    }


}

data class EventMonad<S, out E,out T>(
    val run: (@UnsafeVariance E, S) -> Pair<S, T>
) {

    companion object {
        class EventMonadContext<S, E>(
            var state: S,
            val event: E
        ) {
            operator fun <T> EventMonad<S, E, T>.not() = bind(this)
            fun <T> bind(monad: EventMonad<S, E, T>): T {
                val (newState, output) = monad.run(event, state)
                state = newState
                return output
            }
            val getState get() = get<S,E>()
            val getEvent get() = getEvent<S,E>()
            fun setState(state: S) = set<S,E>(state)
        }

        private class Ref
        private class EventMonadException(val ref: Ref, val value: Any?): Throwable()

        fun <S, E> set(state: S): EventMonad<S, E, Unit> =
            EventMonad { _, _ -> Pair(state, Unit) }

        fun <S, E> get(): EventMonad<S, E, S> =
            EventMonad { _, state -> Pair(state, state) }

        fun <S, E> getEvent(): EventMonad<S, E, E> =
            EventMonad { event, state -> Pair(state, event) }

        fun <S,O> noOp(o: O): EventMonad<S,Any,O> =
            EventMonad<S,Any,O> { _, state -> Pair(state, o) }

        fun <S,E,O> EventMonad<S?,E,O>.runMany(events: List<E>, initial: S?) =
            events.fold(initial) { prev, event ->
                val (newState, _) = this.run(event, prev)
                newState
            }

        @OptIn(ExperimentalTypeInference::class)
        fun <S, E, T> Do(
            @BuilderInference body: EventMonadContext<S, E>.(exit: (value: T) -> EventMonad<S, E, Nothing>) -> T
        ): EventMonad<S, E, T> =
            EventMonad { event, initial ->
                val context = EventMonadContext(initial, event)
                val ref = Ref()
                val output = try {
                    context.body { value -> throw EventMonadException(ref, value) }
                } catch (e: EventMonadException) {
                    if(e.ref === ref) e.value as T else throw e
                }
                Pair(
                    context.state,
                    output
                )
            }

    }


}

/*
StateMonad
    - S: State
    - E: Event
    - T: Output
 */
data class StateMonad<S, out E, out T>(
    val run: (S) -> Triple<S, List<E>, T>
) {

    companion object {

        class StateMonadContext<S, E>(
            var state: S,
            val events: MutableList<E>
        ) {

            operator fun <T> StateMonad<S, E, T>.not() = bind(this)

            fun <T> bind(monad: StateMonad<S, E, T>): T {
                val (newState, newEvents, output) = monad.run(state)
                state = newState
                events += newEvents
                return output
            }

            val getState get() = get<S>()
            fun setState(state: S) = set<S>(state)
            fun emitEvents(vararg event: E)= emit<S, E>(*event)

        }

        private class StateMonadDoException(val ref: Ref, val value: Any?): Throwable()
        private class Ref

        fun <S,E> emit(vararg event: E): StateMonad<S, E, Unit> =
            StateMonad { state -> Triple(state, event.toList(), Unit) }

        fun <S> set(state: S): StateMonad<S, Nothing, Unit> =
            StateMonad { _ -> Triple(state, emptyList(), Unit) }

        fun <S> get(): StateMonad<S, Nothing, S> =
            StateMonad { state -> Triple(state, emptyList(), state) }

        fun <S,O> noOp(o: O): StateMonad<S,Nothing,O> =
            StateMonad { state -> Triple(state, emptyList(), o) }

        @OptIn(ExperimentalTypeInference::class)
        fun <S, E, T> Do(
            @BuilderInference body: StateMonadContext<S, E>.(exit: (value: T) -> StateMonad<S, E, Nothing>) -> T
        ): StateMonad<S, E, T> =
            StateMonad { initial ->

                val context = StateMonadContext<S, E>(initial, mutableListOf<E>())

                val ref = Ref()

                val output = try {
                    context.body { value -> throw StateMonadDoException(ref, value) }
                } catch (e: StateMonadDoException) {
                    if(e.ref === ref) e.value as T else throw e
                }

                Triple(
                    context.state,
                    context.events.toList(),
                    output
                )

            }

    }

}