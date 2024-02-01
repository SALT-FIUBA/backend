package io.kauth.monad.stack

import io.kauth.util.Async
import io.kauth.util.not
import kotlinx.coroutines.CoroutineScope

interface Dependency<in C, out T> {

    val run: (context: C) -> Async<T>

    companion object {

        fun <C> read() = Dependency<C,C> { context -> Async { context } }

        fun <T> pure(value: T): Dependency<Any, T> = object : Dependency<Any, T> {
            override val run: (context: Any) -> Async<T>
                get() = { Async { value } }
        }

        fun <C : CoroutineScope, T> Do(block: suspend DependencyContext<C>.() -> T): Dependency<C, T> =
            object: Dependency<C, T> {
                override val run: (context: C) -> Async<T>
                    get() = { context ->
                        Async {
                            val dc = DependencyContext(context = context)
                            val result = dc.block()
                            result
                        }
                    }
            }
    }

}

fun <C, T> Dependency(
    run: (context: C) -> Async<T>
) = object : Dependency<C, T> {
    override val run: (context: C) -> Async<T> get() = run
}


class DependencyContext<C : CoroutineScope>(
    val context: C
): CoroutineScope by context {

    suspend operator fun <T> Dependency<C, T>.not() = bind(this)

    suspend fun <T> bind(dependency: Dependency<C, T>): T =
        !dependency.run(context)

}