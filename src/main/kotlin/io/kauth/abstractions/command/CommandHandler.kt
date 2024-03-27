package io.kauth.abstractions.command

import io.kauth.abstractions.result.Output
import io.kauth.abstractions.result.throwOnFailure
import io.kauth.monad.stack.AuthStack

typealias CommandHandler<C, O> = (command: C) -> AuthStack<O>
typealias CommandResultHandler<C> = CommandHandler<C, Output>
typealias CommandThrowOnErrorHandler<C> = CommandHandler<C, Unit>

val <C> CommandResultHandler<C>.throwOnFailureHandler get(): CommandThrowOnErrorHandler<C> = { command ->
    AuthStack.Do {
        val result = !this@throwOnFailureHandler.invoke(command)
        !result.throwOnFailure
    }
}