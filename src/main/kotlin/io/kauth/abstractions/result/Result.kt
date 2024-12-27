package io.kauth.abstractions.result

import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.apicall.ApiCall
import io.kauth.monad.stack.AppStack
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Result<out T, out E>
@Serializable
@SerialName("Success")
data class Success<T>(val data: T) : Result<T, Nothing>()
@Serializable
@SerialName("Failure")
data class Failure<E>(val message: E) : Result<Nothing, E>()

val <T, E> Result<T, E>.throwOnFailure
    get(): AppStack<T> = AppStack.Do {
        when (this@throwOnFailure) {
            is Success<T> -> data
            is Failure<E> -> {
                !ApiException(message.toString())
            }
        }
    }

typealias Output = Result<Unit, String>
typealias Fail = Failure<String>
typealias Done = Success<Unit>
val Ok = Success(Unit)