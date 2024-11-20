package io.kauth.client.pulsar.auth

import org.apache.pulsar.client.api.Authentication
import org.apache.pulsar.client.api.AuthenticationDataProvider
import org.apache.pulsar.shade.org.apache.commons.codec.digest.DigestUtils

class MqAuth(
    private val accessId: String,
    private val accessKey: String
) : Authentication {
    override fun close() {
        TODO("Not yet implemented")
    }

    override fun getAuthData(): AuthenticationDataProvider {
        return MqAuthenticationDataProvider(accessId, accessKey)
    }

    override fun getAuthMethodName(): String =
        "auth1"

    override fun configure(authParams: MutableMap<String, String>?) {
    }

    override fun start() {
    }

}

class MqAuthenticationDataProvider(accessId: String, accessKey: String) : AuthenticationDataProvider {

    private val commandData: String = String.format(
        "{\"username\":\"%s\",\"password\":\"%s\"}",
        accessId,
        DigestUtils.md5Hex(accessId + DigestUtils.md5Hex(accessKey)).substring(8, 24)
    )

    override fun getCommandData(): String {
        return commandData
    }

    override fun hasDataForHttp(): Boolean {
        return false
    }

    override fun hasDataFromCommand(): Boolean {
        return true
    }
}
