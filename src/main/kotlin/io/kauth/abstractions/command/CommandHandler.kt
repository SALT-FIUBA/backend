package io.kauth.abstractions.command

import io.kauth.abstractions.result.Output
import io.kauth.abstractions.result.throwOnFailure
import io.kauth.monad.stack.AppStack

typealias CommandHandler<C, O> = (command: C) -> AppStack<O>
typealias CommandResultHandler<C> = CommandHandler<C, Output>
typealias CommandThrowOnErrorHandler<C> = CommandHandler<C, Unit>

val <C> CommandResultHandler<C>.throwOnFailureHandler get(): CommandThrowOnErrorHandler<C> = { command ->
    AppStack.Do {
        val result = !this@throwOnFailureHandler.invoke(command)
        !result.throwOnFailure
    }
}