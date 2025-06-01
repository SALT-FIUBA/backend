package io.kauth.service.recurringoccasion

import io.kauth.abstractions.reducer.Reducer
import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.CommandMonad
import io.kauth.service.occasion.Occasion
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

object RecurringOccasion {

    @Serializable
    data class RecurringConfig(
        val daysOfWeek: List<Int>? = null // for weekly recurrence, 1=Monday
    )

    @Serializable
    data class State(
        @Contextual val id: UUID,
        val config: RecurringConfig,
        val occasionTemplate: Occasion.Command.CreateOccasion,
        val lastGenerated: Instant? = null,
        val disabled: Boolean = false,
        val createdOccasions: List<@Contextual UUID> = emptyList(),
    )

    @Serializable
    sealed interface Command {

        @Serializable
        data class CreateRecurringOccasion(
            val config: RecurringConfig,
            val occasionTemplate: Occasion.Command.CreateOccasion
        ) : Command

        @Serializable
        data class CheckNextOccasion(
            val now: Instant,
            @Contextual val id: UUID,
        ) : Command

        @Serializable
        data class Disable(val disabledAt: Instant) : Command

        @Serializable
        data class OccasionCreateResult(
            @Contextual val occasionId: UUID?,
            val success: Boolean,
            val error: String? = null,
            val generatedAt: Instant
        ) : Event
    }

    @Serializable
    sealed interface Event {
        @Serializable
        data class RecurringOccasionCreated(val state: State) : Event

        @Serializable
        data class GenerateOccasion(val occasion: Occasion.Command.CreateOccasion) : Event

        @Serializable
        data class GeneratedOccasion(@Contextual val occasion: UUID) : Event

        @Serializable
        data class Disabled(val disabledAt: Instant) : Event
    }

    // --- Command Handlers ---
    val handleCreate: CommandMonad<Command.CreateRecurringOccasion, State?, Event, Output> = CommandMonad.Do { exit ->
        val state = !getState
        if (state != null) {
            !emitEvents(Event.RecurringOccasionCreated(state))
            !exit(Failure("RecurringOccasion already exists"))
        }
        val id = UUID.randomUUID()
        val newState = State(
            id = id,
            config = command.config,
            occasionTemplate = command.occasionTemplate,
            lastGenerated = null,
            disabled = false,
            createdOccasions = emptyList()
        )
        !emitEvents(Event.RecurringOccasionCreated(newState))
        Ok
    }

    val handleDisable: CommandMonad<Command.Disable, State?, Event, Output> = CommandMonad.Do { exit ->
        val state = !getState
        if (state == null) {
            !exit(Failure("RecurringOccasion does not exist"))
        }
        if (state.disabled) {
            !exit(Ok)
        }
        !emitEvents(Event.Disabled(command.disabledAt))
        Ok
    }

    val handleOccasionCreateResult: CommandMonad<Command.OccasionCreateResult, State?, Event, Output> = CommandMonad.Do { exit ->
        val state = !getState
        if (state == null) {
            !exit(Failure("RecurringOccasion does not exist"))
        }
        if (command.success && command.occasionId != null) {
            // Update state with new occasion
            val updatedState = state.copy(
                lastGenerated = command.generatedAt,
                createdOccasions = state.createdOccasions + command.occasionId
            )
            !emitEvents(Event.GeneratedOccasion(command.occasionId))
        }
        Ok
    }

    val handleCheckNextOccasion: CommandMonad<Command.CheckNextOccasion, State?, Event, Output> = CommandMonad.Do { exit ->
        val state = !getState
        if (state == null) {
            !exit(Failure("RecurringOccasion does not exist"))
        }
        if (state.disabled) {
            !exit(Ok)
        }
        // Simple daily recurrence logic (replace with real logic as needed)
        val last = state.lastGenerated
        val shouldGenerate = last == null || command.now.epochSeconds - last.epochSeconds >= 86400
        if (shouldGenerate) {
            val nextOccasion = state.occasionTemplate.copy(
                resource = UUID.randomUUID().toString(),
                startDateTime = command.now,
                endDateTime = command.now.plus(3600.seconds),
                createdAt = command.now
            )
            !emitEvents(Event.GenerateOccasion(nextOccasion))
        }
        Ok
    }

    // --- Reducers ---
    val handleRecurringOccasionCreated: Reducer<State?, Event.RecurringOccasionCreated> = Reducer { _, event ->
        event.state
    }

    val handleDisabled: Reducer<State?, Event.Disabled> = Reducer { state, _ ->
        state?.copy(disabled = true)
    }

    val handleGeneratedOccasion: Reducer<State?, Event.GeneratedOccasion> = Reducer { state, event ->
        state?.copy(createdOccasions = state.createdOccasions + event.occasion, lastGenerated = state?.lastGenerated)
    }

    val handleGenerateOccasion: Reducer<State?, Event.GenerateOccasion> = Reducer { state, _ ->
        state // No state change until OccasionCreateResult is received
    }

}
