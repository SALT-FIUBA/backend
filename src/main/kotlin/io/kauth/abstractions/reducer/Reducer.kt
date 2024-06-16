package io.kauth.abstractions.reducer

import kotlin.reflect.KClass

data class Reducer<S,E>(
    val run: (S,E) -> S
)

fun <S,E> Reducer<S?, E>.runMany(events: List<E>, initial: S?) =
    events.fold(initial, this.run)

fun <S,E : Any> reducerOf(vararg reducers: Pair<KClass<out E>, Reducer<S, out E>>) = Reducer<S,E> { state, event ->
    val reducerMap = reducers.toMap()
    val reducer = reducerMap.get(event::class)
    if(reducer != null) {
        val r = (reducer as? Reducer<S,E>) ?: error("Invalid reducer for ${event::class}")
        r.run(state, event)
    }
    else {
        state
    }
}
