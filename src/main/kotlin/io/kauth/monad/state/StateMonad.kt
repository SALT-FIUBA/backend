package io.kauth.monad.state

import kotlin.experimental.ExperimentalTypeInference



//StateMachine

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