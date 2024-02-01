package io.kauth.util


typealias IO<T> = () -> T
fun <T>IO(block: () -> T) = block
operator fun <T> IO<T>.not() = this()
