package io.kauth.service.reservation

import io.kauth.monad.state.StateMonad
import kotlinx.serialization.Serializable

//Este servicio sirve para tomar/liberar un recurso
object Reservation {

    //Domain Model
    @Serializable
    data class Reservation(
        val taken: Boolean,
        val ownerId: String
    )

    //Commands (Inputs)
    @Serializable
    sealed interface Command {

        @Serializable
        data class Take(val ownerId: String): Command

        @Serializable
        data object Release: Command

    }

    //Lo que se guarda en el stream y se utiliza para reconstruir el estado
    @Serializable
    sealed interface ResourceEvent {

        @Serializable
        data class ResourceTaken(val ownerId: String): ResourceEvent

        @Serializable
        data object ResourceReleased: ResourceEvent

    }

    sealed interface Output

    data object Success : Output

    data class Error(val message: String) : Output

    val ResourceEvent.asCommand get() =
        when(this) {
            is ResourceEvent.ResourceReleased -> Command.Release
            is ResourceEvent.ResourceTaken -> Command.Take(ownerId)
        }


    fun handleTake(
        command: Command.Take
    ) =  StateMonad.Do<Reservation?, ResourceEvent, Output> { exit ->

        val state = !getState

        if(state != null && state.taken) {
            !exit(Error("Resource taken"))
        }

        !emitEvents(
            ResourceEvent.ResourceTaken(command.ownerId)
        )

        !setState(state?.copy(taken = true) ?: Reservation(taken = true, ownerId = command.ownerId))

        Success

    }

    fun handleRelease(
        command: Command.Release
    ) =  StateMonad.Do<Reservation?, ResourceEvent,Output> { exit ->

        val state = !getState ?: !exit(Error("No resource exists"))

        !emitEvents(
            ResourceEvent.ResourceReleased
        )

        !setState(state.copy(taken = false))

        Success

    }

    fun stateMachine(
        command: Command
    ) = StateMonad.Do<Reservation?, ResourceEvent, Output> {
        !when(command) {
            is Command.Take-> handleTake(command)
            is Command.Release -> handleRelease(command)
        }
    }

}
