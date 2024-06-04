package io.kauth.abstractions.state

import io.kauth.util.IO

data class Var<T>(
    val get: IO<T>,
    val set: (T) -> IO<Unit>
)

fun <T> varNew(initial: T) = IO {
    var state = initial
    Var(
        get = { state },
        set = { IO { state = it } }
    )
}