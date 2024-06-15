package io.kauth.service.reservation

import io.kauth.abstractions.result.Fail
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.CommandMonad
import io.kauth.monad.state.EventMonad
import io.kauth.monad.state.StateMonad
import io.kauth.service.salt.Device
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

    val ResourceEvent.asCommand get() =
        when(this) {
            is ResourceEvent.ResourceReleased -> Command.Release
            is ResourceEvent.ResourceTaken -> Command.Take(ownerId)
        }


    val handleTakenEvent get() = EventMonad.Do<Reservation?, ResourceEvent.ResourceTaken, Output> { exit ->
        val state = !getState
        !setState(state?.copy(taken = true) ?: Reservation(taken = true, ownerId = event.ownerId))
        Ok
    }

    val handleReleasedEvent get() = EventMonad.Do<Reservation?, ResourceEvent.ResourceReleased, Output> { exit ->
        val state = !getState
        !setState(state?.copy(taken = false))
        Ok
    }


    val handleTake get() = CommandMonad.Do<Command.Take, Reservation?, ResourceEvent, Output> { exit ->
        val state = !getState
        if(state != null && state.taken) { !exit(Fail("Resource taken")) }
        !emitEvents(ResourceEvent.ResourceTaken(command.ownerId))
        Ok
    }

    val handleRelease get() = CommandMonad.Do<Command.Release, Reservation?, ResourceEvent,Output> { exit ->
        !getState ?: !exit(Fail("No resource exists"))
        !emitEvents(ResourceEvent.ResourceReleased)
        Ok
    }

    val stateMachine get() =
        CommandMonad.Do<Command, Reservation?, ResourceEvent,Output> { exit ->
            val command = !getCommand
            !when (command) {
                is Command.Take -> handleTake
                is Command.Release -> handleRelease
            }
        }

    val eventStateMachine get() = EventMonad.Do<Reservation?, ResourceEvent, Output> {
        val event = !getEvent
        !when(event) {
            is ResourceEvent.ResourceTaken -> handleTakenEvent
            is ResourceEvent.ResourceReleased -> handleReleasedEvent
        }
    }

}
