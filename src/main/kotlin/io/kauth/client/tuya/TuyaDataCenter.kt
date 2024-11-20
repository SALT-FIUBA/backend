package io.kauth.client.tuya

enum class TuyaDataCenter {
    CH,
    WA,
    EA,
    CE,
    WE,
    I;

    val url
        get() =
            when (this) {
                CH -> "https://openapi.tuyacn.com"
                WA -> "https://openapi.tuyaus.com"
                EA -> "https://openapi-ueaz.tuyaus.com"
                CE -> "https://openapi.tuyaeu.com"
                WE -> "https://openapi-weaz.tuyaeu.com"
                I -> "https://openapi.tuyain.com"
            }
}