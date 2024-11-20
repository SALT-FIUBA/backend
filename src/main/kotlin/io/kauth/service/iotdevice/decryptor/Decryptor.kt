package io.kauth.service.iotdevice.decryptor

import io.kauth.service.iotdevice.model.tuya.EncryptModel
import org.apache.pulsar.shade.org.apache.commons.codec.binary.Base64
import org.apache.pulsar.shade.org.apache.commons.codec.binary.StringUtils
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

sealed interface Decryptor {
    fun decrypt(data: String, key: String): String
}

object AESEC : Decryptor {
    override fun decrypt(data: String, key: String): String {
        val secretKey = SecretKeySpec(key.toByteArray(), "AES")
        val c = Cipher.getInstance("AES");
        c.init(Cipher.DECRYPT_MODE, secretKey);
        val decodedValue = Base64.decodeBase64(data);
        val decValue = c.doFinal(decodedValue);
        val decryptedValue = StringUtils.newStringUtf8(decValue);
        return decryptedValue;
    }
}

fun EncryptModel.decrypt(data: String, key: String): String {
    return when (this) {
        EncryptModel.AES_ECB -> AESEC.decrypt(data, key)
        else -> error("Invalid")
    }
}

