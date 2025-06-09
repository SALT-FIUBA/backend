package io.kauth.service.mqtt

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import io.kauth.abstractions.state.Var
import io.kauth.abstractions.state.varNew
import io.kauth.service.mqtt.MqttConnectorService.MqttData
import io.kauth.util.Async
import io.kauth.util.io
import io.kauth.util.not
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.*

fun mqttRequesterHiveMQNew(
    username: String,
    password: String,
    serverHost: String,
    port: Int,
    json: Json,
    coroutineScope: CoroutineScope,
    consumerGroup: String,
    callback: (Mqtt5Publish) -> Async<Unit>
) = Async {

    //https://console.hivemq.cloud/clients/java-hivemq?uuid=007f5e0286aa4c36ba410312d36d42f0
    val client: Mqtt5AsyncClient = MqttClient.builder()
        .useMqttVersion5()
        .serverHost(serverHost)
        .serverPort(port)
        .sslWithDefaultConfig()
        .buildAsync()

    /* CONNECTION */
    client.connectWith().simpleAuth()
        .username(username)
        .password(password.toByteArray())
        .applySimpleAuth()
        .send()
        .await()

    client.publishes(MqttGlobalPublishFilter.ALL) {
        !callback(it).io
    }

    MqttRequesterHiveMQ(
        client,
        json,
        coroutineScope,
        consumerGroup,
        callback
    )
}

data class MqttRequesterHiveMQ(
    val client: Mqtt5AsyncClient,
    val serializable: Json,
    val coroutineScope: CoroutineScope,
    val consumerGroup: String,
    val callback: (Mqtt5Publish) -> Async<Unit>
): CoroutineScope by coroutineScope {

    private val subscriptions: Var<List<String>> = !varNew<List<String>>(emptyList())

    val getSubscriptions get() =
        subscriptions.get

    val disconnect get() = Async {
        client.disconnect().await()
    }

    fun unsubscribe(topic: String) = Async {
        client.unsubscribeWith()
            .topicFilter("\$share/${consumerGroup}/$topic")
            .send()
            .await()
        !subscriptions.set(!subscriptions.get - topic)
    }

    fun subscribe(subs: List<String>) = Async {
        val actualSubs = !subscriptions.get
        val filteredSubs = subs.filter { it !in actualSubs }
        if (filteredSubs.isEmpty()) {
            return@Async emptyList()
        }
        !subscriptions.set(!subscriptions.get + filteredSubs)
        filteredSubs.forEach {
            client
                .subscribeWith()
                .topicFilter("\$share/${consumerGroup}/$it")
                .send()
                .await()
        }
        filteredSubs
    }


    fun JsonElement.primitiveOrNull(): JsonPrimitive? {
        return try {
            this.jsonPrimitive
        } catch (e: Throwable) {
            null
        }
    }

    inline fun <reified T> publish(
        topic: String,
        data: T,
        retain: Boolean = false
    ): Async<Unit> = Async {
        val json = serializable.encodeToJsonElement(data)
        val primitive = json.primitiveOrNull()?.contentOrNull
        val payload = serializable.encodeToString(data)
        client.publishWith()
            .topic(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .retain(retain)
            .payload((primitive ?: payload).toByteArray())
            .send()
            .await()
    }

}