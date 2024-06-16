package io.kauth.monad.state

import kotlin.experimental.ExperimentalTypeInference

data class CommandMonad<C,in S,out E,out T>(
    val run: (C, S) -> Pair<List<E>, T>
) {

    companion object {
        class MonadContext<S,C,E>(
            var events: MutableList<E>,
            val command: C,
            val state: S
        ) {
            operator fun <T> CommandMonad<out C,S,E, T>.not() = bind(this)
            fun <T> bind(monad: CommandMonad<out C,S,E,T>): T {
                //@Suppress("UNCHECKED_CAST")
                val (newEvents, output) = (monad as CommandMonad<C,S,E,T>).run(command, state)
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