package io.kauth.abstractions.state

import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.monad.state.StateMonad
import io.kauth.monad.state.StateMonad.Companion.StateMonadContext
import io.kauth.service.device.Device.Event
import io.kauth.service.device.Device.State
import kotlin.experimental.ExperimentalTypeInference

typealias StateMachine<C,S,E,O> = (command: C) -> StateMonad<S?, E, O>

fun <C,S,E,O> StateMachine<C,S,E,O>.runMany(commands: List<C>, initial: S?) =
    commands.foldRight(Pair(initial, emptyList<E>())) { command, prev ->
        val (newState, events) = this(command).run(prev.first)
        newState to (prev.second + events)
    }

fun <CA,S,E,CB,O> StateMachine<CA,S,E,O>.cmap(f: (CB) -> CA): StateMachine<CB,S,E,O> = { command -> this(f(command)) }

//TypeClass Handler
interface StateMachinieHandler<C, S, out E, out O> {
    fun C.handle(): StateMonad<S?,E,O>
}

@OptIn(ExperimentalTypeInference::class)
fun <C,S,E,O> StateMachineHandler(@BuilderInference handle: StateMonadContext<S?, E>.(command: C, exit: (value: O) -> StateMonad<S?, E, Nothing>) -> O): StateMachinieHandler<C,S,E,O> =
    object : StateMachinieHandler<C,S,E,O> {
        override fun C.handle(): StateMonad<S?, E, O> {
            return StateMonad.Do { exit ->
                handle(this@Do, this@handle, exit)
            }
        }
    }

fun <C,S,E,O> buildStateMachine(command: C, handler: StateMachinieHandler<C,S,E,O>): StateMonad<S?,E,O> {
    return with(handler) {
        command.handle()
    }
}
