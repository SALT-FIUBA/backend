package io.kauth.service.mqtt

import MQTTClient
import io.kauth.abstractions.state.Var
import io.kauth.abstractions.state.varNew
import io.kauth.service.mqtt.MqttConnectorService.MqttData
import io.kauth.util.Async
import io.kauth.util.not
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mqtt.Subscription
import mqtt.packets.Qos
import mqtt.packets.mqttv5.ReasonCode
import socket.SocketClosedException
import java.util.*

fun mqttRequesterNew(
    initMqtt: Async<MQTTClient>,
    json: Json,
    coroutineScope: CoroutineScope
) = Async {
    MqttRequester(
        initMqtt,
        json,
        coroutineScope
    )
}

data class MqttRequester(
    val initMqtt: Async<MQTTClient>,
    val serializable: Json,
    val coroutineScope: CoroutineScope
): CoroutineScope by coroutineScope {

    val job: Var<Job?> = !varNew<Job?>(null)
    val mqtt: Var<MQTTClient?> = !varNew<MQTTClient?>(null)
    private val subscriptions: Var<List<Subscription>> = !varNew<List<Subscription>>(emptyList())

    fun connect() = Async {
        !mqtt.set(!initMqtt)
        !job.set(
            launch(Dispatchers.IO) {
                while (isActive && mqtt.get()!!.running) {
                    try {
                        mqtt!!.get()!!.step()
                    } catch (e: Throwable) {
                        //TODO retry forEver?
                        !reconnect
                    }
                }
            }
        )
    }

    val disconnect get() = Async {
        mqtt.get()!!.disconnect(ReasonCode.SUCCESS)
        job.get()!!.cancelAndJoin()
    }

    val reconnect get() = Async {
        !mqtt.set(!initMqtt)
        val subs = subscriptions.get()
        if(subs.isNotEmpty()) {
            mqtt.get()!!.subscribe(subs)
        }
    }

    fun subscribe(subs: List<Subscription>) = Async {
        val client = mqtt.get() ?: error("Not connected")
        val actualSubs = !subscriptions.get
        val filteredSubs = subs.filter { it !in actualSubs }
        if (filteredSubs.isEmpty()) {
            println("ERROR: subscribe empty")
            return@Async
        }
        !subscriptions.set(!subscriptions.get + filteredSubs)
        client.subscribe(filteredSubs)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    inline fun <reified T> publish(
        topic: String,
        data: T,
        idempotence: UUID,
        retain: Boolean = false
    ): Async<Unit> = Async {
        val payload = serializable.encodeToString(MqttData(data, idempotence)).toByteArray().toUByteArray()
        withContext(Dispatchers.IO) {
            mqtt.get()!!.publish(
                qos = Qos.AT_LEAST_ONCE,
                payload = payload,
                retain = retain,
                topic = topic
            )
        }
    }

}