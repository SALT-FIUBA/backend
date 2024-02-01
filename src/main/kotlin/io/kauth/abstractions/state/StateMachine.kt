package io.kauth.abstractions.state

import io.kauth.monad.state.StateMonad

typealias StateMachine<C,S,E, O> = (command: C) -> StateMonad<S?, E, O>

fun <C,S,E,O> StateMachine<C,S,E,O>.runMany(commands: List<C>, initial: S?) =
    commands.foldRight(Pair(initial, emptyList<E>())) { command, prev ->
        val (newState, events) = this(command).run(prev.first)
        newState to (prev.second + events)
    }

fun <CA,S,E,CB,O> StateMachine<CA,S,E,O>.cmap(f: (CB) -> CA): StateMachine<CB,S,E,O> = { command -> this(f(command)) }