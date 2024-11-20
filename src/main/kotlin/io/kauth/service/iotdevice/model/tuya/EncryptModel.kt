package io.kauth.service.iotdevice.model.tuya

enum class EncryptModel(
    val modelName: String,
    val version: String
) {
    AES_ECB("aes_ecb", "1.0.0"),
    AES_GCM("aes_gcm", "2.0.0");

    companion object {
        fun fromString(value: String): EncryptModel? =
            entries.find { it.modelName == value }
    }
}