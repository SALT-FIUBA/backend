package io.kauth.abstractions.command

import io.kauth.util.Async

typealias CommandHandler<C, O> = (command: C) -> Async<O>
