package io.kauth.client.eventStore.model

import com.eventstore.dbclient.ExpectedRevision
import kotlinx.serialization.Serializable

/*
    Representa En que estado tiene que estar el stream en una escritura,
    sirve para implementar optimistic concurrency cuando lee te podes fijas response.lastStreamPosition
    y cuando escribis podes mandar ese dato para chequear si alguien escribio antes
 */
@Serializable
sealed interface StreamRevision {
    @Serializable
    data object AnyRevision: StreamRevision
    @Serializable
    data object NoStream: StreamRevision
    @Serializable
    data object StreamExists: StreamRevision
    //Optimistic concurrency
    @Serializable
    data class Expected(val revision: Long): StreamRevision

}

val ExpectedRevision.toStreamRevision get() =
   when(this.toRawLong()) {
       -1L -> StreamRevision.NoStream
       -2L -> StreamRevision.AnyRevision
       -4L -> StreamRevision.StreamExists
       else -> StreamRevision.Expected(this.toRawLong())
   }

val StreamRevision.toRevision get() =
    when(this) {
        is StreamRevision.AnyRevision -> ExpectedRevision.any()
        is StreamRevision.NoStream -> ExpectedRevision.noStream()
        is StreamRevision.StreamExists -> ExpectedRevision.streamExists()
        is StreamRevision.Expected -> ExpectedRevision.expectedRevision(revision)
    }