package io.kauth.service.mqtt

import MQTTClient
import io.kauth.abstractions.state.Var
import io.kauth.abstractions.state.varNew
import io.kauth.service.mqtt.MqttConnectorService.MqttData
import io.kauth.util.Async
import io.kauth.util.not
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    val mutex = Mutex()
    val job: Var<Job?> = !varNew<Job?>(null)
    val mqtt: Var<MQTTClient?> = !varNew<MQTTClient?>(null)
    private val subscriptions: Var<List<Subscription>> = !varNew<List<Subscription>>(emptyList())

    val getSubscriptions get() =
        subscriptions.get

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
        mutex.withLock {
            val client = mqtt.get() ?: error("Not connected")
            val actualSubs = !subscriptions.get
            val filteredSubs = subs.filter { it !in actualSubs }
            if (filteredSubs.isEmpty()) {
                return@Async emptyList()
            }
            !subscriptions.set(!subscriptions.get + filteredSubs)
            client.subscribe(filteredSubs)
            filteredSubs.map { it.topicFilter }
        }
    }

    fun unsubscribe(subs: List<String>) = Async {
        mutex.withLock {
            val client = mqtt.get() ?: error("Not connected")
            val actualSubs = (!subscriptions.get).map { it.topicFilter }
            val filteredSubs = subs.filter { it in actualSubs }
            if (filteredSubs.isEmpty()) {
                return@Async
            }
            !subscriptions.set((!subscriptions.get).filter { it.topicFilter !in filteredSubs })
            client.unsubscribe(filteredSubs)
        }
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